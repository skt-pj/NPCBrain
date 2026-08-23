package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class ParallelCognitionScheduler {
    interface Work<T> {
        T run(int index) throws Exception;
    }

    interface Completion<T> {
        void onCompleted(int index, T result);
    }

    private ParallelCognitionScheduler() {}

    static <T> List<T> run(
            int count,
            Work<T> work,
            Completion<T> completion
    ) throws Exception {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        if (work == null) throw new IllegalArgumentException("work is required");
        if (count == 0) return new ArrayList<>();

        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "npcbrain-specialist-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService executor = Executors.newFixedThreadPool(count, factory);
        CompletionService<IndexedResult<T>> service = new ExecutorCompletionService<>(executor);
        Object[] ordered = new Object[count];

        for (int i = 0; i < count; i++) {
            final int index = i;
            service.submit(() -> new IndexedResult<>(index, work.run(index)));
        }

        Throwable firstFailure = null;
        int received = 0;
        try {
            while (received < count) {
                Future<IndexedResult<T>> future = service.take();
                received++;
                try {
                    IndexedResult<T> completed = future.get();
                    ordered[completed.index] = completed.result;
                    if (completion != null) {
                        completion.onCompleted(completed.index, completed.result);
                    }
                } catch (ExecutionException error) {
                    if (firstFailure == null) {
                        firstFailure = error.getCause() == null ? error : error.getCause();
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            executor.shutdownNow();
        }

        if (firstFailure != null) {
            if (firstFailure instanceof Exception) throw (Exception) firstFailure;
            if (firstFailure instanceof Error) throw (Error) firstFailure;
            throw new RuntimeException(firstFailure);
        }

        List<T> result = new ArrayList<>(count);
        for (Object item : ordered) {
            @SuppressWarnings("unchecked")
            T typed = (T) item;
            result.add(typed);
        }
        return result;
    }

    private static final class IndexedResult<T> {
        final int index;
        final T result;

        IndexedResult(int index, T result) {
            this.index = index;
            this.result = result;
        }
    }
}
