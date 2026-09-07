package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NeuroanatomyModelTest {
    private static final String[] OWNERS = {
            "perception",
            "salience",
            "episodic_memory",
            "semantic_memory",
            "world_model",
            "executive_control",
            "valuation",
            "error_monitor",
            "action_selection",
            "global_workspace"
    };

    @Test
    public void allExistingCognitionOwnersHaveRegions() {
        assertEquals(10, NeuroanatomyModel.ownerCount());
        for (String owner : OWNERS) {
            assertTrue(owner + " must have mapped regions",
                    NeuroanatomyModel.regionsFor(owner).length() > 0);
        }
        assertEquals(0, NeuroanatomyModel.regionsFor("unknown").length());
    }

    @Test
    public void mappingIsManyToManyRatherThanOneRegionOneModule() {
        JSONArray perception = NeuroanatomyModel.regionsFor("perception");
        JSONArray salience = NeuroanatomyModel.regionsFor("salience");
        JSONArray workspace = NeuroanatomyModel.regionsFor("global_workspace");
        assertTrue(contains(perception, "thalamus"));
        assertTrue(contains(salience, "thalamus"));
        assertTrue(contains(workspace, "thalamus"));
        assertTrue(NeuroanatomyModel.regionsFor("valuation").length() > 1);
    }

    @Test
    public void fullContextCarriesGroundedPrimaryDriveSignalsAndAuthorityPolicy() throws Exception {
        JSONObject character = new JSONObject()
                .put("inner_life", new JSONObject()
                        .put("energy", 0.62)
                        .put("hunger", 0.44)
                        .put("sleep_pressure", 0.71)
                        .put("reproductive_drive", 0.31)
                        .put("safety_concern", 0.26));
        JSONObject context = NeuroanatomyModel.fullContext(character);
        assertEquals("many_to_many_functional_approximation", context.optString("model"));
        JSONObject regions = context.optJSONObject("module_regions");
        assertTrue(regions != null);
        for (String owner : OWNERS) {
            JSONArray mapped = regions.optJSONArray(owner);
            assertTrue(owner + " missing from full context", mapped != null && mapped.length() > 0);
        }
        JSONObject signals = context.optJSONObject("interoceptive_signals");
        assertTrue(signals != null);
        assertEquals(0.44, signals.optDouble("hunger"), 0.0001);
        assertEquals(0.71, signals.optDouble("sleep_pressure"), 0.0001);
        assertEquals(0.31, signals.optDouble("reproductive_drive"), 0.0001);
        String policy = context.optString("policy").toLowerCase(java.util.Locale.ROOT);
        assertTrue(policy.contains("many-to-many"));
        assertTrue(policy.contains("global workspace"));
        assertTrue(policy.contains("consent"));
        assertFalse(policy.contains("reproductive_drive decides"));
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return true;
        }
        return false;
    }
}
