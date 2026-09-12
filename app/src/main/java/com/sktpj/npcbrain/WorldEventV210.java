package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

/** Committed event in the v2.1 ordered world journal. */
final class WorldEventV210 {
    final long sequence;
    final String eventId;
    final long aggregateRevision;
    final long worldTimeMs;
    final String eventType;
    final String actorId;
    final JSONArray participantIds;
    final String location;
    final String correlationId;
    final String causationId;
    final String idempotencyKey;
    final JSONObject payload;

    WorldEventV210(
            long sequence,
            String eventId,
            long aggregateRevision,
            long worldTimeMs,
            String eventType,
            String actorId,
            JSONArray participantIds,
            String location,
            String correlationId,
            String causationId,
            String idempotencyKey,
            JSONObject payload
    ) {
        this.sequence = Math.max(0L, sequence);
        this.eventId = safe(eventId);
        this.aggregateRevision = Math.max(0L, aggregateRevision);
        this.worldTimeMs = Math.max(0L, worldTimeMs);
        this.eventType = safe(eventType);
        this.actorId = safe(actorId);
        this.participantIds = copyArray(participantIds);
        this.location = safe(location);
        this.correlationId = safe(correlationId);
        this.causationId = safe(causationId);
        this.idempotencyKey = safe(idempotencyKey);
        this.payload = copyObject(payload);
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("sequence", sequence);
            json.put("event_id", eventId);
            json.put("aggregate_revision", aggregateRevision);
            json.put("world_time_ms", worldTimeMs);
            json.put("event_type", eventType);
            json.put("actor_id", actorId);
            json.put("participant_ids", copyArray(participantIds));
            json.put("location", location);
            json.put("correlation_id", correlationId);
            json.put("causation_id", causationId);
            json.put("idempotency_key", idempotencyKey);
            json.put("payload", copyObject(payload));
        } catch (Exception ignored) {
        }
        return json;
    }

    private static JSONArray copyArray(JSONArray source) {
        try {
            return source == null ? new JSONArray() : new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject copyObject(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
