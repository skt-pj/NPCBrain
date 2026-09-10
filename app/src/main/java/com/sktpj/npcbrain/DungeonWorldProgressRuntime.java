package com.sktpj.npcbrain;

import android.content.Context;

/** Advances every present dungeon NPC without depending on a Dungeon screen. */
final class DungeonWorldProgressRuntime {
    private final Context appContext;
    private final DungeonPresenceStore presence;
    private final DungeonStore dungeon;
    private final DungeonEconomyRuntime economy;
    private final DungeonNpcStateCoordinator npcState;
    private final DungeonSimulationControlStore controls;

    DungeonWorldProgressRuntime(Context context) {
        appContext = context.getApplicationContext();
        presence = new DungeonPresenceStore(appContext);
        dungeon = new DungeonStore(appContext);
        economy = new DungeonEconomyRuntime(appContext);
        npcState = new DungeonNpcStateCoordinator(appContext);
        controls = new DungeonSimulationControlStore(appContext);
    }

    int advancePresentOnce(long nowMs) {
        return advancePresent(nowMs, false);
    }

    int advancePresentCatchUpOnce(long simulatedTimeMs) {
        return advancePresent(simulatedTimeMs, true);
    }

    private int advancePresent(long nowMs, boolean bypassForegroundInterval) {
        if (controls.paused()) return 0;
        int advanced = 0;
        for (String npcId : presence.activePresentNpcIds()) {
            if (!npcState.canAdvance(npcId)) continue;
            if (!bypassForegroundInterval && !controls.foregroundDue(npcId, nowMs)) continue;
            if (step(npcId, nowMs)) {
                controls.markAdvanced(npcId, nowMs);
                advanced++;
            }
        }
        return advanced;
    }

    private boolean step(String npcId, long nowMs) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
        if (character.isDead()) {
            presence.setPresent(npcId, false);
            return false;
        }

        DungeonState state = dungeon.load(npcId);
        if (state == null) {
            long seed = System.nanoTime() ^ nowMs ^ ((long) npcId.hashCode() << 17);
            state = DungeonGenerator.generate(seed, 1);
            state.lastAction = "世界ランタイムでダンジョン探索を開始";
            dungeon.save(npcId, state);
        }
        if (state.hp <= 0) {
            presence.setPresent(npcId, false);
            return false;
        }

        DungeonObjective objective = new DungeonObjectiveStore(appContext).load(npcId);
        if (objective == null) objective = DungeonObjective.none();
        if (objective.isActive() && objective.isComplete(state.floor)) return false;

        DungeonPersonalityPolicy.Traits traits = new DungeonPersonalityPolicy.Traits(
                character.traitPercent(CharacterStateStore.extraversionKey()),
                character.traitPercent(CharacterStateStore.neuroticismKey()),
                character.traitPercent(CharacterStateStore.agreeablenessKey()),
                character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                character.traitPercent(CharacterStateStore.opennessKey()));
        DungeonMindStore.Snapshot mind = new DungeonMindStore(appContext).load(npcId);
        DungeonPlan plan = mind == null ? null : mind.plan;
        if (plan == null || !plan.matches(objective)) {
            plan = DungeonPlan.local(objective, traits, state, "統合世界ランタイムのローカル計画");
        }
        DungeonIntent intent = worldTurnIntent(state, traits, mind);
        DungeonStepResult result = DungeonEngine.stepDetailed(state, traits, intent, plan);
        DungeonState next = result == null || result.state == null ? state : result.state;
        DungeonPerception.refreshExploration(next);
        dungeon.save(npcId, next);
        economy.process(npcId, next, nowMs);
        if (next.hp <= 0) presence.setPresent(npcId, false);
        return next.turn != state.turn || next.floor != state.floor || next.hp != state.hp
                || !safe(next.lastAction).equals(safe(state.lastAction));
    }

    static DungeonIntent worldTurnIntent(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonMindStore.Snapshot mind
    ) {
        if (state != null && mind != null && mind.intent != null
                && mind.intent.isBrain()
                && mind.intent.floor == state.floor
                && mind.intent.turn == state.turn) {
            return mind.intent;
        }
        return DungeonIntent.localFallback(state, traits, "統合世界ランタイムでBrain persistent planを合法実行");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
