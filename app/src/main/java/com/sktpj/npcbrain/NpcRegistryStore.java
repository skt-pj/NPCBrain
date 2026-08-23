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

    private final Context appContext;
    private final SharedPreferences preferences;

    NpcRegistryStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensure("npc1");
        ensure("npc2");
    }

    synchronized List<String> npcIds() {
        return normalize(readIds());
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
        List<String> ids = normalize(readIds());
        if (ids.contains(id)) return;
        ids.add(id);
        write(ids);
    }

    synchronized String createNpcId() {
        List<String> ids = npcIds();
        String id = nextNpcId(ids);
        ids.add(id);
        write(ids);
        NPCBrainApplication.requestDemoRoomRefresh();
        return id;
    }

    static String nextNpcId(List<String> existing) {
        Set<String> ids = new LinkedHashSet<>();
        if (existing != null) {
            for (String id : existing) {
                try {
                    ids.add(NpcId.of(id).value());
                } catch (Exception ignored) {
                }
            }
        }
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

    private List<String> readIds() {
        List<String> result = new ArrayList<>();
        String raw = preferences.getString(IDS, "");
        if (raw == null || raw.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "").trim();
                if (!id.isEmpty()) result.add(id);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void write(List<String> ids) {
        JSONArray array = new JSONArray();
        for (String id : normalize(ids)) array.put(id);
        preferences.edit().putString(IDS, array.toString()).commit();
    }
}
