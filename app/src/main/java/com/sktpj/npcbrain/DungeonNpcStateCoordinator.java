package com.sktpj.npcbrain;

import android.content.Context;

import java.util.List;

/**
 * Canonical read model for dungeon NPC execution state.
 *
 * Psychological decisions stay owned by the Brain and are persisted in
 * DungeonParticipationState. Android only applies the saved stance to execution.
 */
final class DungeonNpcStateCoordinator {
    private final Context appContext;
    private final NpcRegistryStore registryStore;
    private final DungeonObjectiveStore objectiveStore;
    private final DungeonStore dungeonStore;

    DungeonNpcStateCoordinator(Context context) {
        appContext = context.getApplicationContext();
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
        DungeonParticipationState participation =
                DungeonParticipationStore.forNpc(appContext, id).load();
        DungeonObjective objective = objectiveStore.load(id);
        if (objective == null) objective = DungeonObjective.none();
        DungeonState state = dungeonStore.load(id);
        return new Snapshot(
                id,
                active,
                participation,
                objective,
                state,
                canAdvanceSnapshot(active, participation, objective, state));
    }

    boolean canAdvance(String npcId) {
        return snapshot(npcId).canAdvance;
    }

    static boolean participationAllowsExecution(DungeonParticipationState participation) {
        return participation != null && participation.isAccepted();
    }

    static boolean canAdvanceSnapshot(
            boolean active,
            DungeonParticipationState participation,
            DungeonObjective objective,
            DungeonState state
    ) {
        if (!active || !participationAllowsExecution(participation)) return false;
        if (state != null && state.hp <= 0) return false;
        DungeonObjective safeObjective = objective == null ? DungeonObjective.none() : objective;
        return state == null
                || !safeObjective.isActive()
                || !safeObjective.isComplete(state.floor);
    }

    static final class Snapshot {
        final String npcId;
        final boolean active;
        final DungeonParticipationState participation;
        final DungeonObjective objective;
        final DungeonState state;
        final boolean canAdvance;

        Snapshot(
                String npcId,
                boolean active,
                DungeonParticipationState participation,
                DungeonObjective objective,
                DungeonState state,
                boolean canAdvance
        ) {
            this.npcId = npcId == null ? "" : npcId;
            this.active = active;
            this.participation = participation == null
                    ? DungeonParticipationState.initial() : participation;
            this.objective = objective == null ? DungeonObjective.none() : objective;
            this.state = state;
            this.canAdvance = canAdvance;
        }

        boolean participationAccepted() {
            return participationAllowsExecution(participation);
        }

        private static Snapshot invalid() {
            return new Snapshot(
                    "",
                    false,
                    DungeonParticipationState.initial(),
                    DungeonObjective.none(),
                    null,
                    false);
        }
    }
}
