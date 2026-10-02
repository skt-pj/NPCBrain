package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class ClefDecisionClient {
    private static final String ENDPOINT_PREFIX =
            "https://api.cloudflare.com/client/v4/accounts/";
    private static final String ENDPOINT_SUFFIX =
            "/ai/run/@cf/cloudflare/clef";

    private final Context appContext;
    private final String accountId;
    private final String apiToken;
    private final String model;

    ClefDecisionClient(Context context, String accountId, String apiToken, String model) {
        appContext = context.getApplicationContext();
        this.accountId = ClefSettingsStore.normalizeAccountId(accountId);
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.model = ClefSettingsStore.normalizeModel(model);
    }

    JSONObject decide(String state) throws Exception {
        validateConfiguration();
        JSONObject body = buildRequestBody(model, state);
        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        URL url = new URL(ENDPOINT_PREFIX + accountId + ENDPOINT_SUFFIX);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(180000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }
            int status = connection.getResponseCode();
            String responseText = readAll(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "Cloudflare CLEF HTTP " + status + ": " + preview(responseText));
            }
            JSONObject wrapper = new JSONObject(responseText);
            if (wrapper.has("success") && !wrapper.optBoolean("success", false)) {
                throw new IllegalStateException(
                        "Cloudflare CLEF API error: " + preview(responseText));
            }
            JSONObject result = wrapper.optJSONObject("result");
            if (result == null) result = wrapper;
            if (result.optJSONObject("answers") == null) {
                throw new IllegalStateException("Cloudflare CLEF response has no answers");
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    static JSONObject buildRequestBody(String model, String state) throws Exception {
        JSONObject questions = new JSONObject();
        JSONObject action = new JSONObject()
                .put("type", "choice")
                .put("instructions",
                        "Choose the single action class this NPC should take now. "
                                + "Use only the grounded state, goals, memory, personality, and constraints.")
                .put("criteria", actionCriteria(state));
        JSONObject commit = new JSONObject()
                .put("type", "noul")
                .put("instructions",
                        "Should this NPC commit to the chosen action class now rather than remain undecided?");
        questions.put("action", action);
        questions.put("commit_now", commit);
        return new JSONObject()
                .put("model", ClefSettingsStore.normalizeModel(model))
                .put("state", state == null ? "" : state)
                .put("questions", questions);
    }

    private static JSONObject actionCriteria(String state) throws Exception {
        String source = state == null ? "" : state;
        JSONObject criteria = new JSONObject();
        if (source.contains("\"mode\":\"dungeon_turn\"")) {
            return criteria
                    .put("attack", "Attack a currently legal target when combat is the chosen course.")
                    .put("advance", "Move toward the current grounded objective using legal movement.")
                    .put("explore", "Gather new grounded dungeon information through legal exploration.")
                    .put("retreat", "Move away from danger or leave combat when withdrawal is preferred.")
                    .put("wait", "Take no committed movement or attack now.");
        }
        if (source.contains("\"mode\":\"conversational_message\"")
                || source.contains("\"mode\":\"spontaneous_life_event\"")
                || source.contains("\"mode\":\"reply_timer\"")) {
            return criteria
                    .put("speak_now", "Communicate now with grounded content relevant to the current interaction.")
                    .put("remain_silent", "Do not communicate now.")
                    .put("defer", "Do not communicate now, but leave room to respond later.")
                    .put("act_without_speaking", "Take an in-world action without communicating.");
        }
        return criteria
                .put("continue_current_activity", "Continue the current grounded activity.")
                .put("pursue_goal", "Take a legal action that advances the active goal.")
                .put("communicate", "Communicate with a relevant person when socially or practically warranted.")
                .put("gather_information", "Seek grounded information before committing further.")
                .put("withdraw_or_avoid", "Reduce exposure to a relevant threat or unwanted situation.")
                .put("wait", "Take no new committed action now.");
    }

    private void validateConfiguration() {
        if (accountId.isEmpty() || !accountId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("Cloudflare Account IDが未設定または不正です");
        }
        if (apiToken.isEmpty()) {
            throw new IllegalStateException("Cloudflare API tokenが未設定です");
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    private static String preview(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() <= 1000 ? value : value.substring(0, 1000) + "…";
    }
}
