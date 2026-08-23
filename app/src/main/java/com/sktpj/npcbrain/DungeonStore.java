package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class DungeonStore {
    private static final String PREFS = "npcbrain_dungeon_v1";
    private final Context appContext;
    private final SharedPreferences preferences;

    DungeonStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized DungeonState load(String npcId) {
        String raw = preferences.getString(key(npcId), "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return DungeonState.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized void save(String npcId, DungeonState state) {
        if (state == null) return;
        if (state.hp <= 0) {
            new NpcArchiveStore(appContext).archiveDeath(npcId, state);
        }
        preferences.edit().putString(key(npcId), state.toJson().toString()).apply();
    }

    static String key(String npcId) {
        String id = NpcId.of(npcId).value();
        if ("npc1".equals(id)) return "npc1_state";
        if ("npc2".equals(id)) return "npc2_state";
        return id + "_state";
    }
}
