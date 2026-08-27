package com.sktpj.npcbrain;

final class DungeonViewportPolicy {
    // Keep the board still through normal movement; follow only when the player reaches an edge cell.
    private static final int EDGE_MARGIN_CELLS = 1;

    private DungeonViewportPolicy() {
    }

    static int initialStart(int player, int viewportSize, int mapSize) {
        int maxStart = Math.max(0, mapSize - viewportSize);
        return clamp(player - viewportSize / 2, 0, maxStart);
    }

    static int followStart(int currentStart, int player, int viewportSize, int mapSize) {
        int maxStart = Math.max(0, mapSize - viewportSize);
        int start = clamp(currentStart, 0, maxStart);
        if (viewportSize <= 1 || mapSize <= viewportSize) return 0;

        int margin = Math.min(EDGE_MARGIN_CELLS, Math.max(0, (viewportSize - 1) / 2));
        int minPlayer = start + margin;
        int maxPlayer = start + viewportSize - 1 - margin;
        if (player < minPlayer) {
            start = player - margin;
        } else if (player > maxPlayer) {
            start = player - (viewportSize - 1 - margin);
        }
        return clamp(start, 0, maxStart);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
