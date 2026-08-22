package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpontaneousMessagePolicyTest {
    @Test
    public void triggerOnlyAcceptsNpcActivityStarted() {
        assertTrue(SpontaneousMessagePolicy.isTriggerEvent("activity_started", "npc1"));
        assertTrue(SpontaneousMessagePolicy.isTriggerEvent("activity_started", "npc2"));
        assertFalse(SpontaneousMessagePolicy.isTriggerEvent("activity_ended", "npc1"));
        assertFalse(SpontaneousMessagePolicy.isTriggerEvent("location_changed", "npc2"));
        assertFalse(SpontaneousMessagePolicy.isTriggerEvent("activity_started", "user"));
    }

    @Test
    public void routesAllowedTargetsAndRejectsSelf() {
        assertArrayEquals(new String[]{"user", "npc2", "group"},
                SpontaneousMessagePolicy.allowedTargets("npc1"));
        assertArrayEquals(new String[]{"user", "npc1", "group"},
                SpontaneousMessagePolicy.allowedTargets("npc2"));
        assertEquals(DemoRuntimeV032.ROOM_NPC1,
                SpontaneousMessagePolicy.routeRoom("npc1", "user"));
        assertEquals(DemoRuntimeV032.ROOM_NPC2,
                SpontaneousMessagePolicy.routeRoom("npc2", "user"));
        assertEquals(DemoRuntimeV032.ROOM_GROUP,
                SpontaneousMessagePolicy.routeRoom("npc1", "npc2"));
        assertEquals(DemoRuntimeV032.ROOM_GROUP,
                SpontaneousMessagePolicy.routeRoom("npc2", "group"));
        assertEquals("", SpontaneousMessagePolicy.routeRoom("npc1", "npc1"));
        assertEquals("", SpontaneousMessagePolicy.routeRoom("npc2", "npc2"));
    }

    @Test
    public void deferAndGroupLimitAreDeterministic() {
        assertFalse(SpontaneousMessagePolicy.isDeferredDue(2001L, 2000L));
        assertTrue(SpontaneousMessagePolicy.isDeferredDue(2000L, 2000L));
        assertFalse(SpontaneousMessagePolicy.isDeferredDue(0L, 2000L));
        assertTrue(SpontaneousMessagePolicy.canContinueGroupChain(0));
        assertTrue(SpontaneousMessagePolicy.canContinueGroupChain(3));
        assertFalse(SpontaneousMessagePolicy.canContinueGroupChain(4));
        assertEquals("npc2", SpontaneousMessagePolicy.otherNpc("npc1"));
        assertEquals("npc1", SpontaneousMessagePolicy.otherNpc("npc2"));
    }

    @Test
    public void generatedMessageIdsAreStable() {
        assertEquals("spontaneous_abc-123",
                SpontaneousMessagePolicy.initialMessageId("ABC-123"));
        assertEquals("spontaneous_abc-123_turn2_npc2",
                SpontaneousMessagePolicy.groupTurnMessageId("ABC-123", 2, "npc2"));
    }
}
