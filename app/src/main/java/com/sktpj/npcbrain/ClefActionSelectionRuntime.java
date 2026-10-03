package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ClefActionSelectionRuntime {
    private static final class Criterion {
        final String id;
        final String description;

        Criterion(String id, String description) {
            this.id = id;
            this.description = description;
        }
    }

    private final Context appContext;
    private final String npcId;
    private final ClefSettingsStore settings;
    private final ClefLocalModelRepository modelRepository;

    ClefActionSelectionRuntime(Context context, String npcId) {
        appContext = context.getApplicationContext();
        this.npcId = NpcId.of(npcId).value();
        settings = new ClefSettingsStore(appContext);
        modelRepository = new ClefLocalModelRepository(appContext);
    }

    boolean shouldHandle() {
        return settings.enabled();
    }

    JSONObject request(String state) throws Exception {
        if (!modelRepository.isDownloaded()) {
            throw new IllegalStateException(
                    "ローカルCLEFモデルが未ダウンロードです。設定からCLEF-Flashをダウンロードしてください。");
        }

        String model = settings.model();
        String queueId = ProcessingQueueRegistry.startRunning(
                "clef_decision",
                npcId,
                model + " · local · brain_stage=action_selection",
                System.currentTimeMillis());
        long startedNs = SystemClock.elapsedRealtimeNanos();
        long pssBeforeKb = Debug.getPss();
        long heapBeforeBytes = usedJavaHeapBytes();
        boolean success = false;
        Throwable failure = null;

        try {
            String compactedState = LocalPromptCompactor.compact(
                    state == null ? "" : state,
                    LocalPromptCompactor.MAX_COMPACTION_LEVEL);
            List<Criterion> criteria = criteriaFor(state);
            criteria.sort(Comparator.comparing(item -> item.id));

            String[] ids = new String[criteria.size()];
            String[] descriptions = new String[criteria.size()];
            for (int i = 0; i < criteria.size(); i++) {
                ids[i] = criteria.get(i).id;
                descriptions[i] = criteria.get(i).description;
            }

            File modelFile = modelRepository.modelFile();
            double[] scores = ClefNativeRuntime.decide(
                    modelFile,
                    compactedState,
                    ids,
                    descriptions);
            JSONObject adapted = adaptScores(ids, scores);
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

    static JSONObject adaptScores(String[] actionIds, double[] scores) throws Exception {
        if (actionIds == null || actionIds.length == 0
                || scores == null || scores.length != actionIds.length + 2) {
            throw new IllegalArgumentException("CLEF score shape is invalid");
        }

        double[] actionScores = new double[actionIds.length];
        System.arraycopy(scores, 0, actionScores, 0, actionIds.length);
        double[] actionProbabilities = softmax(actionScores);

        int best = 0;
        for (int i = 1; i < actionProbabilities.length; i++) {
            if (actionProbabilities[i] > actionProbabilities[best]) best = i;
        }
        String choice = actionIds[best];
        double confidence = choiceConfidence(actionProbabilities);

        double[] commitProbabilities = softmax(new double[]{
                scores[actionIds.length],
                scores[actionIds.length + 1]
        });
        double commitNow = commitProbabilities[0];

        String content = "CLEF local action=" + choice
                + ", commit_now=" + String.format(Locale.US, "%.2f", commitNow);
        return new JSONObject()
                .put("module", "action_selection")
                .put("content", content)
                .put("confidence", clamp01(confidence))
                .put("salient_facts", new JSONArray())
                .put("personality_effect", "")
                .put("graph_used_node_ids", new JSONArray());
    }

    private static double[] softmax(double[] scores) {
        double max = Double.NEGATIVE_INFINITY;
        for (double score : scores) {
            if (!Double.isFinite(score)) {
                throw new IllegalStateException("CLEF score is not finite");
            }
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
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= sum;
        }
        return probabilities;
    }

    private static double choiceConfidence(double[] probabilities) {
        if (probabilities.length < 2) return 1.0;
        double uniform = 1.0 / probabilities.length;
        double max = 0.0;
        for (double probability : probabilities) max = Math.max(max, probability);
        return Math.max(0.0, (max - uniform) / (1.0 - uniform));
    }

    private static List<Criterion> criteriaFor(String state) {
        String source = state == null ? "" : state;
        List<Criterion> result = new ArrayList<>();
        if (source.contains("\"mode\":\"dungeon_turn\"")) {
            result.add(new Criterion("attack",
                    "Attack a currently legal target when combat is the chosen course."));
            result.add(new Criterion("advance",
                    "Move toward the current grounded objective using legal movement."));
            result.add(new Criterion("explore",
                    "Gather new grounded dungeon information through legal exploration."));
            result.add(new Criterion("retreat",
                    "Move away from danger or leave combat when withdrawal is preferred."));
            result.add(new Criterion("wait",
                    "Take no committed movement or attack now."));
            return result;
        }
        if (source.contains("\"mode\":\"conversational_message\"")
                || source.contains("\"mode\":\"spontaneous_life_event\"")
                || source.contains("\"mode\":\"reply_timer\"")) {
            result.add(new Criterion("speak_now",
                    "Communicate now with grounded content relevant to the current interaction."));
            result.add(new Criterion("remain_silent", "Do not communicate now."));
            result.add(new Criterion("defer",
                    "Do not communicate now, but leave room to respond later."));
            result.add(new Criterion("act_without_speaking",
                    "Take an in-world action without communicating."));
            return result;
        }
        result.add(new Criterion("continue_current_activity",
                "Continue the current grounded activity."));
        result.add(new Criterion("pursue_goal",
                "Take a legal action that advances the active goal."));
        result.add(new Criterion("communicate",
                "Communicate with a relevant person when socially or practically warranted."));
        result.add(new Criterion("gather_information",
                "Seek grounded information before committing further."));
        result.add(new Criterion("withdraw_or_avoid",
                "Reduce exposure to a relevant threat or unwanted situation."));
        result.add(new Criterion("wait", "Take no new committed action now."));
        return result;
    }

    private static long usedJavaHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
