package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ProcessingQueueRegistryTest {
    @Before
    public void setUp() {
        ProcessingQueueRegistry.clearForTests();
    }

    @After
    public void tearDown() {
        ProcessingQueueRegistry.clearForTests();
    }

    @Test
    public void queuedRunningCompletedPreservesIdentityAndTimestamps() {
        String id = ProcessingQueueRegistry.enqueue("conversation_reply", "npc10", "room=direct_npc10", 100L);
        ProcessingQueueRegistry.markRunning(id, 150L);
        ProcessingQueueRegistry.Snapshot running = ProcessingQueueRegistry.snapshot();
        assertEquals(1, running.active.size());
        assertEquals(id, running.active.get(0).id);
        assertEquals(ProcessingQueueRegistry.Status.RUNNING, running.active.get(0).status);
        assertEquals(100L, running.active.get(0).queuedAtMs);
        assertEquals(150L, running.active.get(0).startedAtMs);

        ProcessingQueueRegistry.markCompleted(id, 225L, "room=direct_npc10");
        ProcessingQueueRegistry.Snapshot done = ProcessingQueueRegistry.snapshot();
        assertTrue(done.active.isEmpty());
        assertEquals(1, done.recent.size());
        assertEquals(id, done.recent.get(0).id);
        assertEquals(ProcessingQueueRegistry.Status.COMPLETED, done.recent.get(0).status);
        assertEquals(225L, done.recent.get(0).finishedAtMs);
    }

    @Test
    public void claimConversationReusesQueuedEntryForSameRoom() {
        String queued = ProcessingQueueRegistry.enqueueConversationWait("direct_npc10", "npc10", 100L);
        String claimed = ProcessingQueueRegistry.claimConversation("direct_npc10", "npc10", 200L);
        assertEquals(queued, claimed);
        ProcessingQueueRegistry.Snapshot snapshot = ProcessingQueueRegistry.snapshot();
        assertEquals(1, snapshot.active.size());
        assertEquals(ProcessingQueueRegistry.Status.RUNNING, snapshot.active.get(0).status);
    }

    @Test
    public void failedEntryKeepsShortRootError() {
        String id = ProcessingQueueRegistry.startRunning("llm_request", "npc2", "local_light", 100L);
        ProcessingQueueRegistry.markFailed(id, new RuntimeException("outer", new IllegalArgumentException("bad input")));
        ProcessingQueueRegistry.Entry entry = ProcessingQueueRegistry.snapshot().recent.get(0);
        assertEquals(ProcessingQueueRegistry.Status.FAILED, entry.status);
        assertEquals("bad input", entry.error);
    }

    @Test
    public void terminalHistoryIsBoundedWithoutDroppingActive() {
        String activeId = ProcessingQueueRegistry.enqueue("conversation_reply", "npc1", "room=direct_npc1", 1L);
        for (int i = 0; i < ProcessingQueueRegistry.MAX_TERMINAL_HISTORY + 20; i++) {
            String id = ProcessingQueueRegistry.startRunning("llm_request", "npc1", "r" + i, 10L + i);
            ProcessingQueueRegistry.markCompleted(id, 20L + i, "r" + i);
        }
        ProcessingQueueRegistry.Snapshot snapshot = ProcessingQueueRegistry.snapshot();
        assertEquals(ProcessingQueueRegistry.MAX_TERMINAL_HISTORY, snapshot.recent.size());
        assertEquals(1, snapshot.active.size());
        assertEquals(activeId, snapshot.active.get(0).id);
    }

    @Test
    public void concurrentUpdatesRemainConsistent() throws Exception {
        int workers = 8;
        int each = 40;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<Throwable> failures = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            final int worker = w;
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await(3, TimeUnit.SECONDS);
                    for (int i = 0; i < each; i++) {
                        String id = ProcessingQueueRegistry.enqueue("llm_request", "npc" + worker, "x", i);
                        ProcessingQueueRegistry.markRunning(id, i + 1L);
                        ProcessingQueueRegistry.snapshot();
                        ProcessingQueueRegistry.markCompleted(id, i + 2L, "x");
                    }
                } catch (Throwable error) {
                    synchronized (failures) {
                        failures.add(error);
                    }
                } finally {
                    done.countDown();
                }
            }).start();
        }
        assertTrue(ready.await(3, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(failures.toString(), failures.isEmpty());
        assertEquals(0, ProcessingQueueRegistry.activeCount());
        assertFalse(ProcessingQueueRegistry.snapshot().recent.isEmpty());
    }
}
