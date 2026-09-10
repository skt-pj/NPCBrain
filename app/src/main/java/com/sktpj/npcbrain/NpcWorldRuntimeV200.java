package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Single runtime entry that keeps life, dungeon and observable world state synchronized. */
final class NpcWorldRuntimeV200 {
    private static final long FOREGROUND_TICK_MS = 650L;

    private final WorldRuntimeV040 lifeRuntime;
    private final DungeonWorldProgressRuntime dungeonProgress;
    private final DungeonWorldEventSynchronizer dungeonEvents;
    private final DungeonAutonomyRuntime dungeonAutonomy;
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

    void syncForegroundOnce(long nowMs) {
        lifeRuntime.syncAllNow();
        dungeonProgress.advanceSoloPresentOnce(nowMs);
        dungeonEvents.syncAll(nowMs);
    }

    void runBackgroundOpportunity(String apiKey, String reasoningEffort, long nowMs) {
        lifeRuntime.syncAllNow();
        dungeonAutonomy.evaluateAndJoin(nowMs, apiKey, reasoningEffort);
        dungeonProgress.advanceSoloPresentOnce(nowMs);
        dungeonEvents.syncAll(nowMs);
    }
}
