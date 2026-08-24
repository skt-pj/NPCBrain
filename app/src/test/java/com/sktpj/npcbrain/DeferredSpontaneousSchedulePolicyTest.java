package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DeferredSpontaneousSchedulePolicyTest {
    @Test
    public void futureDueReturnsExactDelay() {
        assertEquals(2_500L, DeferredSpontaneousSchedulePolicy.delayMs(1_000L, 3_500L));
    }

    @Test
    public void dueOrPastReturnsZero() {
        assertEquals(0L, DeferredSpontaneousSchedulePolicy.delayMs(5_000L, 5_000L));
        assertEquals(0L, DeferredSpontaneousSchedulePolicy.delayMs(5_000L, 4_999L));
    }

    @Test
    public void subtractionOverflowClampsToMax() {
        assertEquals(Long.MAX_VALUE,
                DeferredSpontaneousSchedulePolicy.delayMs(-1L, Long.MAX_VALUE));
    }
}
