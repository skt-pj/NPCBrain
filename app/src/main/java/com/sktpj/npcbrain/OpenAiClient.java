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
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class OpenAiClient {
    static final String MODEL = "gpt-5.6-luna";
    static final String DEFAULT_REASONING_EFFORT = "max";

    private static final String API_HOST = "api.openai.com";
    private static final URL RESPONSES_URL;

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

    OpenAiClient(Context context, String apiKey) {
        this(context, apiKey, DEFAULT_REASONING_EFFORT);
    }

    OpenAiClient(Context context, String apiKey, String reasoningEffort) {
        this.appContext = context.getApplicationContext();
        this.apiKey = apiKey;
        this.reasoningEffort = ModelSettingsStore.normalizeReasoningEffort(reasoningEffort);
    }

    String reasoningEffort() {
        return reasoningEffort;
    }

    JSONObject requestJson(String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("reasoning", new JSONObject().put("effort", reasoningEffort));
        body.put("max_output_tokens", 8192);
        body.put("input", prompt);

        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        List<Network> networks = candidateNetworks();
        Exception lastNetworkError = null;

        for (Network network : networks) {
            try {
                network.getByName(API_HOST);
                return executeRequest(network, request);
            } catch (UnknownHostException | ConnectException | SocketTimeoutException error) {
                lastNetworkError = error;
            } catch (IOException error) {
                lastNetworkError = error;
            }
        }

        try {
            return executeRequest(null, request);
        } catch (UnknownHostException | ConnectException | SocketTimeoutException error) {
            throw networkFailure(error);
        } catch (IOException error) {
            if (lastNetworkError != null) {
                error.addSuppressed(lastNetworkError);
            }
            throw networkFailure(error);
        }
    }

    private JSONObject executeRequest(Network network, byte[] request) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) (network == null
                    ? RESPONSES_URL.openConnection()
                    : network.openConnection(RESPONSES_URL));
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

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
            String outputText = extractOutputText(response);
            if (outputText.isEmpty()) {
                throw new IllegalStateException("OpenAI API returned no output_text");
            }
            try {
                return new JSONObject(stripCodeFence(outputText));
            } catch (Exception error) {
                throw new IllegalStateException("Model output was not valid JSON: " + outputText, error);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private List<Network> candidateNetworks() {
        List<Network> result = new ArrayList<>();
        ConnectivityManager manager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return result;

        Network active = manager.getActiveNetwork();
        if (active != null && hasInternetCapability(manager, active)) {
            result.add(active);
        }

        for (Network network : manager.getAllNetworks()) {
            if (network == null || network.equals(active)) continue;
            if (!hasInternetCapability(manager, network)) continue;
            if (isValidated(manager, network)) {
                result.add(network);
            }
        }

        for (Network network : manager.getAllNetworks()) {
            if (network == null || network.equals(active) || result.contains(network)) continue;
            if (hasInternetCapability(manager, network)) {
                result.add(network);
            }
        }
        return result;
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

    private IllegalStateException networkFailure(Exception cause) {
        ConnectivityManager manager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean hasInternet = false;
        boolean hasValidated = false;
        if (manager != null) {
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities == null) continue;
                hasInternet |= capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                hasValidated |= capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }

        String message;
        if (!hasInternet) {
            message = "インターネット接続を検出できません。Wi-Fiまたはモバイル通信を有効にしてから再試行してください。";
        } else if (!hasValidated) {
            message = "ネットワークには接続していますが、Androidがインターネット到達性を確認できません。Wi-Fiの認証画面、VPN、Private DNSを確認して再試行してください。";
        } else {
            message = "api.openai.com のDNS解決または接続に失敗しました。利用可能な回線は順に試しました。Private DNS、VPN、広告ブロッカー、Wi-Fi側のDNS設定を確認するか、Wi-Fiを一度切ってモバイル回線で再試行してください。";
        }
        return new IllegalStateException(message, cause);
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
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
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
