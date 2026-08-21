package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

final class Message {
    private final String id;
    private final String roomId;
    private final String senderId;
    private final String senderName;
    private final String text;
    private final String action;
    private final long timeMs;
    private final String causeEventId;
    private final JSONArray brainTrace;

    private Message(
            String id,
            String roomId,
            String senderId,
            String senderName,
            String text,
            String action,
            long timeMs,
            String causeEventId,
            JSONArray brainTrace
    ) {
        this.id = safe(id);
        this.roomId = safe(roomId);
        this.senderId = safe(senderId);
        this.senderName = safe(senderName);
        this.text = safe(text);
        this.action = safe(action);
        this.timeMs = timeMs;
        this.causeEventId = safe(causeEventId);
        this.brainTrace = copy(brainTrace);
    }

    static Message create(
            String roomId,
            String senderId,
            String senderName,
            String text,
            String action,
            long timeMs,
            String causeEventId,
            JSONArray brainTrace
    ) {
        return new Message(
                UUID.randomUUID().toString(),
                roomId,
                senderId,
                senderName,
                text,
                action,
                timeMs,
                causeEventId,
                brainTrace
        );
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("room_id", roomId);
            json.put("sender_id", senderId);
            json.put("sender_name", senderName);
            json.put("text", text);
            json.put("action", action);
            json.put("time_ms", timeMs);
            json.put("cause_event_id", causeEventId);
            json.put("brain_trace", copy(brainTrace));
        } catch (Exception ignored) {
        }
        return json;
    }

    private static JSONArray copy(JSONArray json) {
        try {
            return json == null ? new JSONArray() : new JSONArray(json.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
