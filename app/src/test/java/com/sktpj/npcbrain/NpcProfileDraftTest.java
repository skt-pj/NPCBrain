package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class NpcProfileDraftTest {
    @Test
    public void toJsonContainsEveryExplicitProfileFieldAndClampsTraits() {
        NpcProfileDraft draft = new NpcProfileDraft(
                "美咲", 130, -4, 61, 72, 83,
                "落ち着いた敬語", "幼なじみ", "29", "救急医",
                "地方病院から都市部へ移った", "人を守る専門家",
                "誠実さと生命を重視", "救急医療を改善する", "判断ミスで人を失うこと",
                "家族と同僚を大切にする");

        JSONObject json = draft.toJson("npc3");
        assertEquals("npc3", json.optString("character_id"));
        assertEquals("美咲", json.optString("name"));
        assertEquals("落ち着いた敬語", json.optString("speech_style"));
        assertEquals("幼なじみ", json.optString("relationship_to_user"));
        assertEquals("29", json.optString("age"));
        assertEquals("救急医", json.optString("occupation"));
        assertEquals("地方病院から都市部へ移った", json.optString("background"));
        assertEquals("人を守る専門家", json.optString("role_identity"));
        assertEquals("誠実さと生命を重視", json.optString("values"));
        assertEquals("救急医療を改善する", json.optString("goals"));
        assertEquals("判断ミスで人を失うこと", json.optString("fears"));
        assertEquals("家族と同僚を大切にする", json.optString("relationships"));

        JSONObject traits = json.optJSONObject("traits_percent");
        assertEquals(100, traits.optInt("extraversion"));
        assertEquals(0, traits.optInt("neuroticism"));
        assertEquals(61, traits.optInt("agreeableness"));
        assertEquals(72, traits.optInt("conscientiousness"));
        assertEquals(83, traits.optInt("openness"));
    }
}
