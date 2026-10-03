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

    JSONObject request(
            String moduleId,
            String moduleLabel,
            String role,
            String personalityRule,
            JSONObject commonContext,
            JSONObject graphFocus
    ) throws Exception {
        JSONObject state = new JSONObject()
                .put("module", moduleId)
                .put("module_label", moduleLabel)
                .put("role", role)
                .put("personality_rule", personalityRule)
                .put("common_context", commonContext == null ? new JSONObject() : commonContext)
                .put("cognitive_graph_focus", graphFocus == null ? new JSONObject() : graphFocus)
                .put("runtime_contract", new JSONObject()
                        .put("engine", "decision_model")
                        .put("free_form_generation", false)
                        .put("same_cycle_peer_outputs_available", false));
        String stateText = state.toString();
        SpecialistDecisionSchema.Spec spec =
                SpecialistDecisionSchema.forModule(moduleId, stateText);
        String model = settings.model();

        String queueId = ProcessingQueueRegistry.startRunning(
                "decision_model_request",
                npcId,
                DecisionModelCatalog.displayLabel(model)
                        + " · " + DecisionModelCatalog.executionLocationLabel(model)
                        + " · brain_stage=" + moduleId,
                System.currentTimeMillis());

        long startedNs = SystemClock.elapsedRealtimeNanos();
        long pssBeforeKb = Debug.getPss();
        long heapBeforeBytes = usedJavaHeapBytes();
        boolean success = false;
        Throwable failure = null;
        try {
            DecisionModelEvaluation evaluation = DecisionModelCatalog.isLocal(model)
                    ? evaluateLocal(stateText, spec, model)
                    : evaluateCloud(stateText, spec, model);
            JSONObject result = adapt(moduleId, moduleLabel, spec, evaluation);
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
                    DecisionModelCatalog.displayLabel(model)
                            + " · " + DecisionModelCatalog.executionLocationLabel(model),
                    durationMs,
                    pssBeforeKb,
                    Debug.getPss(),
                    heapBeforeBytes,
                    usedJavaHeapBytes(),
                    success,
                    failure);
        }
    }

    private DecisionModelEvaluation evaluateLocal(
            String state,
            SpecialistDecisionSchema.Spec spec,
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
        double[] scores = ClefNativeRuntime.decide(
                modelFile,
                state,
                spec.questionId,
                spec.instruction,
                spec.evidenceInstruction,
                spec.optionIds(),
                spec.optionDescriptions());

        int optionCount = spec.criteria.size();
        double[] optionScores = new double[optionCount];
        System.arraycopy(scores, 0, optionScores, 0, optionCount);
        double[] optionProbabilities = softmax(optionScores);
        int best = 0;
        for (int i = 1; i < optionProbabilities.length; i++) {
            if (optionProbabilities[i] > optionProbabilities[best]) best = i;
        }
        double[] evidenceProbabilities = softmax(new double[]{
                scores[optionCount],
                scores[optionCount + 1]
        });
        return new DecisionModelEvaluation(
                spec.optionIds()[best],
                optionProbabilities[best],
                evidenceProbabilities[0],
                model,
                "local");
    }

    private DecisionModelEvaluation evaluateCloud(
            String state,
            SpecialistDecisionSchema.Spec spec,
            String model
    ) throws Exception {
        String token = tokenStore.load();
        return new CloudflareDecisionModelClient(
                settings.cloudflareAccountId(),
                token,
                model).evaluate(state, spec);
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
