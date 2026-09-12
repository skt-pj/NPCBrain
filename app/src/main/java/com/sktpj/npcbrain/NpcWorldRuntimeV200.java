package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** v2.0 compatibility name; v2.1 delegates every time path to one canonical driver. */
final class NpcWorldRuntimeV200 {
    private static final long FOREGROUND_TICK_MS = 650L;

    private final WorldSimulationDriverV210 driver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    private final Runnable foregroundTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            driver.advanceTo(System.currentTimeMillis());
            handler.postDelayed(this, FOREGROUND_TICK_MS);
        }
    };

    NpcWorldRuntimeV200(Context context) {
        driver = new WorldSimulationDriverV210(context.getApplicationContext());
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
}
