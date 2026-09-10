package com.sktpj.npcbrain;

import org.json.JSONObject;

/** Immutable read model that binds one NPC to one world-time snapshot. */
final class NpcWorldSnapshot {
    private final String npcId;
    private final long worldTimeMs;
    private final boolean active;
    private final boolean dead;
    private final JSONObject character;
    private final JSONObject life;
    private final JSONObject innerLife;
    private final JSONObject relationships;
    private final boolean dungeonPresent;
    private final JSONObject dungeon;
    private final String effectiveLocation;
    private final String effectiveActivity;

    NpcWorldSnapshot(
            String npcId,
            long worldTimeMs,
            boolean active,
            boolean dead,
            JSONObject character,
            JSONObject life,
            JSONObject innerLife,
            JSONObject relationships,
            boolean dungeonPresent,
            JSONObject dungeon,
            String effectiveLocation,
            String effectiveActivity
    ) {
        this.npcId = NpcId.of(npcId).value();
        this.worldTimeMs = Math.max(0L, worldTimeMs);
        this.active = active;
        this.dead = dead;
        this.character = copy(character);
        this.life = copy(life);
        this.innerLife = copy(innerLife);
        this.relationships = copy(relationships);
        this.dungeonPresent = dungeonPresent;
        this.dungeon = copy(dungeon);
        this.effectiveLocation = safe(effectiveLocation, "unknown");
        this.effectiveActivity = safe(effectiveActivity, "idle");
    }

    String npcId() {
        return npcId;
    }

    long worldTimeMs() {
        return worldTimeMs;
    }

    boolean active() {
        return active;
    }

    boolean dead() {
        return dead;
    }

    boolean dungeonPresent() {
        return dungeonPresent;
    }

    String effectiveLocation() {
        return effectiveLocation;
    }

    String effectiveActivity() {
        return effectiveActivity;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("npc_id", npcId);
            json.put("world_time_ms", worldTimeMs);
            json.put("active", active);
            json.put("dead", dead);
            json.put("character", copy(character));
            json.put("life", copy(life));
            json.put("inner_life", copy(innerLife));
            json.put("relationships", copy(relationships));
            json.put("dungeon_present", dungeonPresent);
            json.put("dungeon", copy(dungeon));
            json.put("effective_location", effectiveLocation);
            json.put("effective_activity", effectiveActivity);
        } catch (Exception ignored) {
        }
        return json;
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String safe(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
