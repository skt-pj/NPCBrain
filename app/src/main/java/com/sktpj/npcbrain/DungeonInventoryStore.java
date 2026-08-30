package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class DungeonInventoryStore {
    private static final String PREFS = "npcbrain_dungeon_inventory_v042";
    private static final int MAX_ITEMS = 200;

    private final SharedPreferences preferences;

    DungeonInventoryStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<DungeonItem> load(String npcId) {
        String id = NpcId.of(npcId).value();
        String raw = preferences.getString(key(id), "");
        List<DungeonItem> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                DungeonItem item = DungeonItem.fromJson(array.optJSONObject(i));
                if (item != null) result.add(item);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    synchronized boolean addIfAbsent(String npcId, DungeonItem item) {
        if (item == null) return false;
        String id = NpcId.of(npcId).value();
        List<DungeonItem> items = load(id);
        for (DungeonItem existing : items) {
            if (existing.itemId.equals(item.itemId)) return false;
        }
        items.add(item);
        if (items.size() > MAX_ITEMS) {
            items = new ArrayList<>(items.subList(items.size() - MAX_ITEMS, items.size()));
        }
        persist(id, items);
        return true;
    }

    synchronized DungeonItem latest(String npcId) {
        List<DungeonItem> items = load(npcId);
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    synchronized long totalAppraisedValue(String npcId) {
        long total = 0L;
        for (DungeonItem item : load(npcId)) {
            if (Long.MAX_VALUE - total < item.value) return Long.MAX_VALUE;
            total += item.value;
        }
        return total;
    }

    private void persist(String npcId, List<DungeonItem> items) {
        JSONArray array = new JSONArray();
        for (DungeonItem item : items) array.put(item.toJson());
        preferences.edit().putString(key(npcId), array.toString()).commit();
    }

    private static String key(String npcId) {
        return "inventory_" + npcId;
    }
}
