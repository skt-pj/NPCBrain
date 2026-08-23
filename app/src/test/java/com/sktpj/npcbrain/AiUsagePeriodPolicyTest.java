package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AiUsagePeriodPolicyTest {
    @Test
    public void monthIndexMovesForwardExactlyOncePerCalendarMonth() {
        assertEquals(2026 * 12 + 7, AiUsagePeriodPolicy.monthIndex(2026, 7));
        assertEquals(2026 * 12 + 8, AiUsagePeriodPolicy.monthIndex(2026, 8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void monthIndexRejectsInvalidMonth() {
        AiUsagePeriodPolicy.monthIndex(2026, 12);
    }

    @Test
    public void legacyCountersAreMigratedWithoutReset() {
        assertEquals(
                AiUsagePeriodPolicy.Action.MIGRATE,
                AiUsagePeriodPolicy.action(false, 0, 2026 * 12 + 7));
    }

    @Test
    public void sameMonthNeverResets() {
        int month = AiUsagePeriodPolicy.monthIndex(2026, 7);
        assertEquals(AiUsagePeriodPolicy.Action.KEEP,
                AiUsagePeriodPolicy.action(true, month, month));
    }

    @Test
    public void nextMonthResetsOnce() {
        int august = AiUsagePeriodPolicy.monthIndex(2026, 7);
        int september = AiUsagePeriodPolicy.monthIndex(2026, 8);
        assertEquals(AiUsagePeriodPolicy.Action.RESET,
                AiUsagePeriodPolicy.action(true, august, september));
        assertEquals(AiUsagePeriodPolicy.Action.KEEP,
                AiUsagePeriodPolicy.action(true, september, september));
    }

    @Test
    public void clockRollbackDoesNotResetOrRestoreUsage() {
        int september = AiUsagePeriodPolicy.monthIndex(2026, 8);
        int august = AiUsagePeriodPolicy.monthIndex(2026, 7);
        assertEquals(AiUsagePeriodPolicy.Action.KEEP,
                AiUsagePeriodPolicy.action(true, september, august));
    }
}
