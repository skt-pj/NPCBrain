package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Single runtime entry that owns life/dungeon progression and observable world synchronization. */
final class NpcWorldRuntimeV200 {
    private static final long FOREGROUND_TICK_MS = 650L;

    private final WorldRuntimeV040 lifeRuntime;
    private final DungeonWorldProgressRuntime dungeonProgress;
    private final DungeonWorldEventSynchronizer dungeonEvents;
    private final DungeonAutonomyRuntime dungeonAutonomy;
    private final WorldSimulationCheckpointStore checkpoint;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    private final Runnable foregroundTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            syncForegroundOnce(System.currentTimeMillis());
            handler.postDelayed(this, FOREGROUND_TICK_MS);
        }
    };

    NpcWorldRuntimeV200(Context context) {
        Context app = context.getApplicationContext();
        lifeRuntime = new WorldRuntimeV040(app);
        dungeonProgress = new DungeonWorldProgressRuntime(app);
        dungeonEvents = new DungeonWorldEventSynchronizer(app);
        dungeonAutonomy = new DungeonAutonomyRuntime(app);
        checkpoint = new WorldSimulationCheckpointStore(app);
        prepareObservationState(System.currentTimeMillis());
    }

    synchronized void start() {
        if (running) return;
        running = true;
        handler.removeCallbacks(foregroundTask);
        handler.post(foregroundTask);
    }

    synchronized void stop() {
        running = false;
        handler.removeCallbacks(foregroundTask);
    }

    void prepareObservationState(long nowMs) {
        dungeonProgress.prepareRegisteredStates(nowMs);
    }

    void syncForegroundOnce(long nowMs) {
        lifeRuntime.syncAllNow();
        prepareObservationState(nowMs);
        dungeonProgress.advancePresentOnce(nowMs);
        dungeonEvents.syncAll(nowMs);
        checkpoint.mark(nowMs);
    }

    void runBackgroundOpportunity(String apiKey, String reasoningEffort, long nowMs) {
        long previous = checkpoint.lastSimulationMs();
        int catchUpSteps = WorldSimulationCatchUpPolicy.backgroundSteps(previous, nowMs);
        lifeRuntime.syncAllNow();
        prepareObservationState(nowMs);
        dungeonAutonomy.evaluateAndJoin(nowMs, apiKey, reasoningEffort);
        for (int i = 0; i < catchUpSteps; i++) {
            long simulatedTime = backgroundStepTime(previous, nowMs, i, catchUpSteps);
            dungeonProgress.advancePresentCatchUpOnce(simulatedTime);
            dungeonEvents.syncAll(simulatedTime);
        }
        checkpoint.mark(nowMs);
    }

    static long backgroundStepTime(long previousMs, long nowMs, int index, int totalSteps) {
        if (previousMs <= 0L || totalSteps <= 1) return nowMs;
        long candidate = previousMs + (long) (index + 1) * WorldSimulationCatchUpPolicy.BACKGROUND_STEP_MS;
        return Math.min(nowMs, Math.max(previousMs, candidate));
    }
}
