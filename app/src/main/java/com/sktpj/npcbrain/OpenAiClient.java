package com.sktpj.npcbrain;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class OpenAiClient {
    interface UsageListener {
        void onUsage(Usage usage);
    }

    interface OutputLimitPolicy {
        int maxOutputTokens(int logicalRequestOrdinal);
    }

    interface FunctionTool {
        String name();
        JSONObject definition();
        JSONObject invoke(JSONObject arguments);

        default boolean requiredInvocation() {
            return false;
        }
    }

    private static final class FunctionCall {
        final String callId;
        final String name;
        final JSONObject arguments;

        FunctionCall(String callId, String name, JSONObject arguments) {
            this.callId = callId == null ? "" : callId.trim();
            this.name = name == null ? "" : name.trim();
            this.arguments = arguments == null ? new JSONObject() : arguments;
        }
    }

    static final class Usage {
        final long inputTokens;
        final long cachedInputTokens;
        final long outputTokens;
        final long totalTokens;

        Usage(long inputTokens, long cachedInputTokens, long outputTokens, long totalTokens) {
            this.inputTokens = Math.max(0L, inputTokens);
            this.cachedInputTokens = Math.max(0L, Math.min(this.inputTokens, cachedInputTokens));
            this.outputTokens = Math.max(0L, outputTokens);
            long fallbackTotal = safeAdd(this.inputTokens, this.outputTokens);
            this.totalTokens = totalTokens > 0L ? totalTokens : fallbackTotal;
        }

        static Usage fromResponse(JSONObject response) {
            JSONObject usage = response == null ? null : response.optJSONObject("usage");
            if (usage == null) return new Usage(0L, 0L, 0L, 0L);
            long input = usage.optLong("input_tokens", 0L);
            JSONObject inputDetails = usage.optJSONObject("input_tokens_details");
            long cached = inputDetails == null ? 0L : inputDetails.optLong("cached_tokens", 0L);
            long output = usage.optLong("output_tokens", 0L);
            long total = usage.optLong("total_tokens", 0L);
            return new Usage(input, cached, output, total);
        }

        private static long safeAdd(long a, long b) {
            long left = Math.max(0L, a);
            long right = Math.max(0L, b);
            return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
        }
    }

    static final String MODEL = "gpt-5.6-luna";
    static final String DEFAULT_REASONING_EFFORT = "max";
    static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;

    private static final URL RESPONSES_URL;
    private static final int CONNECTION_RETRY_PASSES = 2;
    private static final long CONNECTION_RETRY_DELAY_MS = 450L;
    private static final ThreadLocal<FunctionTool> FUNCTION_TOOL = new ThreadLocal<>();
    private static volatile Network lastKnownGoodNetwork;

    static {
        try {
            RESPONSES_URL = new URL("https://api.openai.com/v1/responses");
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private final Context appContext;
    private final String apiKey;
    private final String reasoningEffort;
    private final UsageListener usageListener;
    private final OutputLimitPolicy outputLimitPolicy;
    private final AtomicInteger logicalRequestOrdinal = new AtomicInteger();

    OpenAiClient(Context context, String apiKey) {
        this(context, apiKey, DEFAULT_REASONING_EFFORT);
    }

    OpenAiClient(Context context, String apiKey, String reasoningEffort) {
        this(context, apiKey, reasoningEffort, null, null);
    }

    OpenAiClient(
            Context context,
            String apiKey,
            String reasoningEffort,
            UsageListener usageListener,
            OutputLimitPolicy outputLimitPolicy
    ) {
        this.appContext = context.getApplicationContext();
        this.apiKey = apiKey;
        this.reasoningEffort = ModelSettingsStore.normalizeReasoningEffort(reasoningEffort);
        this.usageListener = usageListener;
        this.outputLimitPolicy = outputLimitPolicy;
    }

    static void setFunctionToolForCurrentThread(FunctionTool tool) {
        if (tool == null) FUNCTION_TOOL.remove();
        else FUNCTION_TOOL.set(tool);
    }

    static void clearFunctionToolForCurrentThread() {
        FUNCTION_TOOL.remove();
    }

    String reasoningEffort() {
        return reasoningEffort;
    }

    JSONObject requestJson(String prompt) throws Exception {
        int ordinal = logicalRequestOrdinal.incrementAndGet();
        int requestedLimit;
        if (outputLimitPolicy != null) {
            requestedLimit = outputLimitPolicy.maxOutputTokens(ordinal);
        } else if (!attributedNpcId(prompt).isEmpty()) {
            requestedLimit = NpcAiBudgetPolicy.npcDefaultMaxOutputTokens(prompt);
        } else {
            requestedLimit = DEFAULT_MAX_OUTPUT_TOKENS;
        }
        return requestJsonInternal(prompt, normalizeMaxOutputTokens(requestedLimit));
    }

    JSONObject requestJson(String prompt, int maxOutputTokens) throws Exception {
        logicalRequestOrdinal.incrementAndGet();
        return requestJsonInternal(prompt, normalizeMaxOutputTokens(maxOutputTokens));
    }

    static int normalizeMaxOutputTokens(int value) {
        return Math.max(1, Math.min(DEFAULT_MAX_OUTPUT_TOKENS, value));
    }

    static boolean isGlobalWorkspacePrompt(String prompt) {
        return prompt != null && prompt.startsWith("You are the existing Global Workspace");
    }

    static String toolChoice(OpenAiClient.FunctionTool tool) {
        return tool != null && tool.requiredInvocation() ? "required" : "auto";
    }

    private JSONObject requestJsonInternal(String prompt, int maxOutputTokens) throws Exception {
        FunctionTool tool = isGlobalWorkspacePrompt(prompt) ? FUNCTION_TOOL.get() : null;
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("reasoning", new JSONObject().put("effort", reasoningEffort));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("input", prompt);
        if (tool != null) {
            body.put("tools", new JSONArray().put(tool.definition()));
            body.put("tool_choice", toolChoice(tool));
            body.put("parallel_tool_calls", false);
        }

        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        IOException firstFailure = null;
        String attributedNpcId = attributedNpcId(prompt);

        for (int pass = 0; pass < CONNECTION_RETRY_PASSES; pass++) {
            if (pass > 0) {
                try {
                    Thread.sleep(CONNECTION_RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            Network preferred = validCachedNetwork();
            if (preferred != null) {
                try {
                    JSONObject result = executeRequest(
                            preferred, request, attributedNpcId, tool, maxOutputTokens);
                    rememberNetwork(preferred);
                    return result;
                } catch (IOException error) {
                    if (firstFailure == null) firstFailure = error;
                    clearCachedNetwork(preferred);
                    if (!isSafeConnectionRetry(error)) {
                        throw networkFailure(error);
                    }
                }
            }

            try {
                JSONObject result = executeRequest(
                        null, request, attributedNpcId, tool, maxOutputTokens);
                rememberActiveNetwork();
                return result;
            } catch (IOException error) {
                if (firstFailure == null) firstFailure = error;
                if (!isSafeConnectionRetry(error)) {
                    throw networkFailure(error);
                }
            }

            for (Network network : candidateNetworks()) {
                if (network == null || network.equals(preferred)) continue;
                try {
                    JSONObject result = executeRequest(
                            network, request, attributedNpcId, tool, maxOutputTokens);
                    rememberNetwork(network);
                    return result;
                } catch (IOException error) {
                    if (firstFailure == null) firstFailure = error;
                    if (!isSafeConnectionRetry(error)) {
                        throw networkFailure(error);
                    }
                }
            }
        }

        throw networkFailure(firstFailure == null
                ? new IOException("No route could reach api.openai.com")
                : firstFailure);
    }

    private JSONObject executeRequest(
            Network network,
            byte[] request,
            String attributedNpcId,
            FunctionTool tool,
            int maxOutputTokens
    ) throws Exception {
        JSONObject response = executeResponse(
                network, request, attributedNpcId, maxOutputTokens);
        if (tool != null) {
            FunctionCall call = extractFunctionCall(response, tool.name());
            if (call != null) {
                JSONObject toolOutput = tool.invoke(call.arguments);
                JSONObject continuation = new JSONObject();
                continuation.put("model", MODEL);
                continuation.put("reasoning", new JSONObject().put("effort", reasoningEffort));
                continuation.put("max_output_tokens", maxOutputTokens);
                continuation.put("previous_response_id", response.optString("id", ""));
                continuation.put("input", new JSONArray().put(new JSONObject()
                        .put("type", "function_call_output")
                        .put("call_id", call.callId)
                        .put("output", toolOutput.toString())));
                JSONObject finalResponse = executeResponse(
                        network,
                        continuation.toString().getBytes(StandardCharsets.UTF_8),
                        attributedNpcId,
                        maxOutputTokens);
                return parseJsonOutput(finalResponse);
            }
        }
        return parseJsonOutput(response);
    }

    private JSONObject executeResponse(
            Network network,
            byte[] request,
            String attributedNpcId,
            int maxOutputTokens
    ) throws Exception {
        NpcAiStaminaStore budgetStore = null;
        NpcAiStaminaStore.Reservation reservation = null;
        if (attributedNpcId != null && !attributedNpcId.isEmpty()) {
            budgetStore = new NpcAiStaminaStore(appContext);
            double requestReservation = NpcAiBudgetPolicy.reservationJpy(
                    request == null ? 0 : request.length,
                    maxOutputTokens);
            reservation = budgetStore.tryReserve(attributedNpcId, requestReservation);
            if (reservation == null) {
                throw new IllegalStateException(
                        "AI STAMINAの月額上限に達するため、OpenAI API送信を停止しました。");
            }
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) (network == null
                    ? RESPONSES_URL.openConnection()
                    : network.openConnection(RESPONSES_URL));
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseText = readAll(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(apiErrorMessage(status, responseText));
            }

            JSONObject response = new JSONObject(responseText);
            notifyUsage(Usage.fromResponse(response), attributedNpcId);
            return response;
        } finally {
            if (connection != null) connection.disconnect();
            if (budgetStore != null && reservation != null) {
                budgetStore.releaseReservation(reservation);
            }
        }
    }

    private static JSONObject parseJsonOutput(JSONObject response) {
        String outputText = extractOutputText(response);
        if (outputText.isEmpty()) {
            throw new IllegalStateException("OpenAI API returned no output_text");
        }
        try {
            return new JSONObject(stripCodeFence(outputText));
        } catch (Exception error) {
            throw new IllegalStateException("Model output was not valid JSON: " + outputText, error);
        }
    }

    static JSONObject extractFunctionArgumentsForTest(JSONObject response, String functionName) {
        FunctionCall call = extractFunctionCall(response, functionName);
        return call == null ? null : call.arguments;
    }

    private static FunctionCall extractFunctionCall(JSONObject response, String functionName) {
        JSONArray output = response == null ? null : response.optJSONArray("output");
        if (output == null) return null;
        String expected = functionName == null ? "" : functionName.trim();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null || !"function_call".equals(item.optString("type", ""))) continue;
            String name = item.optString("name", "").trim();
            if (!expected.isEmpty() && !expected.equals(name)) continue;
            String callId = item.optString("call_id", "").trim();
            if (callId.isEmpty() || name.isEmpty()) continue;
            try {
                JSONObject arguments = new JSONObject(item.optString("arguments", "{}"));
                return new FunctionCall(callId, name, arguments);
            } catch (Exception ignored) {
                return new FunctionCall(callId, name, new JSONObject());
            }
        }
        return null;
    }

    private void notifyUsage(Usage usage, String attributedNpcId) {
        if (usage == null) return;
        if (usageListener != null) {
            try {
                usageListener.onUsage(usage);
            } catch (RuntimeException ignored) {
            }
            return;
        }
        if (attributedNpcId == null || attributedNpcId.isEmpty()) return;
        try {
            new NpcAiStaminaStore(appContext).recordUsage(attributedNpcId, usage);
        } catch (RuntimeException ignored) {
        }
    }

    static String attributedNpcId(String prompt) {
        if (prompt == null || prompt.isEmpty()) return "";
        int marker = prompt.indexOf("character_id");
        if (marker < 0) return "";
        int limit = Math.min(prompt.length(), marker + 192);
        String tail = prompt.substring(marker, limit);
        int cursor = 0;
        while (cursor >= 0 && cursor < tail.length()) {
            int npc = tail.indexOf("npc", cursor);
            if (npc < 0) return "";
            int end = npc + 3;
            while (end < tail.length() && Character.isDigit(tail.charAt(end))) end++;
            if (end > npc + 3) {
                String candidate = tail.substring(npc, end);
                try {
                    return NpcId.of(candidate).value();
                } catch (Exception ignored) {
                }
            }
            cursor = npc + 3;
        }
        return "";
    }

    private Network validCachedNetwork() {
        Network cached = lastKnownGoodNetwork;
        if (cached == null) return null;
        ConnectivityManager manager = connectivityManager();
        if (manager == null || !hasInternetCapability(manager, cached)) {
            clearCachedNetwork(cached);
            return null;
        }
        return cached;
    }

    private void rememberActiveNetwork() {
        ConnectivityManager manager = connectivityManager();
        if (manager == null) return;
        Network active = manager.getActiveNetwork();
        if (active != null && hasInternetCapability(manager, active)) {
            rememberNetwork(active);
        }
    }

    private static void rememberNetwork(Network network) {
        if (network != null) lastKnownGoodNetwork = network;
    }

    private static void clearCachedNetwork(Network network) {
        if (network != null && network.equals(lastKnownGoodNetwork)) {
            lastKnownGoodNetwork = null;
        }
    }

    private List<Network> candidateNetworks() {
        List<Network> result = new ArrayList<>();
        ConnectivityManager manager = connectivityManager();
        if (manager == null) return result;

        Network active = manager.getActiveNetwork();
        for (Network network : manager.getAllNetworks()) {
            if (network == null || network.equals(active)) continue;
            if (!hasInternetCapability(manager, network)) continue;
            if (isValidated(manager, network)) result.add(network);
        }
        if (active != null && hasInternetCapability(manager, active)) {
            result.add(active);
        }
        for (Network network : manager.getAllNetworks()) {
            if (network == null || result.contains(network)) continue;
            if (hasInternetCapability(manager, network)) result.add(network);
        }
        return result;
    }

    private ConnectivityManager connectivityManager() {
        return (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private static boolean hasInternetCapability(ConnectivityManager manager, Network network) {
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static boolean isValidated(ConnectivityManager manager, Network network) {
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static boolean isSafeConnectionRetry(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException) {
                return true;
            }
            if (current instanceof SocketTimeoutException) return false;
            current = current.getCause();
        }
        return false;
    }

    private IllegalStateException networkFailure(Exception cause) {
        ConnectivityManager manager = connectivityManager();
        boolean hasInternet = false;
        boolean hasValidated = false;
        int internetNetworks = 0;
        int validatedInternetNetworks = 0;
        if (manager != null) {
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities == null) continue;
                boolean internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                boolean validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if (internet) {
                    hasInternet = true;
                    internetNetworks++;
                    if (validated) {
                        hasValidated = true;
                        validatedInternetNetworks++;
                    }
                }
            }
        }

        String causeName = rootCauseName(cause);
        String message;
        if (!hasInternet) {
            message = "インターネット接続を検出できません。Wi-Fiまたはモバイル通信を有効にして再試行してください。";
        } else if (!hasValidated) {
            message = "ネットワークには接続していますが、Androidが外部インターネット到達性を確認できません。Wi-Fiの認証画面、VPN、Private DNSを確認して再試行してください。";
        } else {
            message = "api.openai.com へのHTTPS接続に失敗しました。成功した回線の再利用、通常経路、利用可能な別回線を試しました。"
                    + "\n検出: internet=" + internetNetworks
                    + " / validatedInternet=" + validatedInternetNetworks
                    + " / cause=" + causeName
                    + "\nDNS/接続確立前の失敗は短時間リトライ済みです。Private DNS、VPN、広告ブロッカー、端末のRemote/プロキシ環境、Wi-Fi側DNSを確認してください。"
                    + " Wi-Fiとモバイル通信の両方がある場合は片方だけにすると切り分けできます。";
        }
        return new IllegalStateException(message, cause);
    }

    private static String rootCauseName(Throwable error) {
        if (error == null) return "unknown";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String name = current.getClass().getSimpleName();
        return name == null || name.isEmpty() ? "unknown" : name;
    }

    private static String apiErrorMessage(int status, String responseText) {
        String detail = responseText == null ? "" : responseText.trim();
        if (detail.length() > 1200) detail = detail.substring(0, 1200);
        if (status == 401) {
            return "OpenAI APIキーが無効、期限切れ、または利用できません。ホームのAI設定を確認してください。\n" + detail;
        }
        if (status == 404 && detail.contains("model")) {
            return "GPT-5.6 Luna を利用できません。APIアカウントのモデル権限または提供状況を確認してください。\n" + detail;
        }
        if (status == 429) {
            return "OpenAI APIの利用上限またはレート制限に達しました。時間を置くか、APIアカウントの利用状況を確認してください。\n" + detail;
        }
        return "OpenAI API HTTP " + status + ": " + detail;
    }

    private static String extractOutputText(JSONObject response) {
        JSONArray output = response.optJSONArray("output");
        if (output == null) return "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    if (text.length() > 0) text.append('\n');
                    text.append(part.optString("text"));
                }
            }
        }
        return text.toString().trim();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static String stripCodeFence(String value) {
        String text = value.trim();
        if (text.startsWith("```")) {
            int firstNewLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                return text.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        return text;
    }
}
