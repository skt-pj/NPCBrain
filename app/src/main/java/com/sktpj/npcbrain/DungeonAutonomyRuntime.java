package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class DungeonAutonomyRuntime {
    private static final String PREFS = "npcbrain_dungeon_autonomy_v043";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final NpcRegistryStore registryStore;
    private final DungeonPresenceStore presenceStore;
    private final DungeonStore dungeonStore;

    DungeonAutonomyRuntime(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registryStore = new NpcRegistryStore(appContext);
        presenceStore = new DungeonPresenceStore(appContext);
        dungeonStore = new DungeonStore(appContext);
    }

    synchronized int evaluateAndEnter(long nowMs) {
        int entered = 0;
        long bucket = DungeonAutonomyPolicy.dayBucket(nowMs);
        for (String npcId : registryStore.activeNpcIds()) {
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
            if (character.isDead() || presenceStore.isPresent(npcId)) continue;
            if (preferences.getLong(dayKey(npcId), -1L) == bucket) continue;
            preferences.edit().putLong(dayKey(npcId), bucket).commit();

            DungeonParticipationState participation =
                    DungeonParticipationStore.forNpc(appContext, npcId).load();
            DungeonPersonalityPolicy.Traits traits = new DungeonPersonalityPolicy.Traits(
                    character.traitPercent(CharacterStateStore.extraversionKey()),
                    character.traitPercent(CharacterStateStore.neuroticismKey()),
                    character.traitPercent(CharacterStateStore.agreeablenessKey()),
                    character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                    character.traitPercent(CharacterStateStore.opennessKey()));
            if (!DungeonAutonomyPolicy.shouldSelfDive(
                    participation, traits, true, npcId, bucket)) continue;

            DungeonState state = dungeonStore.loadRaw(npcId);
            if (state == null) {
                long seed = System.nanoTime()
                        ^ nowMs
                        ^ ((long) npcId.hashCode() << 17);
                state = DungeonGenerator.generate(seed, 1);
                state.lastAction = "自分の意思で単独ダンジョン探索を開始";
                dungeonStore.save(npcId, state);
            }
            presenceStore.setPresent(npcId, true);
            entered++;
        }
        return entered;
    }

    /** Compatibility name retained for existing foreground/job call sites. */
    synchronized int evaluateAndJoin(long nowMs) {
        return evaluateAndEnter(nowMs);
    }

    private static String dayKey(String npcId) {
        return "last_day_" + NpcId.of(npcId).value();
    }
}
