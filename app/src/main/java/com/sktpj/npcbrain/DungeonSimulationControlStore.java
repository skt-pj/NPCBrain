package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared controls for the world-owned dungeon simulation. UI edits these values; it does not step. */
final class DungeonSimulationControlStore {
    private static final String PREFS = "npcbrain_dungeon_simulation_control_v201";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_SPEED = "speed_index";
    private static final String KEY_LAST_PREFIX = "last_step_";
    private static final long[] INTERVALS_MS = {1100L, 650L, 350L};

    private final SharedPreferences preferences;

    DungeonSimulationControlStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean paused() {
        return preferences.getBoolean(KEY_PAUSED, false);
    }

    void setPaused(boolean paused) {
        preferences.edit().putBoolean(KEY_PAUSED, paused).apply();
    }

    int speedIndex() {
        int value = preferences.getInt(KEY_SPEED, 1);
        return Math.max(0, Math.min(INTERVALS_MS.length - 1, value));
    }

    void setSpeedIndex(int speedIndex) {
        preferences.edit().putInt(KEY_SPEED, Math.max(0, Math.min(INTERVALS_MS.length - 1, speedIndex))).apply();
    }

    long intervalMs() {
        return INTERVALS_MS[speedIndex()];
    }

    boolean foregroundDue(String npcId, long nowMs) {
        if (paused() || nowMs <= 0L) return false;
        String id = NpcId.of(npcId).value();
        long last = preferences.getLong(KEY_LAST_PREFIX + id, 0L);
        return last <= 0L || nowMs - last >= intervalMs() || nowMs < last;
    }

    void markAdvanced(String npcId, long nowMs) {
        if (nowMs <= 0L) return;
        String id = NpcId.of(npcId).value();
        preferences.edit().putLong(KEY_LAST_PREFIX + id, nowMs).apply();
    }
}
