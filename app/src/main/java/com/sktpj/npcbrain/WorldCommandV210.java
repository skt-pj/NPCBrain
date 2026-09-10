package com.sktpj.npcbrain;

import org.json.JSONObject;

import java.util.UUID;

/** Immutable command submitted to the canonical world kernel. */
final class WorldCommandV210 {
    static final String ADVANCE_TIME = "advance_time";
    static final String USER_POST_MESSAGE = "user_post_message";
    static final String NPC_POST_MESSAGE = "npc_post_message";
    static final String COMMUNICATION_SKIPPED = "communication_skipped";
    static final String COMMUNICATION_DEFERRED = "communication_deferred";
    static final String UPSERT_LIFE_STATE = "upsert_life_state";
    static final String UPSERT_INNER_LIFE = "upsert_inner_life";
    static final String UPSERT_DUNGEON_STATE = "upsert_dungeon_state";
    static final String SET_DUNGEON_PRESENCE = "set_dungeon_presence";
    static final String APPEND_WORLD_EVENT = "append_world_event";
    static final String APPLY_RELATIONSHIP = "apply_relationship";
    static final String RESET_NPC_BRAIN = "reset_npc_brain";

    final String commandType;
    final long requestedTimeMs;
    final String actorId;
    final String idempotencyKey;
    final String correlationId;
    final JSONObject payload;

    WorldCommandV210(
            String commandType,
            long requestedTimeMs,
            String actorId,
            String idempotencyKey,
            String correlationId,
            JSONObject payload
    ) {
        this.commandType = safe(commandType);
        this.requestedTimeMs = Math.max(0L, requestedTimeMs);
        this.actorId = safe(actorId);
        this.idempotencyKey = safe(idempotencyKey);
        this.correlationId = safe(correlationId);
        this.payload = copy(payload);
        if (this.commandType.isEmpty()) throw new IllegalArgumentException("commandType is required");
        if (this.idempotencyKey.isEmpty()) throw new IllegalArgumentException("idempotencyKey is required");
    }

    static WorldCommandV210 of(
            String type,
            long timeMs,
            String actorId,
            String idempotencyKey,
            JSONObject payload
    ) {
        return new WorldCommandV210(
                type,
                timeMs,
                actorId,
                idempotencyKey,
                UUID.randomUUID().toString(),
                payload);
    }

    private static JSONObject copy(JSONObject source) {
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
