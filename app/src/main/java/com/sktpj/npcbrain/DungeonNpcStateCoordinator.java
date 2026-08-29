package com.sktpj.npcbrain;

import android.content.Context;

import java.util.List;

/** Canonical read model for normal dungeon-area execution state. */
final class DungeonNpcStateCoordinator {
    private final NpcRegistryStore registryStore;
    private final DungeonObjectiveStore objectiveStore;
    private final DungeonStore dungeonStore;

    DungeonNpcStateCoordinator(Context context) {
        Context appContext = context.getApplicationContext();
        registryStore = new NpcRegistryStore(appContext);
        objectiveStore = new DungeonObjectiveStore(appContext);
        dungeonStore = new DungeonStore(appContext);
    }

    Snapshot snapshot(String npcId) {
        final String id;
        try {
            id = NpcId.of(npcId).value();
        } catch (Exception ignored) {
            return Snapshot.invalid();
        }
        List<String> activeNpcIds = registryStore.activeNpcIds();
        boolean active = activeNpcIds.contains(id);
        DungeonObjective objective = objectiveStore.load(id);
        if (objective == null) objective = DungeonObjective.none();
        DungeonState state = dungeonStore.load(id);
        return new Snapshot(
                id,
                active,
                objective,
                state,
                canAdvanceSnapshot(active, objective, state));
    }

    boolean canAdvance(String npcId) {
        return snapshot(npcId).canAdvance;
    }

    static boolean canAdvanceSnapshot(
            boolean active,
            DungeonObjective objective,
            DungeonState state
    ) {
        if (!active) return false;
        if (state != null && state.hp <= 0) return false;
        DungeonObjective safeObjective = objective == null ? DungeonObjective.none() : objective;
        return state == null
                || !safeObjective.isActive()
                || !safeObjective.isComplete(state.floor);
    }

    static final class Snapshot {
        final String npcId;
        final boolean active;
        final DungeonObjective objective;
        final DungeonState state;
        final boolean canAdvance;

        Snapshot(
                String npcId,
                boolean active,
                DungeonObjective objective,
                DungeonState state,
                boolean canAdvance
        ) {
            this.npcId = npcId == null ? "" : npcId;
            this.active = active;
            this.objective = objective == null ? DungeonObjective.none() : objective;
            this.state = state;
            this.canAdvance = canAdvance;
        }

        private static Snapshot invalid() {
            return new Snapshot("", false, DungeonObjective.none(), null, false);
        }
    }
}
