package com.sktpj.npcbrain;

final class DungeonWalkAnimationPolicy {
    static final int DOWN = 0;
    static final int LEFT = 1;
    static final int RIGHT = 2;
    static final int UP = 3;
    static final long WALK_DURATION_MS = 240L;

    private DungeonWalkAnimationPolicy() {
    }

    static int directionRow(int dx, int dy, int lastRow) {
        if (dx == 0 && dy == 1) return DOWN;
        if (dx == -1 && dy == 0) return LEFT;
        if (dx == 1 && dy == 0) return RIGHT;
        if (dx == 0 && dy == -1) return UP;
        return normalizeRow(lastRow);
    }

    static boolean isSingleStep(boolean sameState, boolean sameFloor, int dx, int dy) {
        return sameState && sameFloor && Math.abs(dx) + Math.abs(dy) == 1;
    }

    static float progress(long elapsedMs) {
        if (elapsedMs <= 0L) return 0f;
        if (elapsedMs >= WALK_DURATION_MS) return 1f;
        return elapsedMs / (float) WALK_DURATION_MS;
    }

    static int frameIndex(long elapsedMs) {
        if (elapsedMs < 0L || elapsedMs >= WALK_DURATION_MS) return 0;
        return Math.min(3, (int) ((elapsedMs * 4L) / WALK_DURATION_MS));
    }

    static boolean isActive(long elapsedMs) {
        return elapsedMs >= 0L && elapsedMs < WALK_DURATION_MS;
    }

    private static int normalizeRow(int row) {
        return row >= DOWN && row <= UP ? row : DOWN;
    }
}
