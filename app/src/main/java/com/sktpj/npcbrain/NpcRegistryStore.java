package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class NpcRegistryStore {
    private static final String PREFS = "npcbrain_npc_registry_v1";
    private static final String IDS = "npc_ids";
    private static final String REMOVED_IDS = "removed_npc_ids";

    private final Context appContext;
    private final SharedPreferences preferences;

    NpcRegistryStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensure("npc1");
        ensure("npc2");
    }

    synchronized List<String> npcIds() {
        Set<String> removed = new LinkedHashSet<>(readRemovedIds());
        List<String> result = new ArrayList<>();
        for (String id : normalize(readIds())) {
            if (!removed.contains(id)) result.add(id);
        }
        return result;
    }

    synchronized List<String> activeNpcIds() {
        List<String> result = new ArrayList<>();
        for (String id : npcIds()) {
            CharacterStateStore store = new CharacterStateStore(NpcContexts.storage(appContext, id));
            if (!store.isDead()) result.add(id);
        }
        return result;
    }

    synchronized boolean contains(String npcId) {
        String id = NpcId.of(npcId).value();
        return npcIds().contains(id);
    }

    synchronized void ensure(String npcId) {
        String id = NpcId.of(npcId).value();
        if (readRemovedIds().contains(id)) return;
        List<String> ids = normalize(readIds());
        if (ids.contains(id)) return;
        ids.add(id);
        writeIds(ids);
    }

    synchronized String createNpcId() {
        String id = nextNpcId();
        List<String> ids = npcIds();
        ids.add(id);
        writeIds(ids);
        NPCBrainApplication.requestDemoRoomRefresh();
        return id;
    }

    synchronized String nextNpcId() {
        return nextNpcId(npcIds(), readRemovedIds());
    }

    synchronized boolean removeNpc(String npcId) {
        String id = NpcId.of(npcId).value();
        if (!npcIds().contains(id)) return false;
        List<String> removed = readRemovedIds();
        if (!removed.contains(id)) removed.add(id);
        writeRemovedIds(removed);

        List<String> remaining = new ArrayList<>();
        for (String current : readIds()) {
            try {
                if (!id.equals(NpcId.of(current).value())) remaining.add(current);
            } catch (Exception ignored) {
            }
        }
        writeRawIds(remaining);
        NPCBrainApplication.requestDemoRoomRefresh();
        return true;
    }

    static String nextNpcId(List<String> existing) {
        return nextNpcId(existing, null);
    }

    static String nextNpcId(List<String> existing, List<String> removed) {
        Set<String> ids = new LinkedHashSet<>();
        addCanonical(ids, existing);
        addCanonical(ids, removed);
        for (int number = 3; number < Integer.MAX_VALUE; number++) {
            String candidate = "npc" + number;
            if (!ids.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("NPC ID space exhausted");
    }

    static List<String> normalize(List<String> raw) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        unique.add("npc1");
        unique.add("npc2");
        if (raw != null) {
            for (String value : raw) {
                try {
                    unique.add(NpcId.of(value).value());
                } catch (Exception ignored) {
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private static void addCanonical(Set<String> target, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            try {
                target.add(NpcId.of(value).value());
            } catch (Exception ignored) {
            }
        }
    }

    private List<String> readIds() {
        return readJsonList(IDS);
    }

    private List<String> readRemovedIds() {
        return readJsonList(REMOVED_IDS);
    }

    private List<String> readJsonList(String key) {
        List<String> result = new ArrayList<>();
        String raw = preferences.getString(key, "");
        if (raw == null || raw.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "").trim();
                if (id.isEmpty()) continue;
                try {
                    result.add(NpcId.of(id).value());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void writeIds(List<String> ids) {
        writeRawIds(normalize(ids));
    }

    private void writeRawIds(List<String> ids) {
        JSONArray array = new JSONArray();
        if (ids != null) {
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            addCanonical(unique, ids);
            for (String id : unique) array.put(id);
        }
        preferences.edit().putString(IDS, array.toString()).commit();
    }

    private void writeRemovedIds(List<String> ids) {
        JSONArray array = new JSONArray();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        addCanonical(unique, ids);
        for (String id : unique) array.put(id);
        preferences.edit().putString(REMOVED_IDS, array.toString()).commit();
    }
}
