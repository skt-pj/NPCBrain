package com.sktpj.npcbrain;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class NpcSocialMemoryJobService extends JobService {
    private static final String SOCIAL_PREFS = "npcbrain_periodic_social_v1";
    private static final String LAST_WINDOW = "last_window";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Future<?> running;

    @Override
    public boolean onStartJob(JobParameters params) {
        running = executor.submit(() -> runMaintenance(params));
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

    private void runMaintenance(JobParameters params) {
        boolean retry = false;
        try {
            long now = System.currentTimeMillis();
            new DungeonAutonomyRuntime(this).evaluateAndJoin(now);

            String apiKey = new SecureApiKeyStore(this).load();
            if (apiKey == null || apiKey.trim().isEmpty()) return;
            String key = apiKey.trim();
            String reasoning = new ModelSettingsStore(this).reasoningEffort();
            NpcRegistryStore registry = new NpcRegistryStore(this);
            List<String> active = registry.activeNpcIds();
            HumanMemoryMaintenanceEngine maintenance = new HumanMemoryMaintenanceEngine(this);

            for (String npcId : active) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (!maintenance.isDue(npcId, now)) continue;
                NpcAiStaminaStore.Snapshot budget = new NpcAiStaminaStore(this).snapshot(npcId);
                if (budget.exhausted()) continue;
                try {
                    maintenance.runForNpc(npcId, key, reasoning, now);
                } catch (IllegalStateException budgetOrApiFailure) {
                    retry = true;
                } catch (Exception transientFailure) {
                    retry = true;
                }
            }

            if (active.size() >= 2 && isSocialOpportunityDue(now)) {
                String actor = PeriodicSocialPolicy.initiator(active, now);
                if (!actor.isEmpty() && !new NpcAiStaminaStore(this).snapshot(actor).exhausted()) {
                    try {
                        new PeriodicNpcSocialRuntime(this).runOneOpportunity(key, reasoning, now);
                        markSocialOpportunityAttempted(now);
                    } catch (Exception transientFailure) {
                        retry = true;
                    }
                }
            }
            NPCBrainApplication.requestDemoRoomRefresh();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            retry = true;
        } catch (Exception ignored) {
            retry = true;
        } finally {
            jobFinished(params, retry);
        }
    }

    private boolean isSocialOpportunityDue(long nowMs) {
        SharedPreferences prefs = getSharedPreferences(SOCIAL_PREFS, Context.MODE_PRIVATE);
        long lastWindow = prefs.getLong(LAST_WINDOW, -1L);
        return PeriodicSocialPolicy.window(nowMs) > lastWindow;
    }

    private void markSocialOpportunityAttempted(long nowMs) {
        getSharedPreferences(SOCIAL_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(LAST_WINDOW, PeriodicSocialPolicy.window(nowMs))
                .commit();
    }
}
