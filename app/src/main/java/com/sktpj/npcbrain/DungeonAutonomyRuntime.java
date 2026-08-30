package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class DungeonAutonomyRuntime {
    private static final String PREFS = "npcbrain_dungeon_autonomy_v042";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final NpcRegistryStore registryStore;
    private final DungeonRosterStore rosterStore;

    DungeonAutonomyRuntime(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registryStore = new NpcRegistryStore(appContext);
        rosterStore = new DungeonRosterStore(appContext);
    }

    synchronized int evaluateAndJoin(long nowMs) {
        List<String> roster = new ArrayList<>(rosterStore.activeNpcIds());
        int joined = 0;
        long bucket = DungeonAutonomyPolicy.dayBucket(nowMs);
        for (String npcId : registryStore.activeNpcIds()) {
            if (roster.size() >= DungeonRosterPolicy.MAX_ACTIVE) break;
            if (roster.contains(npcId)) continue;
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
            if (character.isDead()) continue;
            if (preferences.getLong(dayKey(npcId), -1L) == bucket) continue;
            preferences.edit().putLong(dayKey(npcId), bucket).commit();

            DungeonParticipationStore participationStore = DungeonParticipationStore.forNpc(appContext, npcId);
            DungeonParticipationState participation = participationStore.load();
            DungeonPersonalityPolicy.Traits traits = new DungeonPersonalityPolicy.Traits(
                    character.traitPercent(CharacterStateStore.extraversionKey()),
                    character.traitPercent(CharacterStateStore.neuroticismKey()),
                    character.traitPercent(CharacterStateStore.agreeablenessKey()),
                    character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                    character.traitPercent(CharacterStateStore.opennessKey()));
            if (!DungeonAutonomyPolicy.shouldSelfJoin(
                    participation, traits, true, roster.size(), npcId, bucket)) continue;

            if (!participation.isAccepted()) {
                participationStore.save(new DungeonParticipationState(
                        DungeonParticipationState.ACCEPT,
                        Math.max(0.62, participation.willingness),
                        participation.fear,
                        Math.max(0.58, participation.resolve),
                        "自分の意思でダンジョンへ行くことにした",
                        nowMs));
            }
            roster.add(npcId);
            rosterStore.save(roster);
            joined++;
        }
        if (!roster.isEmpty()) {
            new DungeonPartyCoordinator(appContext).reconcile(roster.get(0));
        }
        return joined;
    }

    private static String dayKey(String npcId) {
        return "last_day_" + NpcId.of(npcId).value();
    }
}
