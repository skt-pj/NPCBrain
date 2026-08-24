package com.sktpj.npcbrain;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

final class DeferredSpontaneousScheduler {
    static final String EXTRA_SOURCE_EVENT_ID = "source_event_id";
    static final String EXTRA_DUE_MS = "due_ms";
    private static final long BACKOFF_MS = 30_000L;

    private DeferredSpontaneousScheduler() {}

    static boolean schedule(
            Context context,
            String sourceEventId,
            int jobId,
            long dueMs
    ) {
        if (context == null || jobId <= 0) return false;
        String eventId = sourceEventId == null ? "" : sourceEventId.trim();
        if (eventId.isEmpty()) return false;
        JobScheduler scheduler = (JobScheduler) context.getApplicationContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return false;

        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_SOURCE_EVENT_ID, eventId);
        extras.putLong(EXTRA_DUE_MS, dueMs);
        long delay = DeferredSpontaneousSchedulePolicy.delayMs(
                System.currentTimeMillis(), dueMs);
        JobInfo job = new JobInfo.Builder(
                jobId,
                new ComponentName(context.getApplicationContext(),
                        DeferredSpontaneousJobService.class))
                .setExtras(extras)
                .setMinimumLatency(delay)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        try {
            return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void cancel(Context context, int jobId) {
        if (context == null || jobId <= 0) return;
        JobScheduler scheduler = (JobScheduler) context.getApplicationContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        try {
            scheduler.cancel(jobId);
        } catch (RuntimeException ignored) {
        }
    }
}
