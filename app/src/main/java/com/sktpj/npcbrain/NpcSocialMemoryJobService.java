package com.sktpj.npcbrain;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class NpcSocialMemoryJobService extends JobService {
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

            new NpcWorldRuntimeV200(this).runBackgroundOpportunity(key, reasoning, now);

            NpcRegistryStore registry = new NpcRegistryStore(this);
            List<String> active = registry.activeNpcIds();

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
                } catch (Exception transientFailure) {
                    retry = true;
                }
            }

            AutonomousSocialOpportunityStoreV211 socialWindow =
                    new AutonomousSocialOpportunityStoreV211(this);
            if (active.size() >= 2 && socialWindow.isDue(now)) {
                String actor = PeriodicSocialPolicy.initiator(active, now);
                if (!actor.isEmpty() && NpcInferenceAccess.canRun(this, actor, key)) {
                    try {
                        new PeriodicNpcSocialRuntime(this).runOneOpportunity(key, reasoning, now);
                    } catch (Exception transientFailure) {
                        retry = true;
                    }
                }
                socialWindow.markAttempted(now);
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
}
