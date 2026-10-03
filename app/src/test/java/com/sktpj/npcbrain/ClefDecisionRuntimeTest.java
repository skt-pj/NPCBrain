package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ClefDecisionRuntimeTest {
    @Test
    public void decisionCatalogSeparatesLocalAndCloudClefModels() {
        assertTrue(DecisionModelCatalog.isLocal(
                DecisionModelCatalog.LOCAL_CLEF_FLASH_Q4_K_M));
        assertTrue(DecisionModelCatalog.isCloud(
                DecisionModelCatalog.CLOUD_CLEF_FLASH));
        assertTrue(DecisionModelCatalog.isCloud(
                DecisionModelCatalog.CLOUD_CLEF));
        assertEquals("clef-flash",
                DecisionModelCatalog.cloudflareModel(
                        DecisionModelCatalog.CLOUD_CLEF_FLASH));
        assertEquals("clef",
                DecisionModelCatalog.cloudflareModel(
                        DecisionModelCatalog.CLOUD_CLEF));
    }

    @Test
    public void adaptsTypedDecisionToExistingSpecialistContract() {
        SpecialistDecisionSchema.Spec spec =
                SpecialistDecisionSchema.forModule("valuation", "{}");
        String choice = spec.optionIds()[0];
        JSONObject adapted = SpecialistDecisionModelRuntime.adapt(
                "valuation",
                "価値判断",
                spec,
                new DecisionModelEvaluation(
                        choice,
                        0.84,
                        0.72,
                        DecisionModelCatalog.LOCAL_CLEF_FLASH_Q4_K_M,
                        "local"));

        assertEquals("valuation", adapted.optString("module"));
        assertTrue(adapted.optString("content").contains("choice=" + choice));
        assertEquals(0.84, adapted.optDouble("confidence"), 0.0001);
        assertEquals(choice,
                adapted.optJSONObject("decision_model").optString("choice"));
        assertEquals(0, adapted.optJSONArray("salient_facts").length());
    }
}
