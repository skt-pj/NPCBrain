package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the last world-simulation opportunity independently from any screen. */
final class WorldSimulationCheckpointStore {
    private static final String PREFS = "npcbrain_world_simulation_checkpoint_v201";
    private static final String KEY_LAST = "last_simulation_ms";

    private final SharedPreferences preferences;

    WorldSimulationCheckpointStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    long lastSimulationMs() {
        return Math.max(0L, preferences.getLong(KEY_LAST, 0L));
    }

    void mark(long timeMs) {
        long current = lastSimulationMs();
        long next = WorldSimulationCatchUpPolicy.nextCheckpoint(current, timeMs);
        if (next != current) preferences.edit().putLong(KEY_LAST, next).apply();
    }
}
