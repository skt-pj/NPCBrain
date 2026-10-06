package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class JevDecisionRuntimeTest {
    @Test
    public void decisionRouteDefaultsToLocalClefAndNormalizesKnownValues() {
        assertEquals(DecisionModelSettingsStore.ROUTE_LOCAL_CLEF,
                DecisionModelSettingsStore.normalizeRoute(null));
        assertEquals(DecisionModelSettingsStore.ROUTE_LOCAL_CLEF,
                DecisionModelSettingsStore.normalizeRoute("unknown"));
        assertEquals(DecisionModelSettingsStore.ROUTE_CLOUD_JEV,
                DecisionModelSettingsStore.normalizeRoute(
                        DecisionModelSettingsStore.ROUTE_CLOUD_JEV));
    }

    @Test
    public void jevRequestUsesChoiceQuestionsForExistingFixedReactionSchema() throws Exception {
        List<ClefSpecialistSchema.Field> fields =
                ClefSpecialistSchema.forModule("valuation");
        JSONObject request = JevClient.buildRequest("{\"state\":true}", fields);

        assertEquals("jev-latest", request.optString("model"));
        assertNotNull(request.opt("state"));
        JSONObject questions = request.optJSONObject("questions");
        assertNotNull(questions);
        assertEquals(3, questions.length());

        JSONObject valence = questions.optJSONObject("valence");
        assertEquals("choice", valence.optString("type"));
        assertTrue(valence.optString("instructions").contains("positive or negative"));
        JSONObject criteria = valence.optJSONObject("criteria");
        assertEquals(5, criteria.length());
        assertTrue(criteria.has("very_negative"));
        assertTrue(criteria.has("very_positive"));
    }

    @Test
    public void jevResponseAdaptsToCanonicalDecisionSignals() throws Exception {
        List<ClefSpecialistSchema.Field> fields =
                ClefSpecialistSchema.forModule("valuation");
        JSONObject response = new JSONObject()
                .put("model", "jev-1.13.0")
                .put("answers", new JSONObject()
                        .put("valence", choice("positive", 0.86))
                        .put("attraction", choice("high", 0.79))
                        .put("danger", choice("low", 0.74)))
                .put("usage", new JSONObject()
                        .put("input_tokens", 321)
                        .put("output_tokens", 12));

        JSONObject result = JevClient.adaptResponse("valuation", fields, response);
        assertEquals("decision_model", result.optString("engine"));
        assertEquals("jev-1.13.0", result.optString("model"));
        assertEquals("positive",
                result.optJSONObject("signals").optJSONObject("valence").optString("value"));
        assertEquals("high",
                result.optJSONObject("signals").optJSONObject("attraction").optString("value"));
        assertEquals("low",
                result.optJSONObject("signals").optJSONObject("danger").optString("value"));
        assertTrue(result.optDouble("confidence") > 0.7);
    }

    private static JSONObject choice(String value, double confidence) throws Exception {
        return new JSONObject()
                .put("type", "choice")
                .put("choice", value)
                .put("confidence", confidence)
                .put("probabilities", new JSONObject().put(value, confidence));
    }
}
