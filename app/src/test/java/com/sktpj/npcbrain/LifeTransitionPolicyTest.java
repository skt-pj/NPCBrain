package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LifeTransitionPolicyTest {
    @Test
    public void normalTransitionEndsActivity() {
        assertEquals("activity_ended", LifeTransitionPolicy.endEventType(true, false));
    }

    @Test
    public void scheduleChangeInterruptsActivity() {
        assertEquals("activity_interrupted", LifeTransitionPolicy.endEventType(true, true));
    }

    @Test
    public void normalTransitionUsesScheduledBoundaryTime() {
        long nineAm = 9L * 60L * 60L * 1000L;
        long nineOhSeven = nineAm + 7L * 60L * 1000L;
        assertEquals(
                nineAm,
                LifeTransitionPolicy.transitionTime(false, nineOhSeven, nineAm)
        );
    }

    @Test
    public void interruptionUsesEffectiveObservedTime() {
        long nineAm = 9L * 60L * 60L * 1000L;
        long nineOhSeven = nineAm + 7L * 60L * 1000L;
        assertEquals(
                nineOhSeven,
                LifeTransitionPolicy.transitionTime(true, nineOhSeven, nineAm)
        );
    }

    @Test
    public void sameStateDoesNotRequireTransition() {
        ScheduleSlot slot = new ScheduleSlot(
                "work_am", 510, 720, "work", "workplace", "work", "working"
        );
        assertTrue(LifeTransitionPolicy.sameState("work_am", "work", "workplace", slot));
        assertFalse(LifeTransitionPolicy.sameState("work_am", "meal", "workplace", slot));
    }

    @Test
    public void userTriggerRemainsPrimaryConversationCause() {
        assertEquals(
                "message-event",
                LifeTransitionPolicy.primaryConversationCause("message-event", "activity-event")
        );
    }

    @Test
    public void activityEventIsFallbackOnlyWithoutTrigger() {
        assertEquals(
                "activity-event",
                LifeTransitionPolicy.primaryConversationCause("", "activity-event")
        );
    }
}
