package com.sktpj.npcbrain;

import org.json.JSONObject;

import java.util.UUID;

final class WorldEvent {
    private final String eventId;
    private final String eventType;
    private final String actorId;
    private final String targetId;
    private final long timeMs;
    private final String location;
    private final JSONObject payload;
    private final String causeEventId;

    WorldEvent(
            String eventId,
            String eventType,
            String actorId,
            String targetId,
            long timeMs,
            String location,
            JSONObject payload,
            String causeEventId
    ) {
        this.eventId = safe(eventId);
        this.eventType = safe(eventType);
        this.actorId = safe(actorId);
        this.targetId = safe(targetId);
        this.timeMs = timeMs;
        this.location = safe(location);
        this.payload = copy(payload);
        this.causeEventId = safe(causeEventId);
    }

    static WorldEvent create(
            String eventType,
            String actorId,
            String targetId,
            long timeMs,
            String location,
            JSONObject payload,
            String causeEventId
    ) {
        return new WorldEvent(
                UUID.randomUUID().toString(),
                eventType,
                actorId,
                targetId,
                timeMs,
                location,
                payload,
                causeEventId
        );
    }

    static WorldEvent fromJson(JSONObject json) {
        if (json == null) return null;
        String eventId = json.optString("event_id", "").trim();
        if (eventId.isEmpty()) return null;
        return new WorldEvent(
                eventId,
                json.optString("event_type", ""),
                json.optString("actor_id", ""),
                json.optString("target_id", ""),
                json.optLong("time", 0L),
                json.optString("location", ""),
                json.optJSONObject("payload"),
                json.optString("cause_event_id", "")
        );
    }

    String eventId() {
        return eventId;
    }

    String eventType() {
        return eventType;
    }

    String actorId() {
        return actorId;
    }

    String targetId() {
        return targetId;
    }

    long timeMs() {
        return timeMs;
    }

    String location() {
        return location;
    }

    JSONObject payload() {
        return copy(payload);
    }

    String causeEventId() {
        return causeEventId;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("event_id", eventId);
            json.put("event_type", eventType);
            json.put("actor_id", actorId);
            json.put("target_id", targetId);
            json.put("time", timeMs);
            json.put("location", location);
            json.put("payload", copy(payload));
            json.put("cause_event_id", causeEventId);
        } catch (Exception ignored) {
        }
        return json;
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return json == null ? new JSONObject() : new JSONObject(json.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
