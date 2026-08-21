package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class DailySchedulePersistenceTest {
    @Test
    public void scheduleRoundTripPreservesEntries() {
        DailySchedule original = DailySchedule.defaultFor(NpcId.NPC1);
        DailySchedule restored = DailySchedule.fromJson(NpcId.NPC1, original.toJson());

        assertNotNull(restored);
        assertEquals("work", restored.slotAtMinute(600).activity());
        assertEquals("workplace", restored.slotAtMinute(600).location());
        assertEquals("free_time", restored.slotAtMinute(1200).activity());
    }

    @Test
    public void existingSlotCanBeReplacedAndPersists() {
        DailySchedule original = DailySchedule.defaultFor(NpcId.NPC1);
        ScheduleSlot replacement = new ScheduleSlot(
                "work_am",
                510,
                720,
                "personal_errand",
                "city",
                "finish_errand",
                "schedule override"
        );

        DailySchedule updated = original.replaceSlot(replacement);
        DailySchedule restored = DailySchedule.fromJson(NpcId.NPC1, updated.toJson());

        assertNotNull(restored);
        assertEquals("personal_errand", restored.slotAtMinute(600).activity());
        assertEquals("city", restored.slotAtMinute(600).location());
        assertEquals("finish_errand", restored.slotAtMinute(600).goal());
    }

    @Test
    public void unknownEntryIsRejected() {
        DailySchedule original = DailySchedule.defaultFor(NpcId.NPC1);
        ScheduleSlot replacement = new ScheduleSlot(
                "missing_entry",
                510,
                720,
                "work",
                "workplace",
                "work",
                "working"
        );

        assertThrows(IllegalArgumentException.class, () -> original.replaceSlot(replacement));
    }

    @Test
    public void replacementThatCreatesCoverageGapIsRejected() {
        DailySchedule original = DailySchedule.defaultFor(NpcId.NPC1);
        ScheduleSlot replacement = new ScheduleSlot(
                "work_am",
                520,
                720,
                "work",
                "workplace",
                "work",
                "working"
        );

        assertThrows(IllegalArgumentException.class, () -> original.replaceSlot(replacement));
    }

    @Test
    public void incompletePersistedScheduleIsRejected() throws Exception {
        JSONObject invalid = new JSONObject();
        invalid.put("npc_id", NpcId.NPC1.value());
        JSONArray entries = new JSONArray();
        entries.put(new ScheduleSlot(
                "partial",
                0,
                60,
                "sleep",
                "home",
                "rest",
                "partial"
        ).toJson());
        invalid.put("entries", entries);

        assertNull(DailySchedule.fromJson(NpcId.NPC1, invalid));
    }
}
