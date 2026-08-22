package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

public final class ImportedPersonalityProfileTest {
    @Test
    public void parsesAndClampsAllRequiredTraits() throws Exception {
        JSONObject traits = new JSONObject()
                .put("extraversion", 110)
                .put("neuroticism", -5)
                .put("agreeableness", 72.4)
                .put("conscientiousness", 63)
                .put("openness", 81);
        JSONObject root = new JSONObject()
                .put("name", "島田 恵未")
                .put("speech_style", "短文でくだけた口調")
                .put("traits", traits);

        ImportedPersonalityProfile profile = ImportedPersonalityProfile.fromJson(root);

        assertEquals("島田 恵未", profile.name);
        assertEquals("短文でくだけた口調", profile.speechStyle);
        assertEquals(100, profile.extraversion);
        assertEquals(0, profile.neuroticism);
        assertEquals(72, profile.agreeableness);
        assertEquals(63, profile.conscientiousness);
        assertEquals(81, profile.openness);
    }

    @Test
    public void rejectsMissingTrait() throws Exception {
        JSONObject root = new JSONObject()
                .put("name", "Target")
                .put("speech_style", "casual")
                .put("traits", new JSONObject()
                        .put("extraversion", 50)
                        .put("neuroticism", 50)
                        .put("agreeableness", 50)
                        .put("conscientiousness", 50));

        try {
            ImportedPersonalityProfile.fromJson(root);
            fail("Expected missing openness to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "Personality JSON trait is missing or invalid: openness",
                    expected.getMessage()
            );
        }
    }
}
