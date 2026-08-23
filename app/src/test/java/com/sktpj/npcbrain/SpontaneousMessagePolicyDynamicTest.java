package com.sktpj.npcbrain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class SpontaneousMessagePolicyDynamicTest {
    @Test
    public void npc3GetsUserOtherNpcsAndGroupTargets() {
        assertArrayEquals(
                new String[]{"user", "npc1", "npc2", "npc4", "group"},
                SpontaneousMessagePolicy.allowedTargets(
                        "npc3", Arrays.asList("npc1", "npc2", "npc3", "npc4")));
    }

    @Test
    public void actorMustBeActive() {
        assertArrayEquals(
                new String[0],
                SpontaneousMessagePolicy.allowedTargets(
                        "npc3", Arrays.asList("npc1", "npc2")));
    }

    @Test
    public void directUserRouteIsDynamic() {
        assertEquals(
                "direct_npc4",
                SpontaneousMessagePolicy.routeRoom(
                        "npc4", "user", Arrays.asList("npc1", "npc2", "npc3", "npc4")));
    }

    @Test
    public void anyOtherActiveNpcRoutesToSharedGroupRoom() {
        assertEquals(
                DemoRuntimeV032.ROOM_GROUP,
                SpontaneousMessagePolicy.routeRoom(
                        "npc4", "npc2", Arrays.asList("npc1", "npc2", "npc3", "npc4")));
        assertTrue(SpontaneousMessagePolicy.isAllowedTarget(
                "npc4", "npc3", Arrays.asList("npc1", "npc2", "npc3", "npc4")));
        assertFalse(SpontaneousMessagePolicy.isAllowedTarget(
                "npc4", "npc9", Arrays.asList("npc1", "npc2", "npc3", "npc4")));
    }

    @Test
    public void groupChainCyclesAcrossAllActiveNpcs() {
        java.util.List<String> active = Arrays.asList("npc1", "npc2", "npc3", "npc4");
        assertEquals("npc2", SpontaneousMessagePolicy.nextNpc("npc1", active));
        assertEquals("npc3", SpontaneousMessagePolicy.nextNpc("npc2", active));
        assertEquals("npc4", SpontaneousMessagePolicy.nextNpc("npc3", active));
        assertEquals("npc1", SpontaneousMessagePolicy.nextNpc("npc4", active));
    }

    @Test
    public void genericNpcIdCanTriggerLifeEvent() {
        assertTrue(SpontaneousMessagePolicy.isTriggerEvent("activity_started", "npc17"));
        assertFalse(SpontaneousMessagePolicy.isTriggerEvent("activity_ended", "npc17"));
    }
}
