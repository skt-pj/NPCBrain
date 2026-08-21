package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

final class BrainEngine {
    interface ProgressListener {
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

    private static final class Module {
        final String id;
        final String label;
        final String role;
        final String personalityRule;

        Module(String id, String label, String role, String personalityRule) {
            this.id = id;
            this.label = label;
            this.role = role;
            this.personalityRule = personalityRule;
        }
    }

    private static final List<Module> MODULES = Arrays.asList(
            new Module(
                    "perception",
                    "知覚",
                    "Extract only observable facts, entities, constraints, and explicit uncertainty from the current scene. Do not invent missing facts.",
                    "Keep perception itself personality-neutral. Traits may not rewrite observed reality. If useful, only tag grounded cues such as social, reward, threat-candidate, novelty, rule, or goal relevance."
            ),
            new Module(
                    "salience",
                    "注意・重要度",
                    "Rank what deserves attention now: goals, threats, opportunities, anomalies, conflicts, unknowns, and time-critical information.",
                    "Use the shared personality as bounded attention weights: Extraversion emphasizes social/reward opportunities; Neuroticism threat/uncertainty/rejection; Openness novelty/information; Conscientiousness duties/unfinished goals/standards; Agreeableness others' needs/cooperation/conflict. The situation can override traits."
            ),
            new Module(
                    "episodic_memory",
                    "エピソード記憶",
                    "Compare the present situation with retrieved past experiences. Separate remembered events from current observations and do not assume the past will repeat.",
                    "Personality and current state may bias which already-retrieved episodes feel most self-relevant, but may not alter what happened. Preserve uncertainty and avoid a self-confirming memory loop."
            ),
            new Module(
                    "semantic_memory",
                    "意味記憶",
                    "Use consolidated long-term knowledge, including typed world facts and character adaptations such as goals, values, fears, relationships, role identity, self-beliefs, and strategies.",
                    "Stable traits do not change factual world knowledge. Use character adaptations as learned personal context. Mark conflicts between memory and current evidence rather than forcing consistency."
            ),
            new Module(
                    "world_model",
                    "世界モデル・予測",
                    "Predict plausible next states and causal consequences of candidate actions while respecting physical, social, and scene constraints.",
                    "Separate objective likelihood from character-specific concern or desirability. Traits may change which plausible futures receive attention, but must not silently change causal probability or hard constraints."
            ),
            new Module(
                    "executive_control",
                    "実行制御・計画",
                    "Maintain the character's active goal, decompose the problem into subgoals, coordinate prior module outputs, and prepare candidate plans.",
                    "Use personality to modulate persistence, switching threshold, exploration, order preference, and distraction tolerance. Conscientiousness is not a generic intelligence boost; Openness is not random behavior."
            ),
            new Module(
                    "valuation",
                    "価値判断",
                    "Evaluate candidate actions by reward, safety/threat, affiliation, status, cooperation/fairness, curiosity/information gain, duty/goal completion, effort/cost, reversibility, and relationship impact.",
                    "This is the primary personality entry point for motivation and affect. Stable traits alter value weights; current valence/arousal/stress alter momentary gain. Produce a character-specific preference without pretending it is objective truth."
            ),
            new Module(
                    "error_monitor",
                    "誤り監視",
                    "Detect contradictions, unsupported assumptions, missing information, rule/goal violations, and likely failure modes in accumulated working memory.",
                    "Separate evidence-based error probability from how strongly this character worries about or reacts to a possible error. Personality may change concern and checking, not factual correctness."
            ),
            new Module(
                    "action_selection",
                    "行動選択",
                    "Choose one concrete in-world action from feasible candidates using prior predictions and personality-weighted valuation.",
                    "Personality must become behavior here. Do not give advice to the user and do not describe what an assistant should recommend. Select what this character actually does now."
            )
    );

    private static final String GLOBAL_ID = "global_workspace";
    private static final String GLOBAL_LABEL = "Global Workspace";

    private final OpenAiClient client;
    private final MemoryStore memoryStore;
    private final CharacterStateStore characterStore;

