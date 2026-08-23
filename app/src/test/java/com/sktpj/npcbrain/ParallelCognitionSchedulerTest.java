package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ParallelCognitionSchedulerTest {
    @Test
    public void allTasksCanStartBeforeAnyTaskIsReleased() throws Exception {
        int count = 4;
        CountDownLatch started = new CountDownLatch(count);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<List<Integer>> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread runner = new Thread(() -> {
            try {
                result.set(ParallelCognitionScheduler.run(
                        count,
                        index -> {
                            started.countDown();
                            if (!release.await(3, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("release timeout");
                            }
                            return index;
                        },
                        null));
            } catch (Throwable failure) {
                error.set(failure);
            }
        });
        runner.start();

        assertTrue("all workers should enter concurrently", started.await(2, TimeUnit.SECONDS));
        release.countDown();
        runner.join(4000L);

        assertFalse("scheduler should finish", runner.isAlive());
        assertNull(error.get());
        assertEquals(Arrays.asList(0, 1, 2, 3), result.get());
    }

    @Test
    public void returnedResultsStayInCanonicalInputOrder() throws Exception {
        List<String> result = ParallelCognitionScheduler.run(
                4,
                index -> {
                    Thread.sleep((3L - index) * 25L);
                    return "module-" + index;
                },
                null);

        assertEquals(Arrays.asList(
                "module-0", "module-1", "module-2", "module-3"), result);
    }

    @Test
    public void specialistFailureIsPropagated() throws Exception {
        try {
            ParallelCognitionScheduler.run(
                    3,
                    index -> {
                        if (index == 1) throw new IllegalStateException("specialist boom");
                        return index;
                    },
                    null);
            fail("expected failure");
        } catch (IllegalStateException expected) {
            assertEquals("specialist boom", expected.getMessage());
        }
    }
}
