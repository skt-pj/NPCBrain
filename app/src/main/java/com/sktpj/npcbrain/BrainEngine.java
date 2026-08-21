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
            new Module("perception", "Extract only observable facts, entities, constraints, and explicit uncertainty from the input. Do not invent missing facts."),
            new Module("salience", "Identify what deserves attention now: goals, threats, anomalies, conflicts, unknowns, and time-critical information."),
            new Module("episodic_memory", "Compare the current situation with supplied episodic memory. Retrieve only relevant prior patterns and clearly separate memory from current facts."),
            new Module("world_model", "Predict plausible next states and causal consequences of candidate actions. Mark assumptions and uncertainty."),
            new Module("executive_control", "Maintain the goal, decompose the problem into subgoals, and propose an ordered plan using prior module outputs."),
            new Module("valuation", "Evaluate candidate actions by expected utility, cost, risk, reversibility, and consistency with the goal."),
            new Module("error_monitor", "Search the accumulated reasoning for contradictions, unsupported assumptions, missing information, and likely failure modes."),
            new Module("action_selection", "Select the best next action from the available evidence and state what should be done now, not merely analyzed.")
    );

    private final OpenAiClient client;
    private final MemoryStore memoryStore;

    BrainEngine(OpenAiClient client, MemoryStore memoryStore) {
        this.client = client;
        this.memoryStore = memoryStore;
    }

    String think(String userInput, ProgressListener listener) throws Exception {
        String memory = memoryStore.recentContext();
        JSONArray moduleOutputs = new JSONArray();
        int total = MODULES.size() + 1;

        for (int i = 0; i < MODULES.size(); i++) {
            Module module = MODULES.get(i);
            if (listener != null) listener.onStage(module.id, i + 1, total);
            JSONObject context = new JSONObject();
            context.put("user_input", userInput);
            context.put("episodic_memory", memory);
            context.put("previous_modules", moduleOutputs);
            JSONObject result = client.requestJson(modulePrompt(module, context));
            result.put("module", module.id);
            moduleOutputs.put(result);
        }

        if (listener != null) listener.onStage("global_workspace", total, total);
        JSONObject finalContext = new JSONObject();
        finalContext.put("user_input", userInput);
        finalContext.put("module_outputs", moduleOutputs);
        JSONObject finalResult = client.requestJson(globalWorkspacePrompt(finalContext));

        String content = finalResult.optString("content", "").trim();
        String action = finalResult.optString("action", "").trim();
        String rationale = finalResult.optString("rationale", "").trim();
        String display = content;
        if (!action.isEmpty()) display += "\n\n次の行動: " + action;
        if (!rationale.isEmpty()) display += "\n\n理由: " + rationale;
        memoryStore.remember(userInput, display);
        return display;
    }

    static int moduleCount() {
        return MODULES.size();
    }

    private static String modulePrompt(Module module, JSONObject context) {
        return "You are the " + module.id + " module in a brain-inspired cognitive architecture.\n"
                + "Role: " + module.role + "\n"
                + "Use the previous module outputs as evidence, not as unquestionable truth.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"" + module.id + "\",\"content\":\"concise analysis\",\"confidence\":0.0,\"salient_facts\":[\"fact\"]}\n"
                + "confidence must be between 0.0 and 1.0. Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }

    private static String globalWorkspacePrompt(JSONObject context) {
        return "You are the Global Workspace and final integrator of a brain-inspired cognitive architecture. "
                + "Reconcile the specialized modules, resolve conflicts, preserve uncertainty, and produce one coherent response and one concrete next action.\n"
                + "Return ONLY JSON. The word JSON and the exact JSON format are mandatory.\n"
                + "JSON format:\n"
                + "{\"module\":\"global_workspace\",\"content\":\"final answer\",\"confidence\":0.0,\"action\":\"best next action\",\"rationale\":\"short reason\",\"memory_summary\":\"what should be remembered\"}\n"
                + "confidence must be between 0.0 and 1.0. Do not add keys outside this JSON format.\n"
                + "Input JSON:\n" + context.toString();
    }
}
