package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class LocalMemoryJsonSchemaTest {
    @Test
    public void appraisalUsesBoundedDedicatedSchema() throws Exception {
        String prompt = "character_id=npc10\n"
                + "You are the encoding/appraisal pass of a memory-maintenance system. "
                + "Return JSON only.\nGrounded JSON:\n"
                + "{\"character_id\":\"npc10\",\"now_ms\":1,\"character\":{\"name\":\"NPC10\"},\"memory_candidates\":[]}";
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
        assertEquals("local_request", OpenAiClient.diagnosticBrainStage("character_id=npc1\nother local task"));
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
