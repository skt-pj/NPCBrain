package com.sktpj.npcbrain;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class NpcSocialMemoryScheduler {
    static final int JOB_ID = 0x4E4D534A;

    private NpcSocialMemoryScheduler() {
    }

    static boolean schedule(Context context) {
        if (context == null) return false;
        Context appContext = context.getApplicationContext();
        JobScheduler scheduler = appContext.getSystemService(JobScheduler.class);
        if (scheduler == null) return false;
        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(appContext, NpcSocialMemoryJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(HumanMemoryPolicy.MAINTENANCE_INTERVAL_MS)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }
}
