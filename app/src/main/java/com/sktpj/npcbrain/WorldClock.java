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
        long resolved = resolveNow(stored, System.currentTimeMillis());
        if (resolved != stored) {
            preferences.edit().putLong(KEY_WORLD_TIME_MS, resolved).apply();
        }
        return resolved;
    }

    synchronized long advanceTo(long candidateTimeMs) {
        long current = now();
        long resolved = resolveAdvance(current, candidateTimeMs);
        if (resolved != current) {
            preferences.edit().putLong(KEY_WORLD_TIME_MS, resolved).apply();
        }
        return resolved;
    }

    static long resolveNow(long storedTimeMs, long wallTimeMs) {
        if (storedTimeMs <= 0L) return Math.max(0L, wallTimeMs);
        return Math.max(storedTimeMs, wallTimeMs);
    }

    static long resolveAdvance(long currentTimeMs, long candidateTimeMs) {
        return Math.max(currentTimeMs, candidateTimeMs);
    }
}
