package com.sktpj.npcbrain;

import java.util.ArrayDeque;
import java.util.Queue;

final class DungeonProgressMonitor {
    static final int STALL_TURNS = 48;
    static final int REPLAN_COOLDOWN_TURNS = 96;

    static final class Snapshot {
        final int floor;
        final int turn;
        final int exploredCellCount;
        final int knownStairPathDistance;
        final int lastProgressTurn;

        Snapshot(
                int floor,
                int turn,
                int exploredCellCount,
                int knownStairPathDistance,
                int lastProgressTurn
        ) {
            this.floor = Math.max(1, floor);
            this.turn = Math.max(0, turn);
            this.exploredCellCount = Math.max(0, exploredCellCount);
            this.knownStairPathDistance = knownStairPathDistance < 0 ? 999 : knownStairPathDistance;
            this.lastProgressTurn = Math.max(0, lastProgressTurn);
        }
    }

    static final class Result {
        final Snapshot snapshot;
        final boolean progressed;
        final boolean shouldReplan;

        Result(Snapshot snapshot, boolean progressed, boolean shouldReplan) {
            this.snapshot = snapshot;
            this.progressed = progressed;
            this.shouldReplan = shouldReplan;
        }
    }

    private DungeonProgressMonitor() {
    }

    static Snapshot initial(DungeonState state) {
        if (state == null) return new Snapshot(1, 0, 0, 999, 0);
        return new Snapshot(
                state.floor,
                state.turn,
                exploredCellCount(state),
                knownStairPathDistance(state),
                state.turn);
    }

    static Result observe(Snapshot previous, DungeonState state, int lastBrainPlanTurn) {
        Snapshot base = previous == null ? initial(state) : previous;
        if (state == null) return new Result(base, false, false);
        int explored = exploredCellCount(state);
        int stairDistance = knownStairPathDistance(state);
        boolean floorProgress = state.floor > base.floor;
        boolean explorationProgress = explored > base.exploredCellCount;
        boolean stairProgress = stairDistance < base.knownStairPathDistance;
        boolean progressed = floorProgress || explorationProgress || stairProgress;
        int lastProgressTurn = progressed ? state.turn : base.lastProgressTurn;
        Snapshot next = new Snapshot(
                state.floor,
                state.turn,
                explored,
                stairDistance,
                lastProgressTurn);
        if (state.hp <= 0) return new Result(next, progressed, false);
        boolean stalled = state.turn - lastProgressTurn >= STALL_TURNS;
        boolean cooldownReady = lastBrainPlanTurn < 0
                || state.turn - lastBrainPlanTurn >= REPLAN_COOLDOWN_TURNS;
        return new Result(next, progressed, stalled && cooldownReady);
    }

    static int exploredCellCount(DungeonState state) {
        if (state == null) return 0;
        int count = 0;
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                if (state.visited[y][x]) count++;
            }
        }
        return count;
    }

    static int knownStairPathDistance(DungeonState state) {
        if (state == null || !DungeonPerception.stairsKnown(state)) return 999;
        int targetX = state.stairsX();
        int targetY = state.stairsY();
        if (!knownTraversable(state, targetX, targetY)) return 999;

        boolean[][] seen = new boolean[state.height][state.width];
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{state.playerX, state.playerY, 0});
        seen[state.playerY][state.playerX] = true;
        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            if (point[0] == targetX && point[1] == targetY) return point[2];
            for (int[] direction : directions) {
                int nx = point[0] + direction[0];
                int ny = point[1] + direction[1];
                if (!knownTraversable(state, nx, ny) || seen[ny][nx]) continue;
                seen[ny][nx] = true;
                queue.add(new int[]{nx, ny, point[2] + 1});
            }
        }
        return 999;
    }

    private static boolean knownTraversable(DungeonState state, int x, int y) {
        if (state == null || !state.inside(x, y)) return false;
        if (x == state.playerX && y == state.playerY) return true;
        return state.visited[y][x] && state.walkable(x, y);
    }
}
