package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** v2.0 compatibility name; v2.1+ delegates time and autonomous opportunities to canonical paths. */
final class NpcWorldRuntimeV200 {
    private static final long FOREGROUND_TICK_MS = 650L;
    private static final long OPPORTUNITY_CHECK_MS = 60L * 1000L;

    private final Context appContext;
    private final WorldSimulationDriverV210 driver;
    private final NpcRegistryStore registry;
    private final AutonomousSocialOpportunityStoreV211 socialWindow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService autonomousExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean autonomousRunning = new AtomicBoolean(false);
    private boolean running;
    private long lastOpportunityCheckAt;

    private final Runnable foregroundTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            driver.advanceTo(now);
            maybeRunAutonomousOpportunities(now);
            handler.postDelayed(this, FOREGROUND_TICK_MS);
        }
    };

    NpcWorldRuntimeV200(Context context) {
        appContext = context.getApplicationContext();
        driver = new WorldSimulationDriverV210(appContext);
        registry = new NpcRegistryStore(appContext);
        socialWindow = new AutonomousSocialOpportunityStoreV211(appContext);
    }

    synchronized void start() {
        if (running) return;
        running = true;
        lastOpportunityCheckAt = 0L;
        handler.removeCallbacks(foregroundTask);
        handler.post(foregroundTask);
    }

    synchronized void stop() {
        running = false;
        handler.removeCallbacks(foregroundTask);
    }

    void syncForegroundOnce(long nowMs) {
        driver.advanceTo(nowMs);
    }

    void runBackgroundOpportunity(String apiKey, String reasoningEffort, long nowMs) {
        driver.advanceTo(nowMs);
    }

    WorldQueryServiceV210 query() {
        return driver.query();
    }

    WorldKernelV210 kernel() {
        return driver.kernel();
    }

    private void maybeRunAutonomousOpportunities(long nowMs) {
        if (nowMs - lastOpportunityCheckAt < OPPORTUNITY_CHECK_MS) return;
        lastOpportunityCheckAt = nowMs;
        if (!autonomousRunning.compareAndSet(false, true)) return;
        autonomousExecutor.execute(() -> {
            try {
                String apiKey = "";
                try {
                    String loaded = new SecureApiKeyStore(appContext).load();
                    apiKey = loaded == null ? "" : loaded.trim();
                } catch (Exception ignored) {
                }
                String reasoning = new ModelSettingsStore(appContext).reasoningEffort();

                new CanonicalDungeonAutonomyV211(appContext)
                        .evaluateDue(apiKey, reasoning, nowMs);

                List<String> active = registry.activeNpcIds();
                if (active.size() >= 2 && socialWindow.isDue(nowMs)) {
                    String actor = PeriodicSocialPolicy.initiator(active, nowMs);
                    if (!actor.isEmpty() && NpcInferenceAccess.canRun(appContext, actor, apiKey)) {
                        new PeriodicNpcSocialRuntime(appContext)
                                .runOneOpportunity(apiKey, reasoning, nowMs);
                        socialWindow.markAttempted(nowMs);
                        NPCBrainApplication.requestDemoRoomRefresh();
                    }
                }
            } catch (Exception ignored) {
                // Opportunity failures do not stop the deterministic world clock. The same social
                // window remains due unless an actual attempt was recorded.
            } finally {
                autonomousRunning.set(false);
            }
        });
    }
}
