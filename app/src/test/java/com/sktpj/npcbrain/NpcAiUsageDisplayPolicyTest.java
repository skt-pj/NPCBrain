package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcAiUsageDisplayPolicyTest {
    @Test
    public void positiveSubCentSpendIsVisiblyNonZero() {
        assertEquals("¥0.00", NpcAiUsageDisplayPolicy.formatSpentJpy(0.0));
        String tiny = NpcAiUsageDisplayPolicy.formatSpentJpy(0.0001234);
        assertTrue(tiny.startsWith("¥0."));
        assertFalse("¥0.00".equals(tiny));
        assertFalse("¥0.000000".equals(tiny));
        assertEquals("¥0.1234", NpcAiUsageDisplayPolicy.formatSpentJpy(0.1234));
        assertEquals("¥1.23", NpcAiUsageDisplayPolicy.formatSpentJpy(1.234));
    }

    @Test
    public void aggregateUsesCanonicalSnapshotArithmetic() {
        NpcAiStaminaStore.Snapshot first = new NpcAiStaminaStore.Snapshot(
                0.25, 100, 20, 30, 130);
        NpcAiStaminaStore.Snapshot second = new NpcAiStaminaStore.Snapshot(
                1.50, 200, 40, 60, 260);

        NpcAiUsageDisplayPolicy.Aggregate total =
                NpcAiUsageDisplayPolicy.aggregate(Arrays.asList(first, second));

        assertEquals(2, total.npcCount);
        assertEquals(1.75, total.spentJpy, 0.0000001);
        assertEquals(18.25, total.remainingJpy, 0.0000001);
        assertEquals(20.0, total.budgetJpy, 0.0000001);
        assertEquals(300, total.inputTokens);
        assertEquals(60, total.cachedInputTokens);
        assertEquals(90, total.outputTokens);
        assertEquals(390, total.totalTokens);
    }

    @Test
    public void aggregateSumsDifferentPerNpcBudgetLimits() {
        NpcAiStaminaStore.Snapshot first = new NpcAiStaminaStore.Snapshot(
                1.0, 5.0,
                10, 2, 3, 13,
                8.0, 80, 20, 30, 110);
        NpcAiStaminaStore.Snapshot second = new NpcAiStaminaStore.Snapshot(
                2.0, 25.0,
                20, 4, 6, 26,
                15.0, 200, 40, 60, 260);

        NpcAiUsageDisplayPolicy.Aggregate total =
                NpcAiUsageDisplayPolicy.aggregate(Arrays.asList(first, second));

        assertEquals(30.0, total.budgetJpy, 0.0000001);
        assertEquals(3.0, total.spentJpy, 0.0000001);
        assertEquals(27.0, total.remainingJpy, 0.0000001);
    }

    @Test
    public void resetLikeSnapshotCanHaveZeroCurrentAndPreservedLifetime() {
        NpcAiStaminaStore.Snapshot snapshot = new NpcAiStaminaStore.Snapshot(
                0.0, 42.0,
                0, 0, 0, 0,
                12.34, 1000, 250, 400, 1400);

        assertEquals(0.0, snapshot.spentJpy, 0.0);
        assertEquals(42.0, snapshot.budgetLimitJpy, 0.0);
        assertEquals(42.0, snapshot.remainingJpy, 0.0);
        assertEquals(12.34, snapshot.lifetimeSpentJpy, 0.0);
        assertEquals(1400L, snapshot.lifetimeTotalTokens);
    }
}
