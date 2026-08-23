package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class DungeonParticipationStore {
    private static final String PREFS = "npcbrain_dungeon_participation_v1";
    private static final String STATE = "state";

    private final SharedPreferences preferences;

    DungeonParticipationStore(Context storageContext) {
        preferences = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized DungeonParticipationState load() {
        String raw = preferences.getString(STATE, "");
        if (raw == null || raw.trim().isEmpty()) return DungeonParticipationState.initial();
        try {
            return DungeonParticipationState.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return DungeonParticipationState.initial();
        }
    }

    synchronized void save(DungeonParticipationState state) {
        DungeonParticipationState safe = state == null ? DungeonParticipationState.initial() : state;
        preferences.edit().putString(STATE, safe.toJson().toString()).apply();
    }

    static DungeonParticipationStore forNpc(Context context, String npcId) {
        return new DungeonParticipationStore(NpcContexts.storage(context, npcId));
    }
}
