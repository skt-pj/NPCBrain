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
import java.util.Iterator;

final class CloudflareDecisionModelClient {
    private static final String ENDPOINT_PREFIX =
            "https://api.cloudflare.com/client/v4/accounts/";

    private final String accountId;
    private final String apiToken;
    private final String modelId;

    CloudflareDecisionModelClient(String accountId, String apiToken, String modelId) {
        this.accountId = accountId == null ? "" : accountId.trim();
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.modelId = DecisionModelCatalog.normalize(modelId);
    }

    DecisionModelEvaluation evaluate(
            String state,
            SpecialistDecisionSchema.Spec spec
    ) throws Exception {
        validateConfiguration();
        String endpointModel = DecisionModelCatalog.cloudflareEndpointModel(modelId);
        URL url = new URL(ENDPOINT_PREFIX + accountId + "/ai/run/" + endpointModel);
        byte[] request = buildRequestBody(modelId, state, spec)
                .toString()
                .getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(180000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Authorization", "Bearer " + apiToken);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
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
                        "Cloudflare Decision Model HTTP " + status + ": " + preview(responseText));
            }
            JSONObject wrapper = new JSONObject(responseText);
            if (wrapper.has("success") && !wrapper.optBoolean("success", false)) {
                throw new IllegalStateException(
                        "Cloudflare Decision Model API error: " + preview(responseText));
            }
            JSONObject result = wrapper.optJSONObject("result");
            if (result == null) result = wrapper;
            JSONObject answers = result.optJSONObject("answers");
            if (answers == null) {
                throw new IllegalStateException("Cloudflare Decision Model response has no answers");
            }
            JSONObject choiceAnswer = answers.optJSONObject(spec.questionId);
            if (choiceAnswer == null) {
                throw new IllegalStateException(
                        "Cloudflare Decision Model response has no " + spec.questionId);
            }
            String choice = choiceAnswer.optString("choice", "").trim();
            JSONObject probabilities = choiceAnswer.optJSONObject("probabilities");
            if (choice.isEmpty()) choice = highestProbabilityKey(probabilities);
            if (choice.isEmpty() || !spec.criteria.containsKey(choice)) {
                throw new IllegalStateException(
                        "Cloudflare Decision Model returned unknown choice: " + choice);
            }
            double choiceProbability = probabilityForChoice(
                    choiceAnswer, probabilities, choice);
            JSONObject evidence = answers.optJSONObject("evidence_sufficient");
            double evidenceProbability = readNoul(evidence);
            return new DecisionModelEvaluation(
                    choice,
                    choiceProbability,
                    evidenceProbability,
                    modelId,
                    "cloud");
        } finally {
            connection.disconnect();
        }
    }

    static JSONObject buildRequestBody(
            String modelId,
            String state,
            SpecialistDecisionSchema.Spec spec
    ) {
        JSONObject criteria = new JSONObject();
        for (String id : spec.criteria.keySet()) {
            criteria.put(id, spec.criteria.get(id));
        }
        JSONObject questions = new JSONObject()
                .put(spec.questionId, new JSONObject()
                        .put("type", "choice")
                        .put("instructions", spec.instruction)
                        .put("criteria", criteria))
                .put("evidence_sufficient", new JSONObject()
                        .put("type", "noul")
                        .put("instructions", spec.evidenceInstruction));
        return new JSONObject()
                .put("model", DecisionModelCatalog.cloudflareModel(modelId))
                .put("state", state == null ? "" : state)
                .put("questions", questions);
    }

    private void validateConfiguration() {
        if (!DecisionModelCatalog.isCloud(modelId)) {
            throw new IllegalStateException("クラウド判断モデルが選択されていません");
        }
        if (accountId.isEmpty() || !accountId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("Cloudflare Account IDが未設定または不正です");
        }
        if (apiToken.isEmpty()) {
            throw new IllegalStateException("Cloudflare API tokenが未設定です");
        }
    }

    private static String highestProbabilityKey(JSONObject probabilities) {
        if (probabilities == null) return "";
        String best = "";
        double bestValue = Double.NEGATIVE_INFINITY;
        Iterator<String> keys = probabilities.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            double value = probabilities.optDouble(key, Double.NaN);
            if (Double.isFinite(value) && value > bestValue) {
                bestValue = value;
                best = key;
            }
        }
        return best;
    }

    private static double probabilityForChoice(
            JSONObject choiceAnswer,
            JSONObject probabilities,
            String choice
    ) {
        if (probabilities != null && probabilities.has(choice)) {
            double value = probabilities.optDouble(choice, Double.NaN);
            if (Double.isFinite(value)) return clamp01(value);
        }
        double confidence = choiceAnswer.optDouble("confidence", Double.NaN);
        return Double.isFinite(confidence) ? clamp01(confidence) : 0.5;
    }

    private static double readNoul(JSONObject answer) {
        if (answer == null) return 0.5;
        double direct = answer.optDouble("noul", Double.NaN);
        if (Double.isFinite(direct)) return clamp01(direct);
        double probability = answer.optDouble("probability", Double.NaN);
        return Double.isFinite(probability) ? clamp01(probability) : 0.5;
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

    private static String preview(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() <= 1000 ? value : value.substring(0, 1000) + "…";
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
