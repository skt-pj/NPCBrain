package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class WorldClock {
    private static final String PREFS = "npcbrain_world_clock_v040";
    private static final String KEY_WORLD_TIME_MS = "world_time_ms";

    private final SharedPreferences preferences;

    WorldClock(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized long now() {
        long stored = preferences.getLong(KEY_WORLD_TIME_MS, 0L);
        if (stored > 0L) return stored;
        long initialized = System.currentTimeMillis();
        preferences.edit().putLong(KEY_WORLD_TIME_MS, initialized).apply();
        return initialized;
    }

    synchronized long advanceTo(long candidateTimeMs) {
        long current = now();
        if (candidateTimeMs <= current) return current;
        preferences.edit().putLong(KEY_WORLD_TIME_MS, candidateTimeMs).apply();
        return candidateTimeMs;
    }
}
