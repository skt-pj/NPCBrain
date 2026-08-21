package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WorldClockTest {
    @Test
    public void nowAdvancesToWallClock() {
        assertEquals(2000L, WorldClock.resolveNow(1000L, 2000L));
    }

    @Test
    public void nowNeverMovesBackward() {
        assertEquals(2000L, WorldClock.resolveNow(2000L, 1000L));
    }

    @Test
    public void advanceRejectsPastCandidate() {
        assertEquals(2000L, WorldClock.resolveAdvance(2000L, 1500L));
    }

    @Test
    public void advanceAcceptsFutureCandidate() {
        assertEquals(2500L, WorldClock.resolveAdvance(2000L, 2500L));
    }
}
