package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

final class BrainEngine {
    interface ProgressListener {
        void onStage(String stage, int current, int total);
    }

    private static final class Module {
        final String id;
        final String role;

        Module(String id, String role) {
            this.id = id;
            this.role = role;
        }
    }

    private static final List<Module> MODULES = Arrays.asList(
            new Module("perception", "Extract only observable facts, entities, constraints, and explicit uncertainty from the current input. Do not invent missing facts."),
            new Module("salience", "Identify what deserves attention now: goals, threats, anomalies, conflicts, unknowns, and time-critical information."),
            new Module("episodic_memory", "Use retrieved episodic memory to compare the present situation with relevant past experiences. Separate remembered events from current observations and do not assume the past will repeat."),
            new Module("semantic_memory", "Use retrieved semantic memory as consolidated long-term knowledge. Prefer stable, repeatedly supported facts and explicitly mark uncertain or conflicting memories."),
            new Module("world_model", "Predict plausible next states and causal consequences of candidate actions. Mark assumptions, uncertainty, and alternative outcomes."),
            new Module("executive_control", "Maintain the current goal in working memory, decompose the problem into subgoals, and propose an ordered plan using prior module outputs."),
            new Module("valuation", "Evaluate candidate actions by expected utility, cost, risk, reversibility, urgency, and consistency with the current goal."),
            new Module("error_monitor", "Search the accumulated working memory for contradictions, unsupported assumptions, missing information, and likely failure modes. Trigger correction when needed."),
            new Module("action_selection", "Select the best next action from the available evidence and state what should be done now, not merely analyzed.")
    );

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
            if (listener != null) listener.onStage(module.id, i + 1, total);

            JSONObject context = new JSONObject();
            context.put("user_input", userInput);
            context.put("long_term_memory", new JSONObject(longTermMemory));
            context.put("working_memory", workingMemory);

            JSONObject result = client.requestJson(modulePrompt(module, context));
            result.put("module", module.id);
            workingMemory.put(result);
        }

        if (listener != null) listener.onStage("global_workspace", total, total);
        JSONObject finalContext = new JSONObject();
        finalContext.put("user_input", userInput);
        finalContext.put("long_term_memory", new JSONObject(longTermMemory));
        finalContext.put("working_memory", workingMemory);
        JSONObject finalResult = client.requestJson(globalWorkspacePrompt(finalContext));

        String content = finalResult.optString("content", "").trim();
        String action = finalResult.optString("action", "").trim();
        String rationale = finalResult.optString("rationale", "").trim();
        String memorySummary = finalResult.optString("memory_summary", "").trim();
        double memoryImportance = finalResult.optDouble("memory_importance", 0.5);
        JSONArray semanticFacts = finalResult.optJSONArray("semantic_facts");
        if (semanticFacts == null) semanticFacts = new JSONArray();

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

    private static String modulePrompt(Module module, JSONObject context) {
        return "You are the " + module.id + " module in a brain-inspired cognitive architecture.\n"
                + "Role: " + module.role + "\n"
                + "The working_memory field contains earlier specialist outputs from this same cognitive cycle. "
                + "The long_term_memory field contains retrieved episodic and semantic memory. "
                + "Treat both as fallible evidence, not unquestionable truth.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"" + module.id + "\",\"content\":\"concise analysis\",\"confidence\":0.0,\"salient_facts\":[\"fact\"]}\n"
                + "confidence must be between 0.0 and 1.0. Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }

    private static String globalWorkspacePrompt(JSONObject context) {
        return "You are the Global Workspace and final integrator of a brain-inspired cognitive architecture. "
                + "Reconcile the specialized modules, resolve conflicts, preserve uncertainty, and produce one coherent response and one concrete next action. "
                + "Working memory is temporary for this cycle; long-term memory should only keep information useful in future cycles.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"global_workspace\",\"content\":\"final answer\",\"confidence\":0.0,\"action\":\"best next action\",\"rationale\":\"short reason\",\"memory_summary\":\"brief episodic summary worth recalling later\",\"memory_importance\":0.0,\"semantic_facts\":[\"durable fact explicitly supported by the current input or high-confidence memory\"]}\n"
                + "confidence and memory_importance must be between 0.0 and 1.0. "
                + "semantic_facts must contain only durable facts that are genuinely useful later; use [] when none qualify. "
                + "Do not store transient speculation, chain-of-thought, API keys, or secrets in semantic_facts. "
                + "Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }
}
