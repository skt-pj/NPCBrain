package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/**
 * UI-only observation focus shared by all primary tabs.
 *
 * This never changes world state. It only remembers which canonical NPC the user is looking at so
 * moving between tabs keeps the same person in view.
 */
final class WorldFocusStoreV212 {
    private static final String PREFS = "npcbrain_world_ui_focus_v212";
    private static final String KEY_NPC = "focused_npc";

    private final SharedPreferences preferences;

    WorldFocusStoreV212(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String focusedNpcId(List<String> activeNpcIds) {
        String stored = preferences.getString(KEY_NPC, "");
        if (stored != null && activeNpcIds != null && activeNpcIds.contains(stored)) return stored;
        if (activeNpcIds == null || activeNpcIds.isEmpty()) return "";
        String fallback = activeNpcIds.get(0);
        preferences.edit().putString(KEY_NPC, fallback).apply();
        return fallback;
    }

    void select(String npcId) {
        String id;
        try {
            id = NpcId.of(npcId).value();
        } catch (Exception ignored) {
            return;
        }
        preferences.edit().putString(KEY_NPC, id).apply();
    }
}
