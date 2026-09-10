package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalLlmExecutionQueueTest {
    @Before
    public void setUp() {
        ProcessingQueueRegistry.clearForTests();
    }

    @After
    public void tearDown() {
        ProcessingQueueRegistry.clearForTests();
    }

    @Test
    public void localRequestsUseSingleExecutionSlot() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(3);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger entered = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                futures.add(callers.submit(() -> {
                    try {
                        LocalLlmExecutionQueue.execute("", () -> {
                            int concurrent = active.incrementAndGet();
                            maxActive.accumulateAndGet(concurrent, Math::max);
                            int ordinal = entered.incrementAndGet();
                            try {
                                if (ordinal == 1) {
                                    firstEntered.countDown();
                                    if (!releaseFirst.await(3, TimeUnit.SECONDS)) {
                                        throw new AssertionError("timed out waiting to release first request");
                                    }
                                } else {
                                    Thread.sleep(25L);
                                }
                            } finally {
                                active.decrementAndGet();
                            }
                            return null;
                        });
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                }));
            }

            assertTrue(firstEntered.await(3, TimeUnit.SECONDS));
            long deadline = System.currentTimeMillis() + 3000L;
            while (LocalLlmExecutionQueue.waitingCountForTests() < 2
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            assertEquals(1, LocalLlmExecutionQueue.runningCountForTests());
            assertEquals(2, LocalLlmExecutionQueue.waitingCountForTests());
            releaseFirst.countDown();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertEquals(1, maxActive.get());
        } finally {
            releaseFirst.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void localDiagnosticEntryIsQueuedUntilSlotStarts() throws Exception {
        String id = ProcessingQueueRegistry.startRunning(
                "llm_request", "npc10", "local_light · specialist / task", 100L);
        ProcessingQueueRegistry.Entry queued = ProcessingQueueRegistry.snapshot().active.get(0);
        assertEquals(id, queued.id);
        assertEquals(ProcessingQueueRegistry.Status.QUEUED, queued.status);

        LocalLlmExecutionQueue.execute(id, () -> {
            ProcessingQueueRegistry.Entry running = ProcessingQueueRegistry.snapshot().active.get(0);
            assertEquals(ProcessingQueueRegistry.Status.RUNNING, running.status);
            return null;
        });
        ProcessingQueueRegistry.markCompleted(id, 200L, "done");
        assertTrue(ProcessingQueueRegistry.snapshot().active.isEmpty());
    }
}
