package com.sktpj.npcbrain;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ReplyTimerJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Future<?> running;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (params == null || params.getExtras() == null) return false;
        final String sourceKey = params.getExtras().getString(
                ReplyTimerScheduler.EXTRA_SOURCE_KEY, "");
        if (sourceKey == null || sourceKey.trim().isEmpty()) return false;

        running = executor.submit(() -> runTimer(params, sourceKey.trim()));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Future<?> current = running;
        if (current != null) current.cancel(true);
        return true;
    }

    @Override
    public void onDestroy() {
        Future<?> current = running;
        if (current != null) current.cancel(true);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runTimer(JobParameters params, String sourceKey) {
        ReplyTimerStore store = new ReplyTimerStore(this);
        ReplyTimerTask task = store.get(sourceKey);
        if (task == null) {
            jobFinished(params, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (!ReplyTimerPolicy.isDue(task, now)) {
            ReplyTimerScheduler.schedule(this, task);
            jobFinished(params, false);
            return;
        }
        if (!ReplyTimerProcessingGate.tryAcquire(sourceKey)) {
            jobFinished(params, true);
            return;
        }

        boolean retry = false;
        ReplyTimerExecutionScope.enter(sourceKey);
        try {
            String apiKey = new SecureApiKeyStore(this).load();
            if (apiKey == null || apiKey.trim().isEmpty()) {
                retry = true;
                return;
            }
            DemoRuntimeV032 runtime = new DemoRuntimeV032(this, new ConversationStore(this));
            runtime.processReplyTimer(
                    task,
                    apiKey.trim(),
                    new ModelSettingsStore(this).reasoningEffort(),
                    null);

            ReplyTimerTask after = store.get(sourceKey);
            if (after != null) {
                if (after.wakeAtMs > System.currentTimeMillis()) {
                    if (!ReplyTimerScheduler.schedule(this, after)) retry = true;
                } else {
                    retry = true;
                }
            }
            NPCBrainApplication.requestDemoRoomRefresh();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            retry = true;
        } catch (Exception ignored) {
            retry = true;
        } finally {
            ReplyTimerExecutionScope.exit();
            ReplyTimerProcessingGate.release(sourceKey);
            jobFinished(params, retry);
        }
    }
}
