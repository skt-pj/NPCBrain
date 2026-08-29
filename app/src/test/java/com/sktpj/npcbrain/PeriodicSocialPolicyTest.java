package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeriodicSocialPolicyTest {
    @Test
    public void requiresAtLeastTwoActiveNpcs() {
        assertEquals("", PeriodicSocialPolicy.initiator(Arrays.asList("npc1"), 0L));
    }

    @Test
    public void initiatorAndMessageIdAreDeterministicWithinWindow() {
        List<String> ids = Arrays.asList("npc1", "npc2", "npc3");
        long base = HumanMemoryPolicy.MAINTENANCE_INTERVAL_MS * 5L + 1000L;
        assertEquals(
                PeriodicSocialPolicy.initiator(ids, base),
                PeriodicSocialPolicy.initiator(ids, base + 10_000L));
        assertEquals(
                PeriodicSocialPolicy.messageId(base, "npc2"),
                PeriodicSocialPolicy.messageId(base + 10_000L, "npc2"));
    }

    @Test
    public void userAndSelfAreNeverAllowedTargets() {
        List<String> ids = Arrays.asList("npc1", "npc2", "npc3");
        assertFalse(PeriodicSocialPolicy.isAllowedTarget("npc1", "user", ids));
        assertFalse(PeriodicSocialPolicy.isAllowedTarget("npc1", "npc1", ids));
        assertTrue(PeriodicSocialPolicy.isAllowedTarget("npc1", "npc2", ids));
        assertTrue(PeriodicSocialPolicy.isAllowedTarget("npc1", "group", ids));
    }

    @Test
    public void groupReplySelectsAnotherNpc() {
        List<String> ids = Arrays.asList("npc1", "npc2", "npc3");
        assertEquals("npc2", PeriodicSocialPolicy.firstResponder("npc1", "group", ids));
        assertEquals("npc3", PeriodicSocialPolicy.firstResponder("npc1", "npc3", ids));
    }
}
