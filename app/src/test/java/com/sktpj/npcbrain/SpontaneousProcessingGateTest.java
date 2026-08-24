package com.sktpj.npcbrain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class SpontaneousProcessingGateTest {
    @After
    public void cleanup() {
        SpontaneousProcessingGate.release();
    }

    @Test
    public void onlyOneProcessorCanAcquireUntilRelease() {
        SpontaneousProcessingGate.release();
        assertTrue(SpontaneousProcessingGate.tryAcquire());
        assertTrue(SpontaneousProcessingGate.isBusy());
        assertFalse(SpontaneousProcessingGate.tryAcquire());
        SpontaneousProcessingGate.release();
        assertFalse(SpontaneousProcessingGate.isBusy());
        assertTrue(SpontaneousProcessingGate.tryAcquire());
    }
}