    BrainEngine(OpenAiClient client, MemoryStore memoryStore, CharacterStateStore characterStore) {
        this.client = client;
        this.memoryStore = memoryStore;
        this.characterStore = characterStore;
    }

    String think(String userInput, ProgressListener listener) throws Exception {
        JSONObject characterState = characterStore.snapshotJson();
        characterState.put("characteristic_adaptations", memoryStore.characterAdaptations());
        String longTermMemory = memoryStore.contextFor(userInput, characterState);
        JSONArray workingMemory = new JSONArray();
        int total = MODULES.size() + 1;

        for (int i = 0; i < MODULES.size(); i++) {
            Module module = MODULES.get(i);
            int current = i + 1;
            if (listener != null) {
                listener.onStageStarted(module.id, module.label, current, total);
            }

            JSONObject context = new JSONObject();
            context.put("user_input", userInput);
            context.put("character_state", characterState);
            context.put("long_term_memory", new JSONObject(longTermMemory));
            context.put("working_memory", workingMemory);

            JSONObject result = client.requestJson(modulePrompt(module, context));
            result.put("module", module.id);
            workingMemory.put(result);

            if (listener != null) {
                JSONArray facts = result.optJSONArray("salient_facts");
                if (facts == null) facts = new JSONArray();
                listener.onStageCompleted(
                        module.id,
                        module.label,
                        current,
                        total,
                        result.optString("content", "").trim(),
                        clamp01(result.optDouble("confidence", 0.0)),
                        facts,
                        result.optString("personality_effect", "").trim()
                );
            }
        }

        if (listener != null) {
            listener.onStageStarted(GLOBAL_ID, GLOBAL_LABEL, total, total);
        }

        JSONObject finalContext = new JSONObject();
        finalContext.put("user_input", userInput);
        finalContext.put("character_state", characterState);
        finalContext.put("long_term_memory", new JSONObject(longTermMemory));
        finalContext.put("working_memory", workingMemory);

        JSONObject finalResult = client.requestJson(globalWorkspacePrompt(finalContext));
        String utterance = finalResult.optString("npc_utterance", "").trim();
        String action = finalResult.optString("npc_action", "").trim();
        String internalState = finalResult.optString("internal_state_summary", "").trim();
        String personalityEffect = finalResult.optString("personality_effect", "").trim();
        String memorySummary = finalResult.optString("memory_summary", "").trim();
        double memoryImportance = clamp01(finalResult.optDouble("memory_importance", 0.5));
        JSONArray semanticFacts = finalResult.optJSONArray("semantic_facts");
        if (semanticFacts == null) semanticFacts = new JSONArray();

        JSONObject dynamicState = finalResult.optJSONObject("dynamic_state");
        if (dynamicState != null) {
            characterStore.updateDynamicState(dynamicState);
        }

        if (listener != null) {
            JSONArray workspaceFacts = new JSONArray();
            if (!utterance.isEmpty()) workspaceFacts.put("発話: " + utterance);
            if (!action.isEmpty()) workspaceFacts.put("行動: " + action);
            listener.onStageCompleted(
                    GLOBAL_ID,
                    GLOBAL_LABEL,
                    total,
                    total,
                    internalState.isEmpty() ? "発話と行動を統合しました。" : internalState,
                    clamp01(finalResult.optDouble("confidence", 0.0)),
                    workspaceFacts,
                    personalityEffect
            );
        }

        String display = renderNpcOutput(utterance, action, internalState);
        memoryStore.remember(
                userInput,
                display,
                memorySummary,
                memoryImportance,
                semanticFacts
        );
        return display;
    }

    static int moduleCount() {
        return MODULES.size();
    }

    static String[] stageIds() {
        String[] ids = new String[MODULES.size() + 1];
        for (int i = 0; i < MODULES.size(); i++) {
            ids[i] = MODULES.get(i).id;
        }
        ids[MODULES.size()] = GLOBAL_ID;
        return ids;
    }

