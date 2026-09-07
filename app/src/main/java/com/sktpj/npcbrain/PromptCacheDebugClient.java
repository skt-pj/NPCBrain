package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Debug-only diagnostic transport for explicit Prompt Cache measurements. */
final class PromptCacheDebugClient {
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 180_000;

    private final String apiKey;

    PromptCacheDebugClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    OpenAiClient.Usage execute(
            String stablePrefix,
            String dynamicSuffix,
            String promptCacheKey,
            int maxOutputTokens
    ) throws Exception {
        if (apiKey.isEmpty()) throw new IllegalStateException("OpenAI APIキーが未設定です。");
        JSONObject body = buildRequestBody(
                stablePrefix,
                dynamicSuffix,
                promptCacheKey,
                maxOutputTokens);
        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RESPONSES_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
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
                throw new IllegalStateException(
                        "OpenAI API HTTP " + status + ": " + bounded(responseText, 900));
            }

            JSONObject response = new JSONObject(responseText);
            validateJsonOutput(response);
            return OpenAiClient.Usage.fromResponse(response);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static JSONObject buildRequestBody(
            String stablePrefix,
            String dynamicSuffix,
            String promptCacheKey,
            int maxOutputTokens
    ) {
        String prefix = stablePrefix == null ? "" : stablePrefix;
        String suffix = dynamicSuffix == null ? "" : dynamicSuffix;
        String cacheKey = promptCacheKey == null ? "" : promptCacheKey.trim();
        if (cacheKey.isEmpty()) throw new IllegalArgumentException("promptCacheKey is required");

        JSONObject stable = new JSONObject()
                .put("type", "input_text")
                .put("text", prefix)
                .put("prompt_cache_breakpoint", new JSONObject().put("mode", "explicit"));
        JSONObject dynamic = new JSONObject()
                .put("type", "input_text")
                .put("text", suffix);
        JSONObject message = new JSONObject()
                .put("type", "message")
                .put("role", "user")
                .put("content", new JSONArray().put(stable).put(dynamic));

        return new JSONObject()
                .put("model", OpenAiClient.MODEL)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("max_output_tokens", OpenAiClient.normalizeMaxOutputTokens(maxOutputTokens))
                .put("prompt_cache_key", cacheKey)
                .put("prompt_cache_options", new JSONObject().put("mode", "explicit"))
                .put("input", new JSONArray().put(message));
    }

    private static void validateJsonOutput(JSONObject response) {
        String outputText = extractOutputText(response);
        if (outputText.isEmpty()) {
            throw new IllegalStateException("OpenAI API returned no output_text");
        }
        try {
            new JSONObject(stripCodeFence(outputText));
        } catch (Exception error) {
            throw new IllegalStateException("Prompt Cache probe output was not valid JSON", error);
        }
    }

    private static String extractOutputText(JSONObject response) {
        JSONArray output = response == null ? null : response.optJSONArray("output");
        if (output == null) return "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type", ""))) {
                    if (text.length() > 0) text.append('\n');
                    text.append(part.optString("text", ""));
                }
            }
        }
        return text.toString().trim();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int firstNewLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                return text.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        return text;
    }

    private static String bounded(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
