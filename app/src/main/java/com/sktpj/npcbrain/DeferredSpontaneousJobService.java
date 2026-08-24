package com.sktpj.npcbrain;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DeferredSpontaneousJobService extends JobService {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "npcbrain-deferred-spontaneous");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<Integer, Future<?>> running = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public boolean onStartJob(JobParameters params) {
        if (params == null) return false;
        PersistableBundle extras = params.getExtras();
        String eventId = extras == null
                ? ""
                : safe(extras.getString(DeferredSpontaneousScheduler.EXTRA_SOURCE_EVENT_ID));
        if (eventId.isEmpty()) return false;

        int jobId = params.getJobId();
        Future<?> future = EXECUTOR.submit(() -> runDeferredJob(params, eventId));
        running.put(jobId, future);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (params == null) return true;
        Future<?> future = running.remove(params.getJobId());
        if (future != null) future.cancel(true);
        return true;
    }

    private void runDeferredJob(JobParameters params, String eventId) {
        int jobId = params.getJobId();
        try {
            SpontaneousMessageStore store = new SpontaneousMessageStore(getApplicationContext());
            SpontaneousMessageStore.DeferredStatus before = store.deferredStatus(eventId);
            if (!before.deferred) {
                finish(params, false);
                return;
            }

            long now = System.currentTimeMillis();
            if (before.nextEligibleTimeMs > now) {
                finishAndReschedule(params, eventId, before.jobId, before.nextEligibleTimeMs);
                return;
            }

            String apiKey = new SecureApiKeyStore(getApplicationContext()).load();
            if (apiKey == null || apiKey.trim().isEmpty()) {
                finish(params, true);
                return;
            }
            String effort = new ModelSettingsStore(getApplicationContext()).reasoningEffort();
            DemoRuntimeV032 runtime = new DemoRuntimeV032(
                    getApplicationContext(), new ConversationStore(getApplicationContext()));

            DeferredSpontaneousEventScope.enter(eventId);
            try {
                runtime.processPendingSpontaneous(apiKey, effort, null);
            } finally {
                DeferredSpontaneousEventScope.exit();
            }

            SpontaneousMessageStore.DeferredStatus after = store.deferredStatus(eventId);
            if (!after.deferred) {
                finish(params, false);
                return;
            }
            long afterNow = System.currentTimeMillis();
            if (after.nextEligibleTimeMs > afterNow) {
                finishAndReschedule(
                        params, eventId, after.jobId, after.nextEligibleTimeMs);
            } else {
                // The event remained due, normally because the process-wide gate was busy.
                finish(params, true);
            }
        } catch (Exception ignored) {
            // Keep the deferred checkpoint intact. JobScheduler backoff will retry it.
            finish(params, true);
        }
    }

    private void finish(JobParameters params, boolean retry) {
        int jobId = params.getJobId();
        mainHandler.post(() -> {
            if (running.remove(jobId) == null) return;
            jobFinished(params, retry);
        });
    }

    private void finishAndReschedule(
            JobParameters params,
            String eventId,
            int storedJobId,
            long dueMs
    ) {
        int jobId = params.getJobId();
        int nextJobId = storedJobId > 0 ? storedJobId : jobId;
        mainHandler.post(() -> {
            if (running.remove(jobId) == null) return;
            jobFinished(params, false);
            DeferredSpontaneousScheduler.schedule(
                    getApplicationContext(), eventId, nextJobId, dueMs);
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
