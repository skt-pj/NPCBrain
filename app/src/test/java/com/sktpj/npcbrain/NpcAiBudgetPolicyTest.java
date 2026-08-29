package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcAiBudgetPolicyTest {
    @Test
    public void alreadyExhaustedOrOverspentNpcCannotReserveAnything() {
        assertFalse(NpcAiBudgetPolicy.canReserve(10.0, 0.0, 0.0001));
        assertFalse(NpcAiBudgetPolicy.canReserve(1551.66, 0.0, 0.0001));
    }

    @Test
    public void concurrentOutstandingReservationsCountAgainstSameBudget() {
        assertTrue(NpcAiBudgetPolicy.canReserve(8.0, 1.0, 1.0));
        assertFalse(NpcAiBudgetPolicy.canReserve(8.0, 1.0, 1.0001));
    }

    @Test
    public void requestReservationGrowsWithInputAndMaximumOutput() {
        double base = NpcAiBudgetPolicy.reservationJpy(1000, 256);
        double largerInput = NpcAiBudgetPolicy.reservationJpy(5000, 256);
        double largerOutput = NpcAiBudgetPolicy.reservationJpy(1000, 768);
        assertTrue(base > 0.0);
        assertTrue(largerInput > base);
        assertTrue(largerOutput > base);
    }

    @Test
    public void requestBytesHaveAdditionalConservativeInputOverhead() {
        assertEquals(
                1000L + NpcAiBudgetPolicy.REQUEST_INPUT_OVERHEAD_TOKENS,
                NpcAiBudgetPolicy.conservativeInputTokenUpperBound(1000));
    }

    @Test
    public void npcBrainDefaultOutputLimitsAreBounded() {
        assertEquals(
                NpcAiBudgetPolicy.NPC_SPECIALIST_MAX_OUTPUT_TOKENS,
                NpcAiBudgetPolicy.npcDefaultMaxOutputTokens("ordinary specialist prompt"));
        assertEquals(
                NpcAiBudgetPolicy.NPC_GLOBAL_MAX_OUTPUT_TOKENS,
                NpcAiBudgetPolicy.npcDefaultMaxOutputTokens(
                        "You are the existing Global Workspace for this NPC"));
    }
}
