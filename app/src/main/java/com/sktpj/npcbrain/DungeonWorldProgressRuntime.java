package com.sktpj.npcbrain;

import android.content.Context;

import java.util.List;

/** Advances autonomous solo explorers without depending on DungeonActivity. */
final class DungeonWorldProgressRuntime {
    private final Context appContext;
    private final DungeonPresenceStore presence;
    private final DungeonRosterStore roster;
    private final DungeonStore dungeon;
    private final DungeonEconomyRuntime economy;

    DungeonWorldProgressRuntime(Context context) {
        appContext = context.getApplicationContext();
        presence = new DungeonPresenceStore(appContext);
        roster = new DungeonRosterStore(appContext);
        dungeon = new DungeonStore(appContext);
        economy = new DungeonEconomyRuntime(appContext);
    }

    void advanceSoloPresentOnce(long nowMs) {
        List<String> party = roster.activeNpcIds();
        for (String npcId : presence.activePresentNpcIds()) {
            if (party.contains(npcId)) continue;
            step(npcId, nowMs);
        }
    }

    private void step(String npcId, long nowMs) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
        if (character.isDead()) {
            presence.setPresent(npcId, false);
            return;
        }

        DungeonState state = dungeon.load(npcId);
        if (state == null) {
            long seed = System.nanoTime() ^ nowMs ^ ((long) npcId.hashCode() << 17);
            state = DungeonGenerator.generate(seed, 1);
            state.lastAction = "単独探索を開始";
            dungeon.save(npcId, state);
        }
        if (state.hp <= 0) {
            presence.setPresent(npcId, false);
            return;
        }

        DungeonObjective objective = new DungeonObjectiveStore(appContext).load(npcId);
        if (objective == null) objective = DungeonObjective.none();
        if (objective.isActive() && objective.isComplete(state.floor)) return;

        DungeonPersonalityPolicy.Traits traits = new DungeonPersonalityPolicy.Traits(
                character.traitPercent(CharacterStateStore.extraversionKey()),
                character.traitPercent(CharacterStateStore.neuroticismKey()),
                character.traitPercent(CharacterStateStore.agreeablenessKey()),
                character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                character.traitPercent(CharacterStateStore.opennessKey()));
        DungeonMindStore.Snapshot mind = new DungeonMindStore(appContext).load(npcId);
        DungeonPlan plan = mind == null ? null : mind.plan;
        if (plan == null || !plan.matches(objective)) {
            plan = DungeonPlan.local(objective, traits, state, "単独探索のローカル計画");
        }
        DungeonIntent intent = DungeonRosterBridge.backgroundTurnIntent(state, traits, mind);
        DungeonStepResult result = DungeonEngine.stepDetailed(state, traits, intent, plan);
        DungeonState next = result == null || result.state == null ? state : result.state;
        DungeonPerception.refreshExploration(next);
        dungeon.save(npcId, next);
        economy.process(npcId, next, nowMs);
        if (next.hp <= 0) presence.setPresent(npcId, false);
    }
}
