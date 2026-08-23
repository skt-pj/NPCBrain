package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class DungeonObjectiveStore {
    private static final String PREFS = "npcbrain_dungeon_objective_v1";
    private final SharedPreferences preferences;

    DungeonObjectiveStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized DungeonObjective load(String npcId) {
        String raw = preferences.getString(key(npcId), "");
        if (raw == null || raw.trim().isEmpty()) return DungeonObjective.none();
        try {
            return DungeonObjective.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return DungeonObjective.none();
        }
    }

    synchronized void save(String npcId, DungeonObjective objective) {
        DungeonObjective safe = objective == null ? DungeonObjective.none() : objective;
        preferences.edit().putString(key(npcId), safe.toJson().toString()).apply();
    }

    static String key(String npcId) {
        return "npc2".equals(npcId) ? "npc2_objective" : "npc1_objective";
    }
}
