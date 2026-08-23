package com.sktpj.npcbrain;

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
        return DungeonPersonalityPolicy.knownPathDistance(
                state,
                state.playerX,
                state.playerY,
                state.stairsX(),
                state.stairsY());
    }
}
