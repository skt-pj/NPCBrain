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
    public void configurableBudgetChangesReservationBoundaryPerNpc() {
        assertTrue(NpcAiBudgetPolicy.canReserve(18.0, 1.0, 1.0, 20.0));
        assertFalse(NpcAiBudgetPolicy.canReserve(18.0, 1.0, 1.0001, 20.0));
        assertFalse(NpcAiBudgetPolicy.canReserve(9.5, 0.0, 0.6, 10.0));
        assertTrue(NpcAiBudgetPolicy.canReserve(9.5, 0.0, 0.5, 10.0));
    }

    @Test
    public void budgetLimitNormalizationHasSafeBoundsAndLegacyDefault() {
        assertEquals(10.0, NpcAiBudgetPolicy.normalizeBudgetLimitJpy(0.0), 0.0);
        assertEquals(10.0, NpcAiBudgetPolicy.normalizeBudgetLimitJpy(Double.NaN), 0.0);
        assertEquals(0.01, NpcAiBudgetPolicy.normalizeBudgetLimitJpy(0.001), 0.0);
        assertEquals(100000.0, NpcAiBudgetPolicy.normalizeBudgetLimitJpy(200000.0), 0.0);
        assertEquals(42.5, NpcAiBudgetPolicy.normalizeBudgetLimitJpy(42.5), 0.0);
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
