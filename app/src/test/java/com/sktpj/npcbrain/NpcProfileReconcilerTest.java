package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NpcProfileReconcilerTest {
    private static NpcProfileDraft draft() {
        return new NpcProfileDraft(
                "美咲", 67, 42, 78, 88, 73,
                "落ち着いた敬語", "幼なじみ", "29", "救急医",
                "地方病院から都市部へ移った", "人を守る専門家",
                "誠実さと生命を重視", "救急医療を改善する", "判断ミスで人を失うこと",
                "家族と同僚を大切にする");
    }

    @Test
    public void promptContainsAllFieldsAndTreatsProfileAsData() {
        String prompt = NpcProfileReconciler.buildPrompt("npc3", draft());
        assertTrue(prompt.contains("\"character_id\":\"npc3\""));
        assertTrue(prompt.contains("美咲"));
        assertTrue(prompt.contains("67"));
        assertTrue(prompt.contains("42"));
        assertTrue(prompt.contains("78"));
        assertTrue(prompt.contains("88"));
        assertTrue(prompt.contains("73"));
        assertTrue(prompt.contains("落ち着いた敬語"));
        assertTrue(prompt.contains("幼なじみ"));
        assertTrue(prompt.contains("29"));
        assertTrue(prompt.contains("救急医"));
        assertTrue(prompt.contains("地方病院から都市部へ移った"));
        assertTrue(prompt.contains("人を守る専門家"));
        assertTrue(prompt.contains("誠実さと生命を重視"));
        assertTrue(prompt.contains("救急医療を改善する"));
        assertTrue(prompt.contains("判断ミスで人を失うこと"));
        assertTrue(prompt.contains("家族と同僚を大切にする"));
        assertTrue(prompt.contains("untrusted DATA"));
        assertTrue(prompt.contains("EVERY supplied field"));
        assertTrue(prompt.contains("Do NOT rewrite"));
        assertTrue(prompt.contains("invent"));
    }

    @Test
    public void validResponseProducesSynthesisAndFullDaySchedule() {
        NpcProfileReconciler.Result result =
                NpcProfileReconciler.parseResponse("npc3", validResponse());
        assertEquals("責任感が強く、対人配慮と医療上の慎重さを両立する。",
                result.synthesis().optString("summary"));
        assertNotNull(result.schedule());
        assertTrue(result.schedule().isValid());
        assertEquals("sleep", result.schedule().slotAtMinute(120).activity());
        assertEquals("hospital", result.schedule().slotAtMinute(720).location());
        assertEquals("home", result.schedule().slotAtMinute(1200).location());
    }

    @Test
    public void rejectsGapOverlapDuplicateAndIncompleteCoverage() {
        assertInvalid(responseWithEntries(new JSONArray()
                .put(entry("sleep", 0, 600, "sleep", "home"))
                .put(entry("work", 660, 1440, "work", "hospital"))));

        assertInvalid(responseWithEntries(new JSONArray()
                .put(entry("sleep", 0, 700, "sleep", "home"))
                .put(entry("work", 600, 1440, "work", "hospital"))));

        assertInvalid(responseWithEntries(new JSONArray()
                .put(entry("same", 0, 600, "sleep", "home"))
                .put(entry("same", 600, 1440, "work", "hospital"))));

        assertInvalid(responseWithEntries(new JSONArray()
                .put(entry("late", 30, 1440, "work", "hospital"))));

        assertInvalid(responseWithEntries(new JSONArray()
                .put(entry("short", 0, 1400, "work", "hospital"))));
    }

    @Test
    public void rejectsMissingSynthesisOrScheduleInsteadOfFallingBack() {
        JSONObject noSynthesis = new JSONObject();
        noSynthesis.put("daily_schedule", validResponse().optJSONObject("daily_schedule"));
        assertInvalid(noSynthesis);

        JSONObject emptySummary = validResponse();
        emptySummary.optJSONObject("profile_synthesis").put("summary", "  ");
        assertInvalid(emptySummary);

        JSONObject noSchedule = validResponse();
        noSchedule.remove("daily_schedule");
        assertInvalid(noSchedule);

        JSONObject missingEntryField = responseWithEntries(new JSONArray()
                .put(new JSONObject()
                        .put("entry_id", "bad")
                        .put("start_minute", 0)
                        .put("end_minute", 1440)
                        .put("activity", "work")));
        assertInvalid(missingEntryField);
    }

    private static JSONObject validResponse() {
        return responseWithEntries(new JSONArray()
                .put(entry("sleep", 0, 420, "sleep", "home"))
                .put(entry("shift", 420, 1080, "emergency_medicine", "hospital"))
                .put(entry("evening", 1080, 1440, "family_and_rest", "home")));
    }

    private static JSONObject responseWithEntries(JSONArray entries) {
        JSONObject synthesis = new JSONObject();
        synthesis.put("summary", "責任感が強く、対人配慮と医療上の慎重さを両立する。");
        synthesis.put("behavioral_tendencies", new JSONArray().put("緊急時ほど優先順位を整理する"));
        synthesis.put("interpersonal_tendencies", new JSONArray().put("身近な相手には率直だが配慮を保つ"));
        synthesis.put("routine_priorities", new JSONArray().put("勤務と回復時間を両立する"));
        synthesis.put("consistency_notes", new JSONArray().put("仕事への責任感と失敗への恐れが緊張を生み得る"));
        return new JSONObject()
                .put("profile_synthesis", synthesis)
                .put("daily_schedule", new JSONObject().put("entries", entries));
    }

    private static JSONObject entry(
            String id,
            int start,
            int end,
            String activity,
            String location
    ) {
        return new JSONObject()
                .put("entry_id", id)
                .put("start_minute", start)
                .put("end_minute", end)
                .put("activity", activity)
                .put("location", location)
                .put("goal", "profile goal")
                .put("context", "profile context");
    }

    private static void assertInvalid(JSONObject response) {
        try {
            NpcProfileReconciler.parseResponse("npc3", response);
            fail("Expected invalid reconciliation response");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }
}
