package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

/** JSON-Schema constraints used by LiteRT-LM constrained decoding. */
final class LocalJsonSchema {
    private static final String SPECIALIST_MARKER = "Specialist-specific contract follows.";
    private static final String SPECIALIST_ID_PREFIX = "You are the ";
    private static final String SPECIALIST_ID_SUFFIX = " function inside the brain-inspired NPC cognitive architecture.";
    private static final String AMBIENT_MARKER = "one brief PUBLIC inner-life monitor update";
    private static final String MEMORY_APPRAISAL_MARKER =
            "You are the encoding/appraisal pass of a memory-maintenance system.";
    private static final String MEMORY_CONSOLIDATION_MARKER =
            "You are the consolidation/schema pass.";
    private static final String MEMORY_RETENTION_MARKER =
            "You are the retention/forgetting pass.";

    private LocalJsonSchema() {
    }

    static String schemaFor(String prompt, OpenAiClient.FunctionTool tool) {
        String source = prompt == null ? "" : prompt;
        try {
            if (tool != null && tool.requiredInvocation()) {
                return toolEnvelopeSchema(tool.name()).toString();
            }
            if (isSpecialist(source)) {
                return specialistSchema(expectedSpecialistId(source)).toString();
            }
            if (OpenAiClient.isGlobalWorkspacePrompt(source)) {
                // Optional local tools need to permit either a final workspace object or a tool envelope.
                // A generic object constraint still guarantees syntactically complete JSON in that case.
                return tool == null ? globalWorkspaceSchema().toString() : genericObjectSchema().toString();
            }
            if (source.contains(MEMORY_APPRAISAL_MARKER)) {
                return memoryAppraisalSchema().toString();
            }
            if (source.contains(MEMORY_CONSOLIDATION_MARKER)) {
                return memoryConsolidationSchema().toString();
            }
            if (source.contains(MEMORY_RETENTION_MARKER)) {
                return memoryRetentionSchema().toString();
            }
            if (source.contains(AMBIENT_MARKER)) {
                return ambientSchema().toString();
            }
            return genericObjectSchema().toString();
        } catch (Exception error) {
            throw new IllegalStateException("Failed to build local JSON response schema", error);
        }
    }

