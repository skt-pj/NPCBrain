package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Runs one fixed-reaction CLEF specialist item at a time through the local FIFO. */
final class ClefSpecialistRuntime {
    private final Context appContext;
    private final String npcId;
    private final SpecialistInferenceSettingsStore inferenceSettings;
    private final ClefSettingsStore clefSettings;
    private final ClefLocalModelRepository modelRepository;

    ClefSpecialistRuntime(Context context, String npcId) {
        appContext = context.getApplicationContext();
        this.npcId = NpcId.of(npcId).value();
        inferenceSettings = new SpecialistInferenceSettingsStore(appContext);
        clefSettings = new ClefSettingsStore(appContext);
        modelRepository = new ClefLocalModelRepository(appContext);
    }

    boolean shouldHandle() {
        return inferenceSettings.usesDecisionModel();
    }

    JSONObject request(String moduleId, String boundedState) throws Exception {
        List<ClefSpecialistSchema.Field> fields = ClefSpecialistSchema.forModule(moduleId);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("CLEF specialist schema not found: " + moduleId);
        }
        if (!modelRepository.isDownloaded()) {
            throw new IllegalStateException(
                    "ローカルCLEFモデルが未ダウンロードです。AI管理からCLEF-Flashをダウンロードしてください。");
        }

        String model = clefSettings.model();
        String queueId = ProcessingQueueRegistry.enqueue(
                "decision_model",
                npcId,
                model + " · local · brain_stage=" + moduleId,
                System.currentTimeMillis());

        long startedNs = SystemClock.elapsedRealtimeNanos();
        long pssBeforeKb = Debug.getPss();
        long heapBeforeBytes = usedJavaHeapBytes();
        boolean success = false;
        Throwable failure = null;

        try {
            JSONObject result = ClefLocalExecutionQueue.execute(queueId, () -> {
                File modelFile = modelRepository.modelFile();
                double[] scores = ClefNativeRuntime.evaluate(modelFile, boundedState, fields);
                return adaptScores(moduleId, fields, scores);
            });
            success = true;
            ProcessingQueueRegistry.markCompleted(queueId);
            return result;
        } catch (Exception error) {
            failure = error;
            ProcessingQueueRegistry.markFailed(queueId, error);
            throw error;
        } finally {
            long durationMs = Math.max(
                    0L,
                    (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L);
            new ClefPerformanceStore(appContext).record(
                    model,
                    durationMs,
                    pssBeforeKb,
                    Debug.getPss(),
                    heapBeforeBytes,
                    usedJavaHeapBytes(),
                    success,
                    failure);
        }
    }

    static JSONObject adaptScores(
            String moduleId,
            List<ClefSpecialistSchema.Field> fields,
            double[] scores
    ) throws Exception {
        int expected = 0;
        for (ClefSpecialistSchema.Field field : fields) expected += field.optionIds.length;
        if (scores == null || scores.length != expected) {
            throw new IllegalArgumentException("CLEF score shape is invalid");
        }

        JSONObject signals = new JSONObject();
        StringBuilder content = new StringBuilder();
        int offset = 0;
        double confidenceTotal = 0.0;

        for (ClefSpecialistSchema.Field field : fields) {
            double[] fieldScores = new double[field.optionIds.length];
            System.arraycopy(scores, offset, fieldScores, 0, fieldScores.length);
            offset += fieldScores.length;

            double[] probabilities = softmax(fieldScores);
            int best = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[best]) best = i;
            }
            double confidence = choiceConfidence(probabilities);
            confidenceTotal += confidence;

            JSONObject signal = new JSONObject()
                    .put("value", field.optionIds[best])
                    .put("confidence", round3(confidence));
            signals.put(field.id, signal);

            if (content.length() > 0) content.append(", ");
            content.append(field.id)
                    .append("=")
                    .append(field.optionIds[best])
                    .append("(")
                    .append(String.format(Locale.US, "%.2f", confidence))
                    .append(")");
        }

        double overall = fields.isEmpty() ? 0.0 : confidenceTotal / fields.size();
        return new JSONObject()
                .put("module", moduleId)
                .put("engine", "decision_model")
                .put("model", ClefSettingsStore.MODEL_CLEF_FLASH)
                .put("signals", signals)
                .put("content", content.toString())
                .put("confidence", round3(overall))
                .put("salient_facts", new JSONArray())
                .put("personality_effect", "")
                .put("graph_used_node_ids", new JSONArray());
    }

    private static double[] softmax(double[] scores) {
        double max = Double.NEGATIVE_INFINITY;
        for (double score : scores) {
            if (!Double.isFinite(score)) throw new IllegalStateException("CLEF score is not finite");
            max = Math.max(max, score);
        }
        double sum = 0.0;
        double[] probabilities = new double[scores.length];
        for (int i = 0; i < scores.length; i++) {
            probabilities[i] = Math.exp(scores[i] - max);
            sum += probabilities[i];
        }
        if (!(sum > 0.0) || !Double.isFinite(sum)) {
            throw new IllegalStateException("CLEF softmax failed");
        }
        for (int i = 0; i < probabilities.length; i++) probabilities[i] /= sum;
        return probabilities;
    }

    private static double choiceConfidence(double[] probabilities) {
        if (probabilities.length < 2) return 1.0;
        double uniform = 1.0 / probabilities.length;
        double max = 0.0;
        for (double probability : probabilities) max = Math.max(max, probability);
        return Math.max(0.0, Math.min(1.0, (max - uniform) / (1.0 - uniform)));
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static long usedJavaHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }
}
