package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class DungeonRosterStore {
    private static final String PREFS = "npcbrain_dungeon_roster_v1";
    private static final String ACTIVE = "active_npc_ids";
    private static final String INITIALIZED = "initialized";
    private static final String USER_PARTY_043 = "user_party_043_migrated";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final NpcRegistryStore registry;

    DungeonRosterStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registry = new NpcRegistryStore(appContext);
        migrateToUserOnlyParty();
    }

    synchronized List<String> activeNpcIds() {
        List<String> activeRegistry = registry.activeNpcIds();
        List<String> requested = readIds();
        List<String> normalized;
        if (!preferences.getBoolean(INITIALIZED, false)) {
            normalized = DungeonRosterPolicy.initial(activeRegistry);
            persist(normalized, true);
            return normalized;
        }
        normalized = DungeonRosterPolicy.normalize(requested, activeRegistry);
        if (!normalized.equals(requested)) persist(normalized, true);
        return normalized;
    }

    synchronized List<String> candidates() {
        return new ArrayList<>(registry.activeNpcIds());
    }

    synchronized List<String> save(List<String> requested) {
        List<String> normalized = DungeonRosterPolicy.normalize(requested, registry.activeNpcIds());
        persist(normalized, true);
        DungeonPresenceStore presence = new DungeonPresenceStore(appContext);
        for (String npcId : normalized) presence.setPresent(npcId, true);
        return normalized;
    }

    private void migrateToUserOnlyParty() {
        if (preferences.getBoolean(USER_PARTY_043, false)) return;
        preferences.edit()
                .putString(ACTIVE, "[]")
                .putBoolean(INITIALIZED, true)
                .putBoolean(USER_PARTY_043, true)
                .commit();
    }

    private List<String> readIds() {
        List<String> result = new ArrayList<>();
        String raw = preferences.getString(ACTIVE, "");
        if (raw == null || raw.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty()) result.add(value);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void persist(List<String> ids, boolean initialized) {
        JSONArray array = new JSONArray();
        if (ids != null) {
            for (String id : ids) array.put(id);
        }
        preferences.edit()
                .putString(ACTIVE, array.toString())
                .putBoolean(INITIALIZED, initialized)
                .commit();
    }
}
