package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

final class DungeonBrainRuntime {
    interface Listener {
        void onStageStarted(String stageId, String stageLabel, int current, int total);

        void onStageCompleted(
                String stageId,
                String stageLabel,
                int current,
                int total,
                String summary,
                double confidence,
                JSONArray salientFacts,
                String personalityEffect
        );
    }

    static final class Result {
        final DungeonIntent intent;
        final JSONArray trace;
        final JSONObject cognitiveGraph;
        final String publicSummary;

        Result(
                DungeonIntent intent,
                JSONArray trace,
                JSONObject cognitiveGraph,
                String publicSummary
        ) {
            this.intent = intent;
            this.trace = trace == null ? new JSONArray() : trace;
            this.cognitiveGraph = cognitiveGraph == null ? new JSONObject() : cognitiveGraph;
            this.publicSummary = publicSummary == null ? "" : publicSummary.trim();
        }
    }

    private final Context appContext;

    DungeonBrainRuntime(Context context) {
        appContext = context.getApplicationContext();
    }

    Result run(
            String npcId,
            DungeonState state,
            String triggerReason,
            String apiKey,
            String reasoningEffort,
            Listener listener
    ) throws Exception {
        if (state == null) throw new IllegalArgumentException("DungeonState is required");
        Context storageContext = storageContext(npcId);
        OpenAiClient client = new OpenAiClient(appContext, apiKey, reasoningEffort);
        BrainEngine engine = new BrainEngine(
                client,
                new MemoryStore(storageContext),
                new CharacterStateStore(storageContext));
        JSONArray trace = new JSONArray();
        JSONObject runtimeJson = DungeonPerception.buildRuntimeJson(state, triggerReason);

        BrainEngine.Decision decision = engine.thinkDecision(
                runtimeJson.toString(),
                new BrainEngine.ProgressListener() {
                    @Override
                    public void onStageStarted(
                            String stageId,
                            String stageLabel,
                            int current,
                            int total
                    ) {
                        if (listener != null) {
                            listener.onStageStarted(stageId, stageLabel, current, total);
                        }
                    }

                    @Override
                    public void onStageCompleted(
                            String stageId,
                            String stageLabel,
                            int current,
                            int total,
                            String summary,
                            double confidence,
                            JSONArray salientFacts,
                            String personalityEffect
                    ) {
                        JSONObject item = new JSONObject();
                        try {
                            item.put("stage_id", stageId);
                            item.put("stage_label", stageLabel);
                            item.put("summary", summary == null ? "" : summary);
                            item.put("confidence", confidence);
                            item.put("salient_facts", salientFacts == null
                                    ? new JSONArray() : new JSONArray(salientFacts.toString()));
                            item.put("personality_effect", personalityEffect == null
                                    ? "" : personalityEffect);
                            item.put("model", OpenAiClient.MODEL);
                            item.put("reasoning_effort",
                                    ModelSettingsStore.normalizeReasoningEffort(reasoningEffort));
                        } catch (Exception ignored) {
                        }
                        trace.put(item);
                        if (listener != null) {
                            listener.onStageCompleted(
                                    stageId,
                                    stageLabel,
                                    current,
                                    total,
                                    summary,
                                    confidence,
                                    salientFacts,
                                    personalityEffect);
                        }
                    }
                },
                false);

        String summary = decision.internalState();
        DungeonIntent intent = DungeonIntent.fromEnvironmentAction(
                decision.environmentAction(),
                state.floor,
                state.turn,
                summary);
        if (intent == null) {
            throw new IllegalStateException("Dungeon Brain returned invalid environment_action");
        }
        return new Result(intent, trace, decision.cognitiveGraph(), summary);
    }

    private Context storageContext(String npcId) {
        return "npc2".equals(npcId)
                ? new NpcStorageContext(appContext, "npc2")
                : appContext;
    }
}
