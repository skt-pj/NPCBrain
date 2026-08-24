package com.sktpj.npcbrain;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

final class ReplyTimerScheduler {
    static final String EXTRA_SOURCE_KEY = "reply_timer_source_key";
    static final String EXTRA_WAKE_AT_MS = "reply_timer_wake_at_ms";
    private static final long BACKOFF_MS = 30_000L;

    private ReplyTimerScheduler() {}

    static boolean schedule(Context context, ReplyTimerTask task) {
        if (context == null || task == null || !task.isValid()) return false;
        JobScheduler scheduler = (JobScheduler) context.getApplicationContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return false;

        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_SOURCE_KEY, task.sourceKey);
        extras.putLong(EXTRA_WAKE_AT_MS, task.wakeAtMs);
        long delay = ReplyTimerSchedulePolicy.delayMs(System.currentTimeMillis(), task.wakeAtMs);
        JobInfo job = new JobInfo.Builder(
                task.jobId,
                new ComponentName(context.getApplicationContext(), ReplyTimerJobService.class))
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
