package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

final class SpecialistDecisionModelRuntime {
    private final Context appContext;
    private final String npcId;
    private final DecisionModelSettingsStore settings;
    private final SecureCloudflareTokenStore tokenStore;
    private final ClefLocalModelRepository localRepository;

    SpecialistDecisionModelRuntime(Context context, String npcId) {
        appContext = context.getApplicationContext();
        this.npcId = NpcId.of(npcId).value();
        settings = new DecisionModelSettingsStore(appContext);
        tokenStore = new SecureCloudflareTokenStore(appContext);
        localRepository = new ClefLocalModelRepository(appContext);
    }

    JSONObject[] requestAll(
            String[] moduleIds,
            String[] moduleLabels,
            String[] roles,
            String[] personalityRules,
            JSONObject commonContext,
            JSONObject[] graphFocus
    ) throws Exception {
        int count = moduleIds == null ? 0 : moduleIds.length;
        if (count == 0
                || moduleLabels == null || moduleLabels.length != count
                || roles == null || roles.length != count
                || personalityRules == null || personalityRules.length != count
                || graphFocus == null || graphFocus.length != count) {
            throw new IllegalArgumentException("分割脳の判断モデル入力が不正です");
        }

        JSONArray modules = new JSONArray();
        for (int i = 0; i < count; i++) {
            modules.put(new JSONObject()
                    .put("module", moduleIds[i])
                    .put("module_label", moduleLabels[i])
                    .put("role", roles[i])
                    .put("personality_rule", personalityRules[i])
                    .put("cognitive_graph_focus",
                            graphFocus[i] == null ? new JSONObject() : graphFocus[i]));
        }

        JSONObject state = new JSONObject()
                .put("common_context", commonContext == null ? new JSONObject() : commonContext)
                .put("specialists", modules)
                .put("runtime_contract", new JSONObject()
                        .put("engine", "decision_model")
                        .put("free_form_generation", false)
                        .put("specialist_count", count)
                        .put("same_cycle_peer_outputs_available", false)
                        .put("all_questions_evaluated_in_one_joint_request", true));
        String stateText = state.toString();

        SpecialistDecisionSchema.Spec[] specs =
                new SpecialistDecisionSchema.Spec[count];
        for (int i = 0; i < count; i++) {
            specs[i] = SpecialistDecisionSchema.forModule(moduleIds[i], stateText);
        }

        String model = settings.model();
        String queueId = ProcessingQueueRegistry.startRunning(
                "decision_model_batch",
                npcId,
                DecisionModelCatalog.displayLabel(model)
                        + " · " + DecisionModelCatalog.executionLocationLabel(model)
                        + " · specialists=" + count,
                System.currentTimeMillis());

        long startedNs = SystemClock.elapsedRealtimeNanos();
        long pssBeforeKb = Debug.getPss();
        long heapBeforeBytes = usedJavaHeapBytes();
        boolean success = false;
        Throwable failure = null;
        try {
            DecisionModelEvaluation[] evaluations = DecisionModelCatalog.isLocal(model)
                    ? evaluateLocalBatch(stateText, specs, model)
                    : evaluateCloudBatch(stateText, specs, model);
            if (evaluations.length != count) {
                throw new IllegalStateException(
                        "判断モデルの結果件数が不正です: " + evaluations.length + " / " + count);
            }
            JSONObject[] results = new JSONObject[count];
            for (int i = 0; i < count; i++) {
                results[i] = adapt(
                        moduleIds[i],
                        moduleLabels[i],
                        specs[i],
                        evaluations[i]);
            }
            success = true;
            ProcessingQueueRegistry.markCompleted(queueId);
            return results;
        } catch (Exception error) {
            failure = error;
            ProcessingQueueRegistry.markFailed(queueId, error);
            throw error;
        } finally {
            long durationMs = Math.max(
                    0L,
                    (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L);
            new ClefPerformanceStore(appContext).record(
                    DecisionModelCatalog.displayLabel(model)
                            + " · " + DecisionModelCatalog.executionLocationLabel(model)
                            + " · 9専門joint",
                    durationMs,
                    pssBeforeKb,
                    Debug.getPss(),
                    heapBeforeBytes,
                    usedJavaHeapBytes(),
                    success,
                    failure);
        }
    }

    private DecisionModelEvaluation[] evaluateLocalBatch(
            String state,
            SpecialistDecisionSchema.Spec[] specs,
            String model
    ) {
        if (!DecisionModelCatalog.LOCAL_CLEF_FLASH_Q4_K_M.equals(model)) {
            throw new IllegalStateException("未対応のローカル判断モデルです: " + model);
        }
        if (!localRepository.isDownloaded()) {
            throw new IllegalStateException(
                    "ローカル判断モデル Clef-flash が未ダウンロードです。AI管理からダウンロードしてください。");
        }
        File modelFile = localRepository.modelFile();
        double[] scores = ClefNativeRuntime.decideBatch(modelFile, state, specs);

        DecisionModelEvaluation[] result = new DecisionModelEvaluation[specs.length];
        int cursor = 0;
        for (int i = 0; i < specs.length; i++) {
            SpecialistDecisionSchema.Spec spec = specs[i];
            int optionCount = spec.criteria.size();
            double[] optionScores = new double[optionCount];
            System.arraycopy(scores, cursor, optionScores, 0, optionCount);
            cursor += optionCount;
            double[] optionProbabilities = softmax(optionScores);
            int best = 0;
            for (int j = 1; j < optionProbabilities.length; j++) {
                if (optionProbabilities[j] > optionProbabilities[best]) best = j;
            }
            double[] evidenceProbabilities = softmax(new double[]{
                    scores[cursor],
                    scores[cursor + 1]
            });
            cursor += 2;
            result[i] = new DecisionModelEvaluation(
                    spec.optionIds()[best],
                    optionProbabilities[best],
                    evidenceProbabilities[0],
                    model,
                    "local");
        }
        if (cursor != scores.length) {
            throw new IllegalStateException(
                    "判断モデルscoreの読み取り位置が不正です: " + cursor + " / " + scores.length);
        }
        return result;
    }

    private DecisionModelEvaluation[] evaluateCloudBatch(
            String state,
            SpecialistDecisionSchema.Spec[] specs,
            String model
    ) throws Exception {
        String token = tokenStore.load();
        return new CloudflareDecisionModelClient(
                settings.cloudflareAccountId(),
                token,
                model).evaluateBatch(state, specs);
    }

    static JSONObject adapt(
            String moduleId,
            String moduleLabel,
            SpecialistDecisionSchema.Spec spec,
            DecisionModelEvaluation evaluation
    ) {
        String description = spec.descriptionFor(evaluation.choiceId);
        String content = moduleLabel + ": " + description
                + " [choice=" + evaluation.choiceId
                + ", p=" + String.format(Locale.US, "%.2f", evaluation.choiceProbability)
                + ", evidence=" + String.format(Locale.US, "%.2f", evaluation.evidenceProbability)
                + "]";
        return new JSONObject()
                .put("module", moduleId)
                .put("content", content)
                .put("confidence", evaluation.choiceProbability)
                .put("salient_facts", new JSONArray())
                .put("personality_effect", "")
                .put("graph_used_node_ids", new JSONArray())
                .put("decision_model", new JSONObject()
                        .put("model", evaluation.modelId)
                        .put("execution_location", evaluation.executionLocation)
                        .put("question_id", spec.questionId)
                        .put("choice", evaluation.choiceId)
                        .put("choice_probability", evaluation.choiceProbability)
                        .put("evidence_sufficient_probability", evaluation.evidenceProbability));
    }

    private static double[] softmax(double[] scores) {
        double max = Double.NEGATIVE_INFINITY;
        for (double score : scores) {
            if (!Double.isFinite(score)) {
                throw new IllegalStateException("Decision model score is not finite");
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
            throw new IllegalStateException("Decision model softmax failed");
        }
        for (int i = 0; i < probabilities.length; i++) probabilities[i] /= sum;
        return probabilities;
    }

    private static long usedJavaHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }
}
