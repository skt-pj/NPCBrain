package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImportedPersonalityProfileMetadataTest {
    @Test
    public void missingIdentityMetadataUsesDefaults() throws Exception {
        JSONObject root = profileJson();
        ImportedPersonalityProfile profile = ImportedPersonalityProfile.fromJson(root);
        assertEquals("知人", profile.relationshipToUser);
        assertEquals("不明", profile.age);
        assertEquals("特記事項なし", profile.background);
    }

    @Test
    public void presentIdentityMetadataIsPreserved() throws Exception {
        JSONObject root = profileJson()
                .put("relationship_to_user", "大学時代の友人")
                .put("age", "24歳")
                .put("background", "大学で同じ研究室だった");
        ImportedPersonalityProfile profile = ImportedPersonalityProfile.fromJson(root);
        assertEquals("大学時代の友人", profile.relationshipToUser);
        assertEquals("24歳", profile.age);
        assertEquals("大学で同じ研究室だった", profile.background);
    }

    private static JSONObject profileJson() throws Exception {
        return new JSONObject()
                .put("name", "A")
                .put("speech_style", "短文")
                .put("traits", new JSONObject()
                        .put("extraversion", 50)
                        .put("neuroticism", 50)
                        .put("agreeableness", 50)
                        .put("conscientiousness", 50)
                        .put("openness", 50));
    }
}
