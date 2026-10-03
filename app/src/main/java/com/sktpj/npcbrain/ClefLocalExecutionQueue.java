package com.sktpj.npcbrain;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** FIFO gate for the single local CLEF runtime/context. */
final class ClefLocalExecutionQueue {
    private static final ThreadLocal<Boolean> IN_WORKER = new ThreadLocal<>();

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "npcbrain-clef-fifo");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private ClefLocalExecutionQueue() {
    }

    static <T> T execute(String queueId, Callable<T> task) throws Exception {
        if (task == null) throw new IllegalArgumentException("task is required");
        if (Boolean.TRUE.equals(IN_WORKER.get())) {
            markRunning(queueId);
            return task.call();
        }

        Future<T> future = EXECUTOR.submit(() -> {
            IN_WORKER.set(Boolean.TRUE);
            try {
                markRunning(queueId);
                return task.call();
            } finally {
                IN_WORKER.remove();
            }
        });

        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        }
    }

    private static void markRunning(String queueId) {
        if (queueId == null || queueId.trim().isEmpty()) return;
        ProcessingQueueRegistry.markRunning(queueId);
    }

    static int waitingCountForTests() {
        return EXECUTOR.getQueue().size();
    }

    static int runningCountForTests() {
        return EXECUTOR.getActiveCount();
    }
}
