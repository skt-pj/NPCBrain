package com.sktpj.npcbrain;

import org.json.JSONObject;

/** Canonical mutable runtime state for one NPC. Profile/personality remain configuration. */
final class CanonicalNpcStateV210 {
    private final JSONObject json;

    private CanonicalNpcStateV210(JSONObject json) {
        this.json = copy(json);
    }

    static CanonicalNpcStateV210 empty(String npcId) {
        JSONObject json = new JSONObject();
        try {
            json.put("npc_id", NpcId.of(npcId).value());
            json.put("state_version", 0L);
            json.put("active", true);
            json.put("dead", false);
            json.put("location", "unknown");
            json.put("activity", "idle");
            json.put("goal", "");
            json.put("life_state", new JSONObject());
            json.put("inner_life", new JSONObject());
            json.put("intention", new JSONObject());
            json.put("relationships", new JSONObject());
            json.put("dungeon_present", false);
            json.put("dungeon_actor", new JSONObject());
        } catch (Exception ignored) {
        }
        return new CanonicalNpcStateV210(json);
    }

    static CanonicalNpcStateV210 fromJson(String npcId, JSONObject source) {
        CanonicalNpcStateV210 fallback = empty(npcId);
        JSONObject json = copy(source);
        try {
            json.put("npc_id", NpcId.of(npcId).value());
            if (!json.has("state_version")) json.put("state_version", 0L);
            if (!json.has("active")) json.put("active", true);
            if (!json.has("dead")) json.put("dead", false);
            if (!json.has("location")) json.put("location", "unknown");
            if (!json.has("activity")) json.put("activity", "idle");
            if (!json.has("goal")) json.put("goal", "");
            if (!json.has("life_state")) json.put("life_state", new JSONObject());
            if (!json.has("inner_life")) json.put("inner_life", new JSONObject());
            if (!json.has("intention")) json.put("intention", new JSONObject());
            if (!json.has("relationships")) json.put("relationships", new JSONObject());
            if (!json.has("dungeon_present")) json.put("dungeon_present", false);
            if (!json.has("dungeon_actor")) json.put("dungeon_actor", new JSONObject());
            return new CanonicalNpcStateV210(json);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    String npcId() { return json.optString("npc_id", ""); }
    long stateVersion() { return Math.max(0L, json.optLong("state_version", 0L)); }
    boolean active() { return json.optBoolean("active", true); }
    boolean dead() { return json.optBoolean("dead", false); }
    String location() { return json.optString("location", "unknown"); }
    String activity() { return json.optString("activity", "idle"); }
    String goal() { return json.optString("goal", ""); }
    boolean dungeonPresent() { return json.optBoolean("dungeon_present", false); }
    JSONObject lifeState() { return copy(json.optJSONObject("life_state")); }
    JSONObject innerLife() { return copy(json.optJSONObject("inner_life")); }
    JSONObject intention() { return copy(json.optJSONObject("intention")); }
    JSONObject relationships() { return copy(json.optJSONObject("relationships")); }
    JSONObject dungeonActor() { return copy(json.optJSONObject("dungeon_actor")); }

    CanonicalNpcStateV210 withLife(JSONObject life) {
        JSONObject next = toJson();
        JSONObject safeLife = copy(life);
        try {
            next.put("life_state", safeLife);
            next.put("location", safeLife.optString("location", location()));
            next.put("activity", safeLife.optString("current_activity", activity()));
            next.put("goal", safeLife.optString("current_goal", goal()));
        } catch (Exception ignored) {
        }
        return incremented(next);
    }

    CanonicalNpcStateV210 withInnerLife(JSONObject innerLife) {
        JSONObject next = toJson();
        try {
            next.put("inner_life", copy(innerLife));
        } catch (Exception ignored) {
        }
        return incremented(next);
    }

    CanonicalNpcStateV210 withRelationships(JSONObject relationships) {
        JSONObject next = toJson();
        try {
            next.put("relationships", copy(relationships));
        } catch (Exception ignored) {
        }
        return incremented(next);
    }

    CanonicalNpcStateV210 withDungeon(boolean present, JSONObject actor) {
        JSONObject next = toJson();
        JSONObject dungeon = copy(actor);
        try {
            next.put("dungeon_present", present);
            next.put("dungeon_actor", dungeon);
            if (present && dungeon.length() > 0) {
                int floor = Math.max(1, dungeon.optInt("floor", 1));
                next.put("location", "dungeon_floor_" + floor);
                String action = dungeon.optString("last_action", dungeon.optString("lastAction", "")).trim();
                next.put("activity", action.isEmpty() ? "dungeon_exploration" : action);
            } else {
                JSONObject life = next.optJSONObject("life_state");
                if (life != null) {
                    next.put("location", life.optString("location", "unknown"));
                    next.put("activity", life.optString("current_activity", "idle"));
                }
            }
        } catch (Exception ignored) {
        }
        return incremented(next);
    }

    CanonicalNpcStateV210 withFlags(boolean active, boolean dead) {
        JSONObject next = toJson();
        try {
            next.put("active", active);
            next.put("dead", dead);
            if (dead) next.put("dungeon_present", false);
        } catch (Exception ignored) {
        }
        return incremented(next);
    }

    JSONObject toJson() { return copy(json); }

    private CanonicalNpcStateV210 incremented(JSONObject next) {
        try {
            next.put("state_version", stateVersion() + 1L);
        } catch (Exception ignored) {
        }
        return new CanonicalNpcStateV210(next);
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