    static String stageLabel(String stageId) {
        for (Module module : MODULES) {
            if (module.id.equals(stageId)) return module.label;
        }
        if (GLOBAL_ID.equals(stageId)) return GLOBAL_LABEL;
        return stageId;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String renderNpcOutput(String utterance, String action, String internalState) {
        StringBuilder result = new StringBuilder();
        if (utterance != null && !utterance.trim().isEmpty()) {
            result.append('「').append(utterance.trim()).append('」');
        }
        if (action != null && !action.trim().isEmpty()) {
            if (result.length() > 0) result.append("\n\n");
            result.append(action.trim());
        }
        if (result.length() == 0) {
            return internalState == null || internalState.trim().isEmpty()
                    ? "反応を決められませんでした。"
                    : internalState.trim();
        }
        return result.toString();
    }

    private static String modulePrompt(Module module, JSONObject context) {
        return "You are the " + module.id + " function inside a brain-inspired NPC cognitive architecture.\n"
                + "You are NOT a user-facing assistant. Analyze the scene from the character's situated point of view while preserving the module's responsibility.\n"
                + "Role: " + module.role + "\n"
                + "Personality integration rule: " + module.personalityRule + "\n"
                + "character_state contains stable Big Five traits, current affective state, speech style, and typed characteristic adaptations. "
                + "long_term_memory contains retrieved episodic and semantic evidence. working_memory contains earlier specialist outputs from this same cycle. "
                + "Treat memory and personality as biases/context, not permission to invent facts.\n"
                + "The content field is a concise public diagnostic summary for the brain monitor. "
                + "personality_effect is one short sentence describing which trait/state/adaptation materially influenced this module; use an empty string when none mattered. "
                + "Do not provide hidden chain-of-thought, private scratch work, or step-by-step internal reasoning.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"" + module.id + "\",\"content\":\"concise public summary\",\"confidence\":0.0,"
                + "\"salient_facts\":[\"important grounded fact\"],\"personality_effect\":\"short public effect or empty\"}\n"
                + "confidence must be between 0.0 and 1.0. Keep content concise. Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }

    private static String globalWorkspacePrompt(JSONObject context) {
        return "You are the existing Global Workspace of a brain-inspired NPC cognitive architecture. "
                + "Integrate the nine specialist outputs, the character's stable personality, current affective state, and long-term adaptations. "
                + "The user_input is a scene/event presented to the character, NOT a request for an AI assistant answer. "
                + "Do not give generic advice, explain the architecture, mention being an AI, or append analysis/reasons to the product output. "
                + "Choose what the character actually says and does in-world. The situation may override personality; personality is a probabilistic bias, not a script. "
                + "Resolve conflicts while preserving uncertainty and physical/social constraints.\n"
                + "npc_utterance must contain only words the character actually says, without surrounding quotation marks. Use an empty string if the character stays silent. "
                + "npc_action must be a short natural in-world action description, not advice or a plan list. "
                + "internal_state_summary and personality_effect are developer-facing brain-monitor summaries only, not hidden chain-of-thought. "
                + "dynamic_state updates valence (-1.0..1.0), arousal (0.0..1.0), and stress (0.0..1.0) after this event. "
                + "semantic_facts may contain durable learned memory objects using types world_fact, self_belief, goal, value, fear, relationship, habit_strategy, or role_identity. "
                + "Do not store API keys, secrets, transient speculation, or private reasoning.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"global_workspace\",\"npc_utterance\":\"spoken words or empty\",\"npc_action\":\"in-world action or empty\","
                + "\"internal_state_summary\":\"concise public state summary\",\"confidence\":0.0,\"personality_effect\":\"short public effect or empty\","
                + "\"dynamic_state\":{\"valence\":0.0,\"arousal\":0.0,\"stress\":0.0},"
                + "\"memory_summary\":\"brief episodic summary worth recalling later\",\"memory_importance\":0.0,"
                + "\"semantic_facts\":[{\"type\":\"world_fact\",\"text\":\"durable fact\"}]}\n"
                + "confidence and memory_importance must be between 0.0 and 1.0. semantic_facts may be [] when none qualify. "
                + "Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }
}
