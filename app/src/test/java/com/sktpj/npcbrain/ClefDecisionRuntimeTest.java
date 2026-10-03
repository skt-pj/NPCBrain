package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ClefDecisionRuntimeTest {
    @Test
    public void localModelSelectionIsFixedToClefFlash() {
        assertEquals(
                ClefSettingsStore.MODEL_CLEF_FLASH,
                ClefSettingsStore.normalizeModel(null));
        assertEquals(
                ClefSettingsStore.MODEL_CLEF_FLASH,
                ClefSettingsStore.normalizeModel("remote-or-unknown"));
        assertTrue(ClefSettingsStore.displayLabel().contains("ローカル"));
    }

    @Test
    public void adaptsNativeJointHeadScoresToExistingActionSelectionContract() throws Exception {
        JSONObject adapted = ClefActionSelectionRuntime.adaptScores(
                new String[]{"attack", "wait"},
                new double[]{2.0, 0.0, 1.0, -1.0});

        assertEquals("action_selection", adapted.optString("module"));
        assertTrue(adapted.optString("content").contains("CLEF local action=attack"));
        assertTrue(adapted.optDouble("confidence") > 0.5);
        assertEquals(0, adapted.optJSONArray("salient_facts").length());
        assertEquals(0, adapted.optJSONArray("graph_used_node_ids").length());
    }
}
