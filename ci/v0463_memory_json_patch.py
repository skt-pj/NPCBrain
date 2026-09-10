from pathlib import Path


def replace_exact(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing patch anchor in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


schema = "app/src/main/java/com/sktpj/npcbrain/LocalJsonSchema.java"
replace_exact(
    schema,
    '    private static final String AMBIENT_MARKER = "one brief PUBLIC inner-life monitor update";\n',
    '    private static final String AMBIENT_MARKER = "one brief PUBLIC inner-life monitor update";\n'
    '    private static final String MEMORY_APPRAISAL_MARKER =\n'
    '            "You are the encoding/appraisal pass of a memory-maintenance system.";\n'
    '    private static final String MEMORY_CONSOLIDATION_MARKER =\n'
    '            "You are the consolidation/schema pass.";\n'
    '    private static final String MEMORY_RETENTION_MARKER =\n'
    '            "You are the retention/forgetting pass.";\n',
)
replace_exact(
    schema,
    '''            if (source.contains(AMBIENT_MARKER)) {
                return ambientSchema().toString();
            }
            return genericObjectSchema().toString();
''',
    '''            if (source.contains(MEMORY_APPRAISAL_MARKER)) {
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
''',
)
anchor = '    private static JSONObject toolEnvelopeSchema(String toolName) throws Exception {\n'
methods = '''    private static JSONObject memoryAppraisalSchema() throws Exception {
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

'''
replace_exact(schema, anchor, methods + anchor)

client = "app/src/main/java/com/sktpj/npcbrain/OpenAiClient.java"
replace_exact(
    client,
    '''        return "specialist";
    }

    private void completeDiagnosticLlmRequest(String queueId) {
''',
    '''        if (source.contains("encoding/appraisal pass of a memory-maintenance system")) {
            return "memory_appraisal";
        }
        if (source.contains("consolidation/schema pass")) {
            return "memory_consolidation";
        }
        if (source.contains("retention/forgetting pass")) {
            return "memory_retention";
        }
        return "local_request";
    }

    private void completeDiagnosticLlmRequest(String queueId) {
''',
)

vm = "app/src/main/java/com/sktpj/npcbrain/NpcBrainQueueViewModel.java"
replace_exact(
    vm,
    '''            String stageId = stageId(entry.detail);
            if ("global_workspace".equals(stageId)) return "Global Workspace";
            if (!stageId.isEmpty() && !"specialist".equals(stageId)) {
''',
    '''            String stageId = stageId(entry.detail);
            if ("global_workspace".equals(stageId)) return "Global Workspace";
            if ("memory_appraisal".equals(stageId)) return "記憶メンテナンス · 評価";
            if ("memory_consolidation".equals(stageId)) return "記憶メンテナンス · 統合";
            if ("memory_retention".equals(stageId)) return "記憶メンテナンス · 保持/忘却";
            if ("local_request".equals(stageId)) return "LLM処理";
            if (!stageId.isEmpty() && !"specialist".equals(stageId)) {
''',
)

version = Path("version.properties")
text = version.read_text()
if "VERSION_NAME=0.4.62" not in text or "VERSION_CODE=82" not in text:
    raise SystemExit("unexpected version.properties")
version.write_text(text.replace("VERSION_NAME=0.4.62", "VERSION_NAME=0.4.63")
                   .replace("VERSION_CODE=82", "VERSION_CODE=83"))

test = Path("app/src/test/java/com/sktpj/npcbrain/LocalMemoryJsonSchemaTest.java")
test.write_text('''package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class LocalMemoryJsonSchemaTest {
    @Test
    public void appraisalUsesBoundedDedicatedSchema() throws Exception {
        String prompt = "character_id=npc10\\n"
                + "You are the encoding/appraisal pass of a memory-maintenance system. "
                + "Return JSON only.\\nGrounded JSON:\\n"
                + "{\\\"character_id\\\":\\\"npc10\\\",\\\"now_ms\\\":1,\\\"character\\\":{\\\"name\\\":\\\"NPC10\\\"},\\\"memory_candidates\\\":[]}";
        JSONObject schema = LocalJsonSchema.schemaObjectForTest(prompt, null);
        assertFalse(schema.getBoolean("additionalProperties"));
        JSONObject properties = schema.getJSONObject("properties");
        assertTrue(properties.has("items"));
        assertFalse(properties.has("character_id"));
        assertEquals(4, properties.getJSONObject("items").getInt("maxItems"));
        JSONObject item = properties.getJSONObject("items").getJSONObject("items");
        assertFalse(item.getBoolean("additionalProperties"));
        assertTrue(item.getJSONObject("properties").has("candidate_id"));
    }

    @Test
    public void allMemoryMaintenancePassesAreClassifiedAndConstrained() throws Exception {
        String appraisal = "You are the encoding/appraisal pass of a memory-maintenance system.";
        String consolidation = "You are the consolidation/schema pass.";
        String retention = "You are the retention/forgetting pass.";
        assertEquals("memory_appraisal", OpenAiClient.diagnosticBrainStage(appraisal));
        assertEquals("memory_consolidation", OpenAiClient.diagnosticBrainStage(consolidation));
        assertEquals("memory_retention", OpenAiClient.diagnosticBrainStage(retention));
        assertEquals("local_request", OpenAiClient.diagnosticBrainStage("character_id=npc1\\nother local task"));
        assertTrue(LocalJsonSchema.schemaObjectForTest(consolidation, null)
                .getJSONObject("properties").has("semantic_updates"));
        assertTrue(LocalJsonSchema.schemaObjectForTest(retention, null)
                .getJSONObject("properties").has("retention"));
    }

    @Test
    public void debugLabelDoesNotMisreportMemoryMaintenanceAsSpecialistBrain() {
        ProcessingQueueRegistry.Entry entry = new ProcessingQueueRegistry.Entry(
                "q1", "llm_request", "npc10",
                "local_light · brain_stage=memory_appraisal",
                ProcessingQueueRegistry.Status.FAILED,
                1L, 2L, 3L, "bad json");
        assertEquals("記憶メンテナンス · 評価", NpcBrainQueueViewModel.internalLabel(entry));
    }
}
''')
