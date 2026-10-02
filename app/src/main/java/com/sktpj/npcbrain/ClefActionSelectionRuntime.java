package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class ClefActionSelectionRuntime {
    private final Context appContext;
    private final String npcId;
    private final ClefSettingsStore settings;
    private final SecureCloudflareTokenStore tokenStore;

    ClefActionSelectionRuntime(Context context, String npcId) {
        appContext = context.getApplicationContext();
        this.npcId = NpcId.of(npcId).value();
        settings = new ClefSettingsStore(appContext);
        tokenStore = new SecureCloudflareTokenStore(appContext);
    }

    boolean shouldHandle() {
        return settings.enabled();
    }

    JSONObject request(String state) throws Exception {
        String token = tokenStore.load().trim();
        String model = settings.model();
        String queueId = ProcessingQueueRegistry.startRunning(
                "clef_decision",
                npcId,
                model + " · brain_stage=action_selection",
                System.currentTimeMillis());
        long startedNs = SystemClock.elapsedRealtimeNanos();
        long pssBeforeKb = Debug.getPss();
        long heapBeforeBytes = usedJavaHeapBytes();
        boolean success = false;
        Throwable failure = null;
        try {
            JSONObject result = new ClefDecisionClient(
                    appContext,
                    settings.accountId(),
                    token,
                    model).decide(state);
            JSONObject adapted = adaptResult(result);
            success = true;
            ProcessingQueueRegistry.markCompleted(queueId);
            return adapted;
        } catch (Exception error) {
            failure = error;
            ProcessingQueueRegistry.markFailed(queueId, error);
            throw error;
        } finally {
            long durationMs = Math.max(
                    0L,
                    (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L);
            long pssAfterKb = Debug.getPss();
            long heapAfterBytes = usedJavaHeapBytes();
            new ClefPerformanceStore(appContext).record(
                    model,
                    durationMs,
                    pssBeforeKb,
                    pssAfterKb,
                    heapBeforeBytes,
                    heapAfterBytes,
                    success,
                    failure);
        }
    }

    private static long usedJavaHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    static JSONObject adaptResult(JSONObject result) throws Exception {
        JSONObject answers = result == null ? null : result.optJSONObject("answers");
        JSONObject action = answers == null ? null : answers.optJSONObject("action");
        if (action == null) {
            throw new IllegalStateException("CLEF action answer is missing");
        }
        String choice = action.optString("choice", "").trim();
        if (choice.isEmpty()) {
            throw new IllegalStateException("CLEF action choice is missing");
        }

        double confidence = action.optDouble("confidence", Double.NaN);
        if (Double.isNaN(confidence)) {
            JSONObject probabilities = action.optJSONObject("probabilities");
            if (probabilities != null) {
                confidence = probabilities.optDouble(choice, Double.NaN);
            }
        }
        if (Double.isNaN(confidence)) confidence = 0.0;
        confidence = clamp01(confidence);

        double commitNow = 0.5;
        JSONObject commit = answers.optJSONObject("commit_now");
        if (commit != null) {
            commitNow = clamp01(commit.optDouble("noul", 0.5));
        }

        String content = "CLEF action=" + choice
                + ", commit_now=" + String.format(Locale.US, "%.2f", commitNow);
        return new JSONObject()
                .put("module", "action_selection")
                .put("content", content)
                .put("confidence", confidence)
                .put("salient_facts", new JSONArray())
                .put("personality_effect", "")
                .put("graph_used_node_ids", new JSONArray());
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
