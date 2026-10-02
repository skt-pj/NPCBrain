package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ClefDecisionRuntimeTest {
    @Test
    public void defaultsToFlashAndBuildsTypedDecisionQuestions() {
        assertEquals(ClefSettingsStore.MODEL_CLEF_FLASH, ClefSettingsStore.normalizeModel(null));
        assertEquals(ClefSettingsStore.MODEL_CLEF_FLASH, ClefSettingsStore.normalizeModel("unknown"));
        assertEquals(ClefSettingsStore.MODEL_CLEF, ClefSettingsStore.normalizeModel("clef"));

        JSONObject body = ClefDecisionClient.buildRequestBody(
                ClefSettingsStore.MODEL_CLEF_FLASH,
                "Runtime JSON: {\"mode\":\"dungeon_turn\"}");
        assertEquals("clef-flash", body.optString("model"));
        JSONObject questions = body.optJSONObject("questions");
        assertEquals("choice", questions.optJSONObject("action").optString("type"));
        assertEquals("noul", questions.optJSONObject("commit_now").optString("type"));
        JSONObject criteria = questions.optJSONObject("action").optJSONObject("criteria");
        assertTrue(criteria.has("attack"));
        assertTrue(criteria.has("retreat"));
    }

    @Test
    public void adaptsChoiceProbabilityToExistingActionSelectionContract() {
        JSONObject result = new JSONObject()
                .put("answers", new JSONObject()
                        .put("action", new JSONObject()
                                .put("choice", "speak_now")
                                .put("confidence", 0.82)
                                .put("probabilities", new JSONObject()
                                        .put("speak_now", 0.82)
                                        .put("remain_silent", 0.18)))
                        .put("commit_now", new JSONObject().put("noul", 0.73)));

        JSONObject adapted = ClefActionSelectionRuntime.adaptResult(result);
        assertEquals("action_selection", adapted.optString("module"));
        assertEquals(0.82, adapted.optDouble("confidence"), 0.0001);
        assertTrue(adapted.optString("content").contains("speak_now"));
        assertEquals(0, adapted.optJSONArray("salient_facts").length());
        assertEquals(0, adapted.optJSONArray("graph_used_node_ids").length());
    }
}
