package com.sktpj.npcbrain;

import org.json.JSONObject;

final class DungeonItem {
    static final String SOURCE_ENEMY = "enemy";
    static final String SOURCE_CHEST = "chest";

    final String itemId;
    final String name;
    final long value;
    final String source;
    final int floor;
    final long acquiredAtMs;

    DungeonItem(String itemId, String name, long value, String source, int floor, long acquiredAtMs) {
        this.itemId = clean(itemId, 120, "item");
        this.name = clean(name, 80, "アイテム");
        this.value = Math.max(0L, value);
        this.source = SOURCE_CHEST.equals(source) ? SOURCE_CHEST : SOURCE_ENEMY;
        this.floor = Math.max(1, floor);
        this.acquiredAtMs = Math.max(0L, acquiredAtMs);
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("item_id", itemId);
            object.put("name", name);
            object.put("value", value);
            object.put("source", source);
            object.put("floor", floor);
            object.put("acquired_at_ms", acquiredAtMs);
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonItem fromJson(JSONObject object) {
        if (object == null) return null;
        String id = object.optString("item_id", "").trim();
        if (id.isEmpty()) return null;
        return new DungeonItem(
                id,
                object.optString("name", "アイテム"),
                Math.max(0L, object.optLong("value", 0L)),
                object.optString("source", SOURCE_ENEMY),
                object.optInt("floor", 1),
                object.optLong("acquired_at_ms", 0L));
    }

    private static String clean(String raw, int max, String fallback) {
        String value = raw == null ? "" : raw.replace('\n', ' ').trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) value = fallback;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
