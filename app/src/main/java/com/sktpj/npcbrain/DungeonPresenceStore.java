package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Dungeon presence is independent from user-invited party membership. */
final class DungeonPresenceStore {
    private static final String PREFS = "npcbrain_dungeon_presence_v043";
    private static final String PRESENT = "present_npc_ids";
    private static final String INITIALIZED = "initialized";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final NpcRegistryStore registry;

    DungeonPresenceStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registry = new NpcRegistryStore(appContext);
    }

    synchronized List<String> activePresentNpcIds() {
        ensureInitialized();
        List<String> active = registry.activeNpcIds();
        Set<String> allowed = new LinkedHashSet<>(active);
        List<String> normalized = new ArrayList<>();
        for (String raw : readIds()) {
            try {
                String id = NpcId.of(raw).value();
                if (allowed.contains(id) && !normalized.contains(id)) normalized.add(id);
            } catch (Exception ignored) {
            }
        }
        if (!normalized.equals(readIds())) persist(normalized);
        return normalized;
    }

    synchronized boolean isPresent(String npcId) {
        String id = NpcId.of(npcId).value();
        return activePresentNpcIds().contains(id);
    }

    synchronized void setPresent(String npcId, boolean present) {
        ensureInitialized();
        String id = NpcId.of(npcId).value();
        List<String> ids = activePresentNpcIds();
        if (present) {
            if (registry.activeNpcIds().contains(id) && !ids.contains(id)) ids.add(id);
        } else {
            ids.remove(id);
        }
        persist(ids);
    }

    private void ensureInitialized() {
        if (preferences.getBoolean(INITIALIZED, false)) return;
        List<String> initial = new ArrayList<>();
        DungeonStore dungeonStore = new DungeonStore(appContext);
        for (String npcId : registry.activeNpcIds()) {
            DungeonState state = dungeonStore.loadRaw(npcId);
            if (state != null && state.hp > 0) initial.add(npcId);
        }
        preferences.edit()
                .putString(PRESENT, toJson(initial))
                .putBoolean(INITIALIZED, true)
                .commit();
    }

    private List<String> readIds() {
        List<String> result = new ArrayList<>();
        String raw = preferences.getString(PRESENT, "");
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

    private void persist(List<String> ids) {
        preferences.edit()
                .putString(PRESENT, toJson(ids))
                .putBoolean(INITIALIZED, true)
                .commit();
    }

    private static String toJson(List<String> ids) {
        JSONArray array = new JSONArray();
        if (ids != null) {
            for (String id : ids) array.put(id);
        }
        return array.toString();
    }
}
