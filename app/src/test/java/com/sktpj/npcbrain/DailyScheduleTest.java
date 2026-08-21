package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class DailyScheduleTest {
    @Test
    public void npc1ScheduleCoversWholeDay() {
        DailySchedule schedule = DailySchedule.defaultFor(NpcId.NPC1);
        for (int minute = 0; minute < 1440; minute++) {
            assertNotNull(schedule.slotAtMinute(minute));
        }
    }

    @Test
    public void npc1TransitionsAcrossLifeActivities() {
        DailySchedule schedule = DailySchedule.defaultFor(NpcId.NPC1);
        assertEquals("sleep", schedule.slotAtMinute(120).activity());
        assertEquals("meal", schedule.slotAtMinute(430).activity());
        assertEquals("move", schedule.slotAtMinute(470).activity());
        assertEquals("work", schedule.slotAtMinute(600).activity());
        assertEquals("meal", schedule.slotAtMinute(750).activity());
        assertEquals("free_time", schedule.slotAtMinute(1200).activity());
    }

    @Test
    public void npc2HasSchoolAndPlannedActivity() {
        DailySchedule schedule = DailySchedule.defaultFor(NpcId.NPC2);
        assertEquals("school", schedule.slotAtMinute(600).activity());
        assertEquals("planned_activity", schedule.slotAtMinute(1260).activity());
        assertEquals("home", schedule.slotAtMinute(1260).location());
    }
}
