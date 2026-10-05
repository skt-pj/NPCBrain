package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        assertEquals("", ClefSettingsStore.normalizeExecutionBackend(null));
        assertEquals("", ClefSettingsStore.normalizeExecutionBackend("unknown"));
        assertEquals(ClefSettingsStore.EXECUTION_GPU,
                ClefSettingsStore.normalizeExecutionBackend(ClefSettingsStore.EXECUTION_GPU));
        assertEquals(ClefSettingsStore.EXECUTION_CPU,
                ClefSettingsStore.normalizeExecutionBackend(ClefSettingsStore.EXECUTION_CPU));
        assertEquals("未選択", ClefSettingsStore.executionBackendLabel(""));
    }

    @Test
    public void everySpecialistHasThreeFixedReactionFields() {
        for (String moduleId : BrainEngine.specialistIds()) {
            List<ClefSpecialistSchema.Field> fields =
                    ClefSpecialistSchema.forModule(moduleId);
            assertEquals(moduleId, 3, fields.size());
            for (ClefSpecialistSchema.Field field : fields) {
                assertNotNull(field.id);
                assertTrue(field.optionIds.length >= 2);
                assertEquals(field.optionIds.length, field.optionDescriptions.length);
            }
        }
    }

    @Test
    public void adaptsFixedReactionScoresWithoutGeneratingAction() throws Exception {
        List<ClefSpecialistSchema.Field> fields =
                ClefSpecialistSchema.forModule("valuation");
        double[] scores = new double[]{
                -2.0, -1.0, 0.0, 3.0, 1.0,
                -2.0, -1.0, 0.0, 2.0, 1.0,
                -2.0, -1.0, 0.0, 1.0, 3.0
        };

        JSONObject adapted =
                ClefSpecialistRuntime.adaptScores("valuation", fields, scores);

        assertEquals("valuation", adapted.optString("module"));
        assertEquals("decision_model", adapted.optString("engine"));
        JSONObject signals = adapted.optJSONObject("signals");
        assertNotNull(signals);
        assertEquals("positive",
                signals.optJSONObject("valence").optString("value"));
        assertEquals("high",
                signals.optJSONObject("attraction").optString("value"));
        assertEquals("very_high",
                signals.optJSONObject("danger").optString("value"));
        assertTrue(adapted.optDouble("confidence") > 0.5);
        assertEquals(0, adapted.optJSONArray("salient_facts").length());
        assertEquals(0, adapted.optJSONArray("graph_used_node_ids").length());
    }
}
