package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.json.JSONObject;
import org.junit.Test;

public class LifeStateCompatibilityTest {
    @Test
    public void legacyLifeStateWithoutScheduleFieldsStillLoads() throws Exception {
        JSONObject legacy = new JSONObject();
        legacy.put("npc_id", "npc1");
        legacy.put("world_time", 1000L);
        legacy.put("location", "home");
        legacy.put("current_activity", "free_time");
        legacy.put("activity_started_at", 900L);
        legacy.put("current_goal", "rest");
        legacy.put("active_context", "legacy");

        LifeState state = LifeState.fromJson(legacy, NpcId.NPC1, 1000L);

        assertEquals("npc1", state.npcId().value());
        assertEquals("home", state.location());
        assertEquals("free_time", state.currentActivity());
        assertEquals("", state.currentScheduleEntryId());
        assertEquals("", state.currentActivityEventId());
        assertNotNull(state.dailySchedule());
        assertEquals(0, state.dailySchedule().length());
    }
}
