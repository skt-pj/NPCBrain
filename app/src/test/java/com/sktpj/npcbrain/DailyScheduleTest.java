package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

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

    @Test
    public void adventurerDoesNotUseSchoolOrWorkDaytime() {
        DailySchedule schedule = DailySchedule.profileFor(NpcId.NPC1, "24", "冒険者");
        assertEquals("adventure", schedule.slotAtMinute(600).activity());
        assertNotEquals("school", schedule.slotAtMinute(600).activity());
        assertNotEquals("work", schedule.slotAtMinute(600).activity());
    }

    @Test
    public void childWithUnsetOccupationUsesSchoolInsteadOfWork() {
        DailySchedule schedule = DailySchedule.profileFor(NpcId.NPC1, "こども", "未設定");
        assertEquals("school", schedule.slotAtMinute(600).activity());
        assertNotEquals("work", schedule.slotAtMinute(600).activity());
    }

    @Test
    public void unsetAdultProfileDoesNotAssumeSchoolOrWork() {
        DailySchedule schedule = DailySchedule.profileFor(NpcId.NPC1, "25", "未設定");
        assertEquals("planned_activity", schedule.slotAtMinute(600).activity());
        assertNotEquals("school", schedule.slotAtMinute(600).activity());
        assertNotEquals("work", schedule.slotAtMinute(600).activity());
    }

    @Test
    public void explicitOccupationUsesWorkSchedule() {
        DailySchedule schedule = DailySchedule.profileFor(NpcId.NPC1, "25", "鍛冶師");
        assertEquals("work", schedule.slotAtMinute(600).activity());
    }
}
