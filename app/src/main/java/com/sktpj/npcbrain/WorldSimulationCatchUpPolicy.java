package com.sktpj.npcbrain;

/** Pure timing policy for screen-independent world simulation catch-up. */
final class WorldSimulationCatchUpPolicy {
    static final long BACKGROUND_STEP_MS = 60L * 60L * 1000L;
    static final int MAX_BACKGROUND_STEPS = 24;

    private WorldSimulationCatchUpPolicy() {}

    static int backgroundSteps(long previousCheckpointMs, long nowMs) {
        if (nowMs <= 0L) return 0;
        if (previousCheckpointMs <= 0L) return 1;
        if (nowMs <= previousCheckpointMs) return 0;
        long elapsed = nowMs - previousCheckpointMs;
        long steps = Math.max(1L, elapsed / BACKGROUND_STEP_MS);
        return (int) Math.min(MAX_BACKGROUND_STEPS, steps);
    }

    static long nextCheckpoint(long previousCheckpointMs, long nowMs) {
        if (nowMs <= 0L) return Math.max(0L, previousCheckpointMs);
        return Math.max(previousCheckpointMs, nowMs);
    }
}
