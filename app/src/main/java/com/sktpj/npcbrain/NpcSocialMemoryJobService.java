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
            String apiKey = new SecureApiKeyStore(this).load();
            String key = apiKey == null ? "" : apiKey.trim();
            String reasoning = new ModelSettingsStore(this).reasoningEffort();

            // All time progression is owned by the canonical simulation driver.
            new NpcWorldRuntimeV200(this).runBackgroundOpportunity(key, reasoning, now);

            NpcRegistryStore registry = new NpcRegistryStore(this);
            List<String> active = registry.activeNpcIds();

            // Expensive autonomous choices are opportunities evaluated by the same per-NPC Brain
            // coordinator and committed through WorldKernel; they are not UI-owned simulation.
            try {
                new CanonicalDungeonAutonomyV211(this).evaluateDue(key, reasoning, now);
            } catch (Exception transientFailure) {
                retry = true;
            }

            HumanMemoryMaintenanceEngine maintenance = new HumanMemoryMaintenanceEngine(this);
            for (String npcId : active) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (!maintenance.isDue(npcId, now)) continue;
                if (!NpcInferenceAccess.canRun(this, npcId, key)) continue;
                try {
                    maintenance.runForNpc(npcId, key, reasoning, now);
                } catch (IllegalStateException budgetOrApiFailure) {
                    retry = true;
                } catch (Exception transientFailure) {
                    retry = true;
                }
            }

            // Social opportunities have their own cadence. Memory consolidation stays on the 12h
            // HumanMemoryPolicy cadence above.
            if (active.size() >= 2 && isSocialOpportunityDue(now)) {
                String actor = PeriodicSocialPolicy.initiator(active, now);
                if (!actor.isEmpty() && NpcInferenceAccess.canRun(this, actor, key)) {
                    try {
                        new PeriodicNpcSocialRuntime(this).runOneOpportunity(key, reasoning, now);
                        markSocialOpportunityAttempted(now);
                    } catch (Exception transientFailure) {
                        retry = true;
                    }
                } else {
                    // No runnable actor this window. Mark the opportunity so we do not spin every
                    // periodic job tick; the next social window naturally retries.
                    markSocialOpportunityAttempted(now);
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
