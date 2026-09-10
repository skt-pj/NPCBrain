package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ProcessingQueueTimingTest {
    @Before
    public void setUp() {
        ProcessingQueueRegistry.clearForTests();
    }

    @After
    public void tearDown() {
        ProcessingQueueRegistry.clearForTests();
    }

    @Test
    public void localLlmTracksEnqueueDequeueWaitAndProcessingDurations() {
        String id = ProcessingQueueRegistry.startRunning(
                "llm_request",
                "npc9",
                "local_light · specialist / task",
                1_000L);

        ProcessingQueueRegistry.Entry queued = ProcessingQueueRegistry.snapshot().active.get(0);
        assertEquals(id, queued.id);
        assertEquals(1_000L, queued.queuedAtMs);
        assertFalse(queued.hasBeenDequeued());
        assertEquals(500L, queued.queueWaitDurationMs(1_500L));
        assertEquals(0L, queued.processingDurationMs(1_500L));

        ProcessingQueueRegistry.markRunning(id, 1_800L);
        ProcessingQueueRegistry.Entry running = ProcessingQueueRegistry.snapshot().active.get(0);
        assertTrue(running.hasBeenDequeued());
        assertEquals(1_800L, running.dequeuedAtMs());
        assertEquals(800L, running.queueWaitDurationMs(2_300L));
        assertEquals(500L, running.processingDurationMs(2_300L));

        ProcessingQueueRegistry.markCompleted(id, 2_700L, "done");
        ProcessingQueueRegistry.Entry completed = ProcessingQueueRegistry.snapshot().recent.get(0);
        assertEquals(1_000L, completed.queuedAtMs);
        assertEquals(1_800L, completed.dequeuedAtMs());
        assertEquals(2_700L, completed.finishedAtMs);
        assertEquals(800L, completed.queueWaitDurationMs(9_999L));
        assertEquals(900L, completed.processingDurationMs(9_999L));
    }

    @Test
    public void dequeueTimestampIsCapturedOnlyOnce() {
        String id = ProcessingQueueRegistry.startRunning(
                "llm_request",
                "npc10",
                "local_medium · specialist / task",
                5_000L);

        ProcessingQueueRegistry.markRunning(id, 5_400L);
        ProcessingQueueRegistry.markRunning(id, 8_000L);

        ProcessingQueueRegistry.Entry running = ProcessingQueueRegistry.snapshot().active.get(0);
        assertEquals(5_400L, running.dequeuedAtMs());
        assertEquals(400L, running.queueWaitDurationMs(9_000L));
        assertEquals(3_600L, running.processingDurationMs(9_000L));
    }
}
