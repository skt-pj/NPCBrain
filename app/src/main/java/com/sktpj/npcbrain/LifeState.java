package com.sktpj.npcbrain;

import org.json.JSONObject;

final class LifeState {
    private final NpcId npcId;
    private final long worldTimeMs;
    private final String location;
    private final String currentActivity;
    private final long activityStartedAtMs;
    private final String currentGoal;
    private final String activeContext;

    LifeState(
            NpcId npcId,
            long worldTimeMs,
            String location,
            String currentActivity,
            long activityStartedAtMs,
            String currentGoal,
            String activeContext
    ) {
        this.npcId = npcId;
        this.worldTimeMs = worldTimeMs;
        this.location = safe(location, "unknown");
        this.currentActivity = safe(currentActivity, "idle");
        this.activityStartedAtMs = activityStartedAtMs > 0L ? activityStartedAtMs : worldTimeMs;
        this.currentGoal = safe(currentGoal, "");
        this.activeContext = safe(activeContext, "");
    }

    static LifeState initial(NpcId npcId, long worldTimeMs) {
        return new LifeState(npcId, worldTimeMs, "unknown", "idle", worldTimeMs, "", "");
    }

    static LifeState fromJson(JSONObject json, NpcId fallbackId, long fallbackWorldTimeMs) {
        if (json == null) return initial(fallbackId, fallbackWorldTimeMs);
        NpcId id;
        try {
            id = NpcId.of(json.optString("npc_id", fallbackId.value()));
        } catch (Exception ignored) {
            id = fallbackId;
        }
        long worldTime = json.optLong("world_time", fallbackWorldTimeMs);
        return new LifeState(
                id,
                worldTime,
                json.optString("location", "unknown"),
                json.optString("current_activity", "idle"),
                json.optLong("activity_started_at", worldTime),
                json.optString("current_goal", ""),
                json.optString("active_context", "")
        );
    }

    LifeState atWorldTime(long newWorldTimeMs) {
        return new LifeState(
                npcId,
                Math.max(worldTimeMs, newWorldTimeMs),
                location,
                currentActivity,
                activityStartedAtMs,
                currentGoal,
                activeContext
        );
    }

    NpcId npcId() {
        return npcId;
    }

    long worldTimeMs() {
        return worldTimeMs;
    }

    String location() {
        return location;
    }

    String currentActivity() {
        return currentActivity;
    }

    long activityStartedAtMs() {
        return activityStartedAtMs;
    }

    String currentGoal() {
        return currentGoal;
    }

    String activeContext() {
        return activeContext;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("npc_id", npcId.value());
            json.put("world_time", worldTimeMs);
            json.put("location", location);
            json.put("current_activity", currentActivity);
            json.put("activity_started_at", activityStartedAtMs);
            json.put("current_goal", currentGoal);
            json.put("active_context", activeContext);
        } catch (Exception ignored) {
        }
        return json;
    }

    private static String safe(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
