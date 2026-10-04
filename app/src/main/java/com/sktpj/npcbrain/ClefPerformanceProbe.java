package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Debug probe that measures the actual local CLEF runtime without OpenAI. */
final class ClefPerformanceProbe {
    interface ProgressListener {
        void onProgress(String moduleId, int completed, int total, long durationMs);
    }

    static final class Result {
        final String text;

        Result(String text) {
            this.text = text == null ? "" : text;
        }

        String displayText() {
            return text;
        }
    }

    private ClefPerformanceProbe() {
    }

    static Result run(Context context) throws Exception {
        return run(context, null);
    }

    static Result run(Context context, ProgressListener listener) throws Exception {
        Context appContext = context.getApplicationContext();
        ClefLocalModelRepository repository = new ClefLocalModelRepository(appContext);
        if (!repository.isDownloaded()) {
            throw new IllegalStateException("CLEF-Flashが未ダウンロードです。");
        }

        File modelFile = repository.modelFile();
        String model = new ClefSettingsStore(appContext).model();
        ClefPerformanceStore store = new ClefPerformanceStore(appContext);
        String state = probeState().toString();

        long totalStart = SystemClock.elapsedRealtimeNanos();
        long totalPssBefore = Debug.getPss();
        long totalHeapBefore = usedJavaHeapBytes();
        long sumMs = 0L;
        int completed = 0;
        int processed = 0;
        StringBuilder detail = new StringBuilder();
        String[] moduleIds = BrainEngine.specialistIds();

        for (String moduleId : moduleIds) {
            List<ClefSpecialistSchema.Field> fields =
                    ClefSpecialistSchema.forModule(moduleId);
            if (fields.isEmpty()) continue;

            long startedNs = SystemClock.elapsedRealtimeNanos();
            long pssBefore = Debug.getPss();
            long heapBefore = usedJavaHeapBytes();
            boolean success = false;
            Throwable failure = null;
            try {
                double[] scores = ClefLocalExecutionQueue.execute(
                        "",
                        () -> ClefNativeRuntime.evaluate(modelFile, state, fields));
                ClefSpecialistRuntime.adaptScores(moduleId, fields, scores);
                success = true;
                completed++;
            } catch (Exception error) {
                failure = error;
                throw error;
            } finally {
                long durationMs = Math.max(
                        0L,
                        (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L);
                sumMs += durationMs;
                store.record(
                        model,
                        durationMs,
                        pssBefore,
                        Debug.getPss(),
                        heapBefore,
                        usedJavaHeapBytes(),
                        success,
                        failure);
                if (detail.length() > 0) detail.append("\n");
                detail.append(BrainEngine.stageLabel(moduleId))
                        .append("  ")
                        .append(durationMs)
                        .append(" ms")
                        .append(success ? "" : "  ERROR");
                processed++;
                if (listener != null) {
                    listener.onProgress(moduleId, processed, moduleIds.length, durationMs);
                }
            }
        }

        long totalMs = Math.max(
                0L,
                (SystemClock.elapsedRealtimeNanos() - totalStart) / 1_000_000L);
        long pssAfter = Debug.getPss();
        long heapAfter = usedJavaHeapBytes();
        long average = completed <= 0 ? 0L : sumMs / completed;

        StringBuilder result = new StringBuilder();
        result.append("ローカルCLEF実測完了")
                .append("\nモデル  ").append(ClefSettingsStore.displayLabel())
                .append("\n9専門合計  ").append(totalMs).append(" ms")
                .append(" · 平均 ").append(average).append(" ms/専門")
                .append("\nPSS  ")
                .append(ClefPerformanceStore.formatKiB(totalPssBefore))
                .append(" → ")
                .append(ClefPerformanceStore.formatKiB(pssAfter))
                .append("\nJava heap  ")
                .append(ClefPerformanceStore.formatBytes(totalHeapBefore))
                .append(" → ")
                .append(ClefPerformanceStore.formatBytes(heapAfter))
                .append("\n\n専門別\n")
                .append(detail);
        return new Result(result.toString());
    }

    static JSONObject probeState() {
        JSONObject root = new JSONObject();
        try {
            root.put("stimulus",
                    "A familiar person says they want to talk later. "
                            + "The NPC is mildly tired but safe and has no urgent task.");
            root.put("character_state", new JSONObject()
                    .put("location", "home")
                    .put("activity", "resting")
                    .put("goal", "maintain ordinary life and relationships")
                    .put("fatigue", 0.35)
                    .put("stress", 0.20));
            root.put("long_term_memory", new JSONObject()
                    .put("policy", "Retrieved memories are fallible evidence."));
            root.put("parallel_phase", new JSONObject()
                    .put("mode", "parallel_specialists")
                    .put("peer_outputs_available", false)
                    .put("specialist_count", 9));
        } catch (Exception ignored) {
        }
        return root;
    }

    private static long usedJavaHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }
}