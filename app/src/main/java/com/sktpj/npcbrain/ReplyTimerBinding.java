package com.sktpj.npcbrain;

import org.json.JSONObject;

final class ReplyTimerBinding {
    static final String MODE_CONVERSATION = "conversation";
    static final String MODE_SPONTANEOUS = "spontaneous";

    final String mode;
    final String npcId;
    final String roomId;
    final String sourceMessageId;
    final String sourceEventId;
    final JSONObject sourceMessage;
    final JSONObject sourceEvent;
    final long decisionNowMs;

    private ReplyTimerBinding(
            String mode,
            String npcId,
            String roomId,
            String sourceMessageId,
            String sourceEventId,
            JSONObject sourceMessage,
            JSONObject sourceEvent,
            long decisionNowMs
    ) {
        this.mode = safe(mode);
        this.npcId = NpcId.of(npcId).value();
        this.roomId = safe(roomId);
        this.sourceMessageId = safe(sourceMessageId);
        this.sourceEventId = safe(sourceEventId);
        this.sourceMessage = copy(sourceMessage);
        this.sourceEvent = copy(sourceEvent);
        this.decisionNowMs = Math.max(0L, decisionNowMs);
    }

    static ReplyTimerBinding conversation(
            String npcId,
            String roomId,
            JSONObject sourceMessage,
            WorldEvent sourceEvent,
            long decisionNowMs
    ) {
        JSONObject message = copy(sourceMessage);
        return new ReplyTimerBinding(
                MODE_CONVERSATION,
                npcId,
                roomId,
                message.optString("id", ""),
                sourceEvent == null ? message.optString("cause_event_id", "") : sourceEvent.eventId(),
                message,
                sourceEvent == null ? new JSONObject() : sourceEvent.toJson(),
                decisionNowMs
        );
    }

    static ReplyTimerBinding spontaneous(
            String npcId,
            String roomId,
            WorldEvent sourceEvent,
            long decisionNowMs
    ) {
        return new ReplyTimerBinding(
                MODE_SPONTANEOUS,
                npcId,
                roomId,
                "",
                sourceEvent == null ? "" : sourceEvent.eventId(),
                new JSONObject(),
                sourceEvent == null ? new JSONObject() : sourceEvent.toJson(),
                decisionNowMs
        );
    }

    String sourceKey() {
        if (MODE_CONVERSATION.equals(mode)) {
            return mode + "|" + npcId + "|" + roomId + "|" + sourceMessageId;
        }
        return mode + "|" + npcId + "|" + sourceEventId;
    }

    boolean isValid() {
        if (npcId.isEmpty()) return false;
        if (MODE_CONVERSATION.equals(mode)) {
            return !roomId.isEmpty() && !sourceMessageId.isEmpty();
        }
        if (MODE_SPONTANEOUS.equals(mode)) return !sourceEventId.isEmpty();
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
