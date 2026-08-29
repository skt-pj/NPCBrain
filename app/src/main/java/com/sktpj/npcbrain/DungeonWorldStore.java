package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Application-wide canonical store for the one shared dungeon world. */
final class DungeonWorldStore {
    private static final String PREFS = "npcbrain_dungeon_world_v0440";
    private static final String FLOOR_PREFIX = "floor_";

    private final SharedPreferences preferences;

    DungeonWorldStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    DungeonSharedFloor load(int floor) {
        String raw = preferences.getString(key(floor), "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return DungeonSharedFloor.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    boolean save(DungeonSharedFloor floor) {
        if (floor == null) return false;
        return preferences.edit()
                .putString(key(floor.floor), floor.toJson().toString())
                .commit();
    }

    private static String key(int floor) {
        return FLOOR_PREFIX + Math.max(1, floor);
    }
}
