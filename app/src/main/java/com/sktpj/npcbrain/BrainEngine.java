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
                JSONArray salientFacts
        );
    }

    private static final class Module {
        final String id;
        final String label;
        final String role;

        Module(String id, String label, String role) {
            this.id = id;
            this.label = label;
            this.role = role;
        }
    }

    private static final List<Module> MODULES = Arrays.asList(
            new Module("perception", "知覚", "Extract only observable facts, entities, constraints, and explicit uncertainty from the current input. Do not invent missing facts."),
            new Module("salience", "注意・重要度", "Identify what deserves attention now: goals, threats, anomalies, conflicts, unknowns, and time-critical information."),
            new Module("episodic_memory", "エピソード記憶", "Use retrieved episodic memory to compare the present situation with relevant past experiences. Separate remembered events from current observations and do not assume the past will repeat."),
            new Module("semantic_memory", "意味記憶", "Use retrieved semantic memory as consolidated long-term knowledge. Prefer stable, repeatedly supported facts and explicitly mark uncertain or conflicting memories."),
            new Module("world_model", "世界モデル・予測", "Predict plausible next states and causal consequences of candidate actions. Mark assumptions, uncertainty, and alternative outcomes."),
            new Module("executive_control", "実行制御・計画", "Maintain the current goal in working memory, decompose the problem into subgoals, and propose an ordered plan using prior module outputs."),
            new Module("valuation", "価値判断", "Evaluate candidate actions by expected utility, cost, risk, reversibility, urgency, and consistency with the current goal."),
            new Module("error_monitor", "誤り監視", "Search the accumulated working memory for contradictions, unsupported assumptions, missing information, and likely failure modes. Trigger correction when needed."),
            new Module("action_selection", "行動選択", "Select the best next action from the available evidence and state what should be done now, not merely analyzed.")
    );

    private static final String GLOBAL_ID = "global_workspace";
    private static final String GLOBAL_LABEL = "Global Workspace";

    private final OpenAiClient client;
    private final MemoryStore memoryStore;

    BrainEngine(OpenAiClient client, MemoryStore memoryStore) {
        this.client = client;
        this.memoryStore = memoryStore;
    }

    String think(String userInput, ProgressListener listener) throws Exception {
        String longTermMemory = memoryStore.contextFor(userInput);
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
                        facts
                );
            }
        }

        if (listener != null) {
            listener.onStageStarted(GLOBAL_ID, GLOBAL_LABEL, total, total);
        }
        JSONObject finalContext = new JSONObject();
        finalContext.put("user_input", userInput);
        finalContext.put("long_term_memory", new JSONObject(longTermMemory));
        finalContext.put("working_memory", workingMemory);
        JSONObject finalResult = client.requestJson(globalWorkspacePrompt(finalContext));

        String content = finalResult.optString("content", "").trim();
        String action = finalResult.optString("action", "").trim();
        String rationale = finalResult.optString("rationale", "").trim();
        String memorySummary = finalResult.optString("memory_summary", "").trim();
        double memoryImportance = clamp01(finalResult.optDouble("memory_importance", 0.5));
        JSONArray semanticFacts = finalResult.optJSONArray("semantic_facts");
        if (semanticFacts == null) semanticFacts = new JSONArray();

        if (listener != null) {
            JSONArray workspaceFacts = new JSONArray();
            if (!action.isEmpty()) workspaceFacts.put("次の行動: " + action);
            if (!rationale.isEmpty()) workspaceFacts.put("理由: " + rationale);
            listener.onStageCompleted(
                    GLOBAL_ID,
                    GLOBAL_LABEL,
                    total,
                    total,
                    content,
                    clamp01(finalResult.optDouble("confidence", 0.0)),
                    workspaceFacts
            );
        }

        String display = content;
        if (!action.isEmpty()) display += "\n\n次の行動: " + action;
        if (!rationale.isEmpty()) display += "\n\n理由: " + rationale;

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

    private static String modulePrompt(Module module, JSONObject context) {
        return "You are the " + module.id + " module in a brain-inspired cognitive architecture.\n"
                + "Role: " + module.role + "\n"
                + "The working_memory field contains earlier specialist outputs from this same cognitive cycle. "
                + "The long_term_memory field contains retrieved episodic and semantic memory. "
                + "Treat both as fallible evidence, not unquestionable truth.\n"
                + "The content field is a short public-facing decision summary for debugging. "
                + "Do not provide hidden chain-of-thought, private scratch work, or step-by-step internal reasoning.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"" + module.id + "\",\"content\":\"concise public summary\",\"confidence\":0.0,\"salient_facts\":[\"important fact\"]}\n"
                + "confidence must be between 0.0 and 1.0. Keep content concise. Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }

    private static String globalWorkspacePrompt(JSONObject context) {
        return "You are the Global Workspace and final integrator of a brain-inspired cognitive architecture. "
                + "Reconcile the specialized modules, resolve conflicts, preserve uncertainty, and produce one coherent response and one concrete next action. "
                + "Working memory is temporary for this cycle; long-term memory should only keep information useful in future cycles. "
                + "The content and rationale fields are public-facing summaries; never expose hidden chain-of-thought or private scratch work.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"global_workspace\",\"content\":\"final answer\",\"confidence\":0.0,\"action\":\"best next action\",\"rationale\":\"short public reason\",\"memory_summary\":\"brief episodic summary worth recalling later\",\"memory_importance\":0.0,\"semantic_facts\":[\"durable fact explicitly supported by the current input or high-confidence memory\"]}\n"
                + "confidence and memory_importance must be between 0.0 and 1.0. "
                + "semantic_facts must contain only durable facts that are genuinely useful later; use [] when none qualify. "
                + "Do not store transient speculation, chain-of-thought, API keys, or secrets in semantic_facts. "
                + "Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }
}
