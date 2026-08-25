package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.List;

final class DungeonCognitionGate {
    static final int PERIODIC_TURNS = 12;

    static final String FLOOR_START = "floor_start";
    static final String ENEMY_SPOTTED = "enemy_spotted";
    static final String STAIRS_SPOTTED = "stairs_spotted";
    static final String HP_RISK = "hp_risk";
    static final String COMBAT_CHANGE = "combat_change";
    static final String TACTICAL_EXPIRED = "tactical_intent_expired";
    static final String PERIODIC = "periodic";

    static final String OBJECTIVE_CHANGED = "objective_changed";
    static final String PROGRESS_STALLED = "progress_stalled";

    static final class Signal {
        final int floor;
        final int turn;
        final String hpBand;
        final List<String> visibleEnemyIds;
        final boolean stairsKnown;
        final boolean combatAdjacent;

        Signal(
                int floor,
                int turn,
                String hpBand,
                List<String> visibleEnemyIds,
                boolean stairsKnown,
                boolean combatAdjacent
        ) {
            this.floor = Math.max(1, floor);
            this.turn = Math.max(0, turn);
            this.hpBand = hpBand == null ? "normal" : hpBand;
            this.visibleEnemyIds = visibleEnemyIds == null
                    ? new ArrayList<>() : new ArrayList<>(visibleEnemyIds);
            this.visibleEnemyIds.sort(String::compareTo);
            this.stairsKnown = stairsKnown;
            this.combatAdjacent = combatAdjacent;
        }
    }

    private DungeonCognitionGate() {
    }

    static Signal snapshot(DungeonState state) {
        if (state == null) {
            return new Signal(1, 0, "critical", new ArrayList<>(), false, false);
        }
        return new Signal(
                state.floor,
                state.turn,
                hpBand(state),
                DungeonPerception.visibleEnemyIds(state),
                DungeonPerception.stairsKnown(state),
                DungeonPerception.adjacentVisibleEnemy(state));
    }

    /**
     * Detects changes worth another Brain evaluation. HP bands are salience buckets only: they
     * decide when to ask Brain again and never select EVADE, WITHDRAW, or any other action.
     */
    static String reason(Signal previous, Signal current, int lastBrainTurn) {
        if (current == null) return "";
        if (previous == null || current.floor != previous.floor) return FLOOR_START;
        if (containsNew(current.visibleEnemyIds, previous.visibleEnemyIds)) return ENEMY_SPOTTED;
        if (current.stairsKnown && !previous.stairsKnown) return STAIRS_SPOTTED;
        if (!current.hpBand.equals(previous.hpBand)) return HP_RISK;
        if (current.combatAdjacent != previous.combatAdjacent) return COMBAT_CHANGE;
        if (current.turn - Math.max(0, lastBrainTurn) >= PERIODIC_TURNS) return PERIODIC;
        return "";
    }

    static boolean isStrategyTrigger(String reason) {
        return OBJECTIVE_CHANGED.equals(reason) || PROGRESS_STALLED.equals(reason);
    }

    static boolean isCognitionTrigger(String reason) {
        return OBJECTIVE_CHANGED.equals(reason)
                || PROGRESS_STALLED.equals(reason)
                || FLOOR_START.equals(reason)
                || ENEMY_SPOTTED.equals(reason)
                || STAIRS_SPOTTED.equals(reason)
                || HP_RISK.equals(reason)
                || COMBAT_CHANGE.equals(reason)
                || PERIODIC.equals(reason);
    }

    static String mergePending(String current, String incoming) {
        String safeCurrent = current == null ? "" : current;
        String safeIncoming = incoming == null ? "" : incoming;
        if (safeIncoming.isEmpty()) return safeCurrent;
        if (safeCurrent.isEmpty()) return safeIncoming;
        return priority(safeIncoming) < priority(safeCurrent) ? safeIncoming : safeCurrent;
    }

    private static int priority(String reason) {
        if (OBJECTIVE_CHANGED.equals(reason)) return 0;
        if (PROGRESS_STALLED.equals(reason)) return 1;
        if (FLOOR_START.equals(reason)) return 2;
        if (HP_RISK.equals(reason)) return 3;
        if (ENEMY_SPOTTED.equals(reason)) return 4;
        if (STAIRS_SPOTTED.equals(reason)) return 5;
        if (COMBAT_CHANGE.equals(reason)) return 6;
        if (PERIODIC.equals(reason)) return 7;
        if (TACTICAL_EXPIRED.equals(reason)) return 8;
        return 9;
    }

    private static boolean containsNew(List<String> current, List<String> previous) {
        for (String id : current) if (!previous.contains(id)) return true;
        return false;
    }

    private static String hpBand(DungeonState state) {
        double ratio = state.maxHp <= 0 ? 0.0 : state.hp / (double) state.maxHp;
        if (ratio <= 0.25) return "critical";
        if (ratio <= 0.50) return "low";
        return "normal";
    }
}
