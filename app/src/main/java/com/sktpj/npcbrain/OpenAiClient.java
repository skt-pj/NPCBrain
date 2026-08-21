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

final class OpenAiClient {
    static final String MODEL = "gpt-5.6-luna";
    static final String REASONING_EFFORT = "max";

    private final String apiKey;

    OpenAiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    JSONObject requestJson(String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("reasoning", new JSONObject().put("effort", REASONING_EFFORT));
        body.put("max_output_tokens", 8192);
        body.put("input", prompt);

        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(180000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String responseText = readAll(stream);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("OpenAI API HTTP " + status + ": " + responseText);
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
        } finally {
            connection.disconnect();
        }
    }

    private static String extractOutputText(JSONObject response) {
        JSONArray output = response.optJSONArray("output");
        if (output == null) {
            return "";
        }
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
