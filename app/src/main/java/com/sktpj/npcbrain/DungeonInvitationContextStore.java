package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class DungeonInvitationContextStore {
    private static final String PREFS = "npcbrain_dungeon_invitation_v1";
    private static final String CURRENT = "current_invitation";

    private final SharedPreferences preferences;

    DungeonInvitationContextStore(Context storageContext) {
        preferences = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void save(DungeonInvitationContext context) {
        if (context == null || context.npcId.isEmpty()) return;
        preferences.edit().putString(CURRENT, context.toJson().toString()).commit();
    }

    synchronized DungeonInvitationContext load() {
        String raw = preferences.getString(CURRENT, "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return DungeonInvitationContext.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized JSONObject snapshotJson() {
        DungeonInvitationContext context = load();
        return context == null ? new JSONObject() : context.toJson();
    }
}