    static JSONObject schemaObjectForTest(String prompt, OpenAiClient.FunctionTool tool) {
        try {
            return new JSONObject(schemaFor(prompt, tool));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    static boolean isSpecialist(String prompt) {
        return prompt != null
                && prompt.contains(SPECIALIST_MARKER)
                && prompt.contains(SPECIALIST_ID_SUFFIX);
    }

    static String expectedSpecialistId(String prompt) {
        if (prompt == null) return "";
        int suffix = prompt.indexOf(SPECIALIST_ID_SUFFIX);
        if (suffix < 0) return "";
        int prefix = prompt.lastIndexOf(SPECIALIST_ID_PREFIX, suffix);
        if (prefix < 0) return "";
        int start = prefix + SPECIALIST_ID_PREFIX.length();
        if (start >= suffix) return "";
        String candidate = prompt.substring(start, suffix).trim();
        return candidate.matches("[a-z0-9_]{1,48}") ? candidate : "";
    }

    private static JSONObject specialistSchema(String moduleId) throws Exception {
        JSONObject properties = new JSONObject();
        properties.put("module", moduleId.isEmpty()
                ? stringSchema(48)
                : enumStringSchema(moduleId));
        properties.put("content", stringSchema(180));
        properties.put("confidence", numberSchema(0.0, 1.0));
        properties.put("salient_facts", stringArraySchema(4, 100));
        properties.put("personality_effect", stringSchema(100));
        properties.put("graph_used_node_ids", stringArraySchema(8, 48));
        return objectSchema(properties,
                "module", "content", "confidence", "salient_facts",
                "personality_effect", "graph_used_node_ids");
    }

    private static JSONObject ambientSchema() throws Exception {
        JSONObject properties = new JSONObject();
        properties.put("mood_summary", stringSchema(100));
        properties.put("focus", stringSchema(120));
        properties.put("thought", stringSchema(220));
        properties.put("intention", stringSchema(140));
        properties.put("reflection", stringSchema(240));
        properties.put("importance", numberSchema(0.0, 1.0));
        return objectSchema(properties,
                "mood_summary", "focus", "thought", "intention", "reflection", "importance");
    }

    private static JSONObject globalWorkspaceSchema() throws Exception {
        JSONObject properties = new JSONObject();
        properties.put("module", enumStringSchema("global_workspace"));
        properties.put("npc_utterance", stringSchema(220));
        properties.put("npc_action", stringSchema(180));
        properties.put("internal_state_summary", stringSchema(220));
        properties.put("confidence", numberSchema(0.0, 1.0));
        properties.put("personality_effect", stringSchema(140));
        properties.put("graph_used_node_ids", stringArraySchema(8, 48));

        JSONObject dynamic = new JSONObject();
        dynamic.put("valence", numberSchema(-1.0, 1.0));
        dynamic.put("arousal", numberSchema(0.0, 1.0));
        dynamic.put("stress", numberSchema(0.0, 1.0));
        properties.put("dynamic_state", objectSchema(dynamic, "valence", "arousal", "stress"));

        properties.put("memory_summary", stringSchema(220));
        properties.put("memory_importance", numberSchema(0.0, 1.0));

        JSONObject semanticFact = new JSONObject();
        semanticFact.put("type", enumStringSchema(
                "world_fact", "self_belief", "goal", "value", "fear",
                "relationship", "habit_strategy", "role_identity"));
        semanticFact.put("text", stringSchema(160));
        properties.put("semantic_facts", arraySchema(
                objectSchema(semanticFact, "type", "text"), 6));

        JSONObject communication = new JSONObject();
        communication.put("decision", enumStringSchema("none", "send", "defer", "skip"));
        communication.put("target_id", stringSchema(64));
        communication.put("defer_until_ms", integerSchema(0));
        properties.put("communication", objectSchema(
                communication, "decision", "target_id", "defer_until_ms"));

        JSONObject environmentAction = new JSONObject();
        environmentAction.put("type", enumStringSchema("none", "move", "attack", "wait"));
        environmentAction.put("direction", enumStringSchema("none", "up", "down", "left", "right"));
        environmentAction.put("intent", enumStringSchema(
                "none", "explore", "seek_stairs", "engage", "evade", "hold"));
        environmentAction.put("target_id", stringSchema(64));
        environmentAction.put("confidence", numberSchema(0.0, 1.0));
        properties.put("environment_action", objectSchema(
                environmentAction, "type", "direction", "intent", "target_id", "confidence"));

        JSONObject dungeonPlan = new JSONObject();
        dungeonPlan.put("applicable", booleanSchema());
        dungeonPlan.put("objective_interpretation", stringSchema(180));
        dungeonPlan.put("plan_summary", stringSchema(220));
        dungeonPlan.put("strategy", enumStringSchema("advance", "hunt", "explore", "survive", "balanced"));
        dungeonPlan.put("target_floor", integerSchema(0, 10));
        dungeonPlan.put("risk_tolerance", numberSchema(0.0, 1.0));
        dungeonPlan.put("combat_preference", numberSchema(0.0, 1.0));
        dungeonPlan.put("exploration_preference", numberSchema(0.0, 1.0));
        dungeonPlan.put("progress_preference", numberSchema(0.0, 1.0));
        dungeonPlan.put("persistence", numberSchema(0.0, 1.0));
        dungeonPlan.put("confidence", numberSchema(0.0, 1.0));
        properties.put("dungeon_plan", objectSchema(
                dungeonPlan,
                "applicable", "objective_interpretation", "plan_summary", "strategy",
                "target_floor", "risk_tolerance", "combat_preference",
                "exploration_preference", "progress_preference", "persistence", "confidence"));

        return objectSchema(properties,
                "module", "npc_utterance", "npc_action", "internal_state_summary",
                "confidence", "personality_effect", "graph_used_node_ids", "dynamic_state",
                "memory_summary", "memory_importance", "semantic_facts", "communication",
                "environment_action", "dungeon_plan");
    }

    private static JSONObject memoryAppraisalSchema() throws Exception {
        JSONObject item = new JSONObject();
        item.put("candidate_id", stringSchema(64));
        item.put("importance", numberSchema(0.0, 1.0));
        item.put("emotionality", numberSchema(0.0, 1.0));
        item.put("social_relevance", numberSchema(0.0, 1.0));
        item.put("repetition", numberSchema(0.0, 1.0));
        item.put("gist", stringSchema(80));
        JSONObject properties = new JSONObject();
        properties.put("items", arraySchema(objectSchema(
                item, "candidate_id", "importance", "emotionality",
                "social_relevance", "repetition", "gist"), 4));
        return objectSchema(properties, "items");
    }

    private static JSONObject memoryConsolidationSchema() throws Exception {
        JSONObject episode = new JSONObject();
        episode.put("candidate_id", stringSchema(64));
        episode.put("gist", stringSchema(80));

        JSONObject semantic = new JSONObject();
        semantic.put("type", enumStringSchema(
                "world_fact", "self_belief", "goal", "value", "fear",
                "relationship", "habit_strategy", "role_identity"));
        semantic.put("text", stringSchema(100));
        semantic.put("confidence", numberSchema(0.0, 1.0));

        JSONObject relationship = new JSONObject();
        relationship.put("other_id", stringSchema(64));
        relationship.put("familiarity_delta", numberSchema(-0.15, 0.15));
        relationship.put("trust_delta", numberSchema(-0.15, 0.15));
        relationship.put("affinity_delta", numberSchema(-0.15, 0.15));
        relationship.put("summary", stringSchema(80));

        JSONObject properties = new JSONObject();
        properties.put("episodic", arraySchema(
                objectSchema(episode, "candidate_id", "gist"), 4));
        properties.put("semantic_updates", arraySchema(
                objectSchema(semantic, "type", "text", "confidence"), 3));
        properties.put("relationship_updates", arraySchema(
                objectSchema(relationship, "other_id", "familiarity_delta",
                        "trust_delta", "affinity_delta", "summary"), 3));
        return objectSchema(properties,
                "episodic", "semantic_updates", "relationship_updates");
    }

    private static JSONObject memoryRetentionSchema() throws Exception {
        JSONObject item = new JSONObject();
        item.put("memory_id", stringSchema(64));
        item.put("decision", enumStringSchema("detail", "gist", "forget"));
        item.put("gist", stringSchema(80));
        JSONObject properties = new JSONObject();
        properties.put("retention", arraySchema(
                objectSchema(item, "memory_id", "decision", "gist"), 8));
        return objectSchema(properties, "retention");
    }

    private static JSONObject toolEnvelopeSchema(String toolName) throws Exception {
        JSONObject call = new JSONObject();
        call.put("name", enumStringSchema(toolName == null ? "" : toolName));
        call.put("arguments", genericObjectSchema());
        JSONObject root = new JSONObject();
        root.put("_npcbrain_tool_call", objectSchema(call, "name", "arguments"));
        return objectSchema(root, "_npcbrain_tool_call");
    }

    private static JSONObject genericObjectSchema() throws Exception {
        return new JSONObject()
                .put("type", "object")
                .put("minProperties", 1)
                .put("maxProperties", 32);
    }

    private static JSONObject objectSchema(JSONObject properties, String... required) throws Exception {
        JSONObject result = new JSONObject()
                .put("type", "object")
                .put("properties", properties == null ? new JSONObject() : properties)
                .put("additionalProperties", false);
        JSONArray requiredArray = new JSONArray();
        if (required != null) {
            for (String key : required) {
                if (key != null && !key.isEmpty()) requiredArray.put(key);
            }
        }
        if (requiredArray.length() > 0) result.put("required", requiredArray);
        return result;
    }

    private static JSONObject stringSchema(int maxLength) throws Exception {
        return new JSONObject()
                .put("type", "string")
                .put("maxLength", Math.max(1, maxLength));
    }

    private static JSONObject enumStringSchema(String... values) throws Exception {
        JSONArray enums = new JSONArray();
        if (values != null) {
            for (String value : values) enums.put(value == null ? "" : value);
        }
        return new JSONObject().put("type", "string").put("enum", enums);
    }

    private static JSONObject numberSchema(double minimum, double maximum) throws Exception {
        return new JSONObject()
                .put("type", "number")
                .put("minimum", minimum)
                .put("maximum", maximum);
    }

    private static JSONObject integerSchema(long minimum) throws Exception {
        return new JSONObject().put("type", "integer").put("minimum", minimum);
    }

    private static JSONObject integerSchema(long minimum, long maximum) throws Exception {
        return new JSONObject()
                .put("type", "integer")
                .put("minimum", minimum)
                .put("maximum", maximum);
    }

    private static JSONObject booleanSchema() throws Exception {
        return new JSONObject().put("type", "boolean");
    }

    private static JSONObject stringArraySchema(int maxItems, int maxLength) throws Exception {
        return arraySchema(stringSchema(maxLength), maxItems);
    }

    private static JSONObject arraySchema(JSONObject items, int maxItems) throws Exception {
        return new JSONObject()
                .put("type", "array")
                .put("maxItems", Math.max(0, maxItems))
                .put("items", items);
    }
}
