package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class JevClient {
    static final String MODEL = "jev-latest";
    private static final URL ENDPOINT;

    static {
        try {
            ENDPOINT = new URL("https://api.typesafe.ai/v1/systemone");
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private final Context appContext;
    private final String npcId;
    private final SecureTypeSafeApiKeyStore apiKeyStore;

    JevClient(Context context, String npcId) {
        if (context == null) throw new IllegalArgumentException("context is required");
        appContext = context.getApplicationContext();
        this.npcId = NpcId.of(npcId).value();
        apiKeyStore = new SecureTypeSafeApiKeyStore(appContext);
    }

    JSONObject request(
            String moduleId,
            String boundedState,
            List<ClefSpecialistSchema.Field> fields
    ) throws Exception {
        String apiKey = apiKeyStore.load().trim();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "TypeSafe APIキーが未設定です。AI管理でAPIキーを設定してください。");
        }

        JSONObject request = buildRequest(boundedState, fields);
        byte[] requestBytes = request.toString().getBytes(StandardCharsets.UTF_8);
        long conservativeInput = NpcAiBudgetPolicy.conservativeInputTokenUpperBound(
                requestBytes.length);
        double reservationJpy = ApiPricingPolicy.reservationJpy(
                ApiPricingPolicy.MODEL_JEV_LATEST,
                conservativeInput,
                0);

        NpcAiStaminaStore budgetStore = new NpcAiStaminaStore(appContext);
        NpcAiStaminaStore.Reservation reservation =
                budgetStore.tryReserve(npcId, reservationJpy);
        if (reservation == null) {
            throw new IllegalStateException(
                    "AI STAMINAの月額上限に達するため、Jev API送信を停止しました。");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) ENDPOINT.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(120_000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
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
            JSONObject usage = response.optJSONObject("usage");
            long inputTokens = usage == null ? 0L : usage.optLong("input_tokens", 0L);
            long outputTokens = usage == null ? 0L : usage.optLong("output_tokens", 0L);
            long totalTokens = safeAdd(inputTokens, outputTokens);
            String actualModel = response.optString("model", MODEL);
            budgetStore.recordUsage(
                    npcId,
                    actualModel,
                    inputTokens,
                    0L,
                    0L,
                    outputTokens,
                    totalTokens);
            return adaptResponse(moduleId, fields, response);
        } finally {
            if (connection != null) connection.disconnect();
            budgetStore.releaseReservation(reservation);
        }
    }

    static JSONObject buildRequest(
            String boundedState,
            List<ClefSpecialistSchema.Field> fields
    ) throws Exception {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Jev questions are empty");
        }

        JSONObject request = new JSONObject();
        request.put("model", MODEL);
        String state = boundedState == null ? "" : boundedState.trim();
        try {
            request.put("state", new JSONObject(state));
        } catch (Exception ignored) {
            request.put("state", state);
        }

        JSONObject questions = new JSONObject();
        for (ClefSpecialistSchema.Field field : fields) {
            if (field == null
                    || field.id == null
                    || field.id.trim().isEmpty()
                    || field.optionIds == null
                    || field.optionDescriptions == null
                    || field.optionIds.length < 2
                    || field.optionIds.length != field.optionDescriptions.length) {
                throw new IllegalArgumentException("Jev specialist field is invalid");
            }
            JSONObject criteria = new JSONObject();
            for (int i = 0; i < field.optionIds.length; i++) {
                criteria.put(field.optionIds[i], field.optionDescriptions[i]);
            }
            questions.put(field.id, new JSONObject()
                    .put("type", "choice")
                    .put("instructions", field.instruction)
                    .put("criteria", criteria));
        }
        request.put("questions", questions);
        return request;
    }

    static JSONObject adaptResponse(
            String moduleId,
            List<ClefSpecialistSchema.Field> fields,
            JSONObject response
    ) throws Exception {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Jev specialist fields are empty");
        }
        JSONObject answers = response == null ? null : response.optJSONObject("answers");
        if (answers == null) throw new IllegalStateException("Jev response has no answers");

        JSONObject signals = new JSONObject();
        StringBuilder content = new StringBuilder();
        double confidenceTotal = 0.0;

        for (ClefSpecialistSchema.Field field : fields) {
            JSONObject answer = answers.optJSONObject(field.id);
            if (answer == null || !"choice".equals(answer.optString("type", ""))) {
                throw new IllegalStateException("Jev answer is missing: " + field.id);
            }
            String choice = answer.optString("choice", "").trim();
            if (!contains(field.optionIds, choice)) {
                throw new IllegalStateException(
                        "Jev returned an unknown choice for " + field.id + ": " + choice);
            }

            double confidence = answer.optDouble("confidence", Double.NaN);
            if (!Double.isFinite(confidence)) {
                JSONObject probabilities = answer.optJSONObject("probabilities");
                confidence = probabilities == null
                        ? 0.0
                        : probabilities.optDouble(choice, 0.0);
            }
            confidence = clamp01(confidence);
            confidenceTotal += confidence;

            signals.put(field.id, new JSONObject()
                    .put("value", choice)
                    .put("confidence", round3(confidence)));

            if (content.length() > 0) content.append(", ");
            content.append(field.id)
                    .append("=")
                    .append(choice)
                    .append("(")
                    .append(String.format(Locale.US, "%.2f", confidence))
                    .append(")");
        }

        double overall = confidenceTotal / fields.size();
        return new JSONObject()
                .put("module", moduleId == null ? "" : moduleId)
                .put("engine", "decision_model")
                .put("provider", "typesafe")
                .put("model", response.optString("model", MODEL))
                .put("signals", signals)
                .put("content", content.toString())
                .put("confidence", round3(overall))
                .put("salient_facts", new JSONArray())
                .put("personality_effect", "")
                .put("graph_used_node_ids", new JSONArray());
    }

    private static boolean contains(String[] values, String target) {
        if (values == null || target == null) return false;
        for (String value : values) {
            if (target.equals(value)) return true;
        }
        return false;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static long safeAdd(long left, long right) {
        long a = Math.max(0L, left);
        long b = Math.max(0L, right);
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    private static String apiErrorMessage(int status, String responseText) {
        String detail = responseText == null ? "" : responseText.trim();
        if (detail.length() > 800) detail = detail.substring(0, 800);
        if (status == 401 || status == 403) {
            return "TypeSafe APIキーを確認してください。" + suffix(detail);
        }
        if (status == 429) {
            return "TypeSafe APIの利用上限に達しました。" + suffix(detail);
        }
        if (status == 529 || status >= 500) {
            return "TypeSafe APIが一時的に利用できません。" + suffix(detail);
        }
        return "TypeSafe API HTTP " + status + suffix(detail);
    }

    private static String suffix(String detail) {
        return detail == null || detail.isEmpty() ? "" : "\n" + detail;
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
}
