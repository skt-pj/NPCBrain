package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

final class ConversationStore {
    private static final String PREFS = "npcbrain_conversations_v1";
    private static final int MAX_MESSAGES_PER_ROOM = 120;

    private final SharedPreferences preferences;

    ConversationStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized JSONObject appendUserMessage(String roomId, String text, long timeMs) {
        return append(
                roomId,
                "user",
                "あなた",
                text,
                "",
                timeMs,
                "",
                new JSONArray()
        );
    }

    synchronized JSONObject appendNpcMessage(
            String roomId,
            String senderId,
            String senderName,
            String text,
            String action,
            long timeMs,
            String causeEventId,
            JSONArray brainTrace
    ) {
        return append(
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

    synchronized JSONArray messages(String roomId) {
        JSONArray source = load(roomId);
        try {
            return new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    synchronized JSONObject lastMessage(String roomId) {
        JSONArray items = load(roomId);
        JSONObject last = items.optJSONObject(items.length() - 1);
        if (last == null) return null;
        try {
            return new JSONObject(last.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized int messageCount(String roomId) {
        return load(roomId).length();
    }

    synchronized String recentContext(String roomId, int maxMessages) {
        JSONArray items = load(roomId);
        int start = Math.max(0, items.length() - Math.max(1, maxMessages));
        StringBuilder result = new StringBuilder();
        for (int i = start; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String sender = item.optString("sender_name", item.optString("sender_id", "?"));
            String text = item.optString("text", "").trim();
            if (text.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(sender).append(": ").append(text);
        }
        return result.toString();
    }

    synchronized void clearRoom(String roomId) {
        preferences.edit().remove(key(roomId)).apply();
    }

    synchronized void clearAll() {
        preferences.edit().clear().apply();
    }

    private JSONObject append(
            String roomId,
            String senderId,
            String senderName,
            String text,
            String action,
            long timeMs,
            String causeEventId,
            JSONArray brainTrace
    ) {
        try {
            JSONObject message = new JSONObject();
            message.put("id", UUID.randomUUID().toString());
            message.put("room_id", roomId == null ? "" : roomId);
            message.put("sender_id", senderId == null ? "" : senderId);
            message.put("sender_name", senderName == null ? "" : senderName);
            message.put("text", text == null ? "" : text.trim());
            message.put("action", action == null ? "" : action.trim());
            message.put("time_ms", timeMs);
            message.put("cause_event_id", causeEventId == null ? "" : causeEventId);
            message.put("brain_trace", brainTrace == null
                    ? new JSONArray()
                    : new JSONArray(brainTrace.toString()));

            JSONArray source = load(roomId);
            JSONArray updated = new JSONArray();
            int start = Math.max(0, source.length() - (MAX_MESSAGES_PER_ROOM - 1));
            for (int i = start; i < source.length(); i++) {
                updated.put(source.get(i));
            }
            updated.put(message);
            preferences.edit().putString(key(roomId), updated.toString()).apply();
            return new JSONObject(message.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private JSONArray load(String roomId) {
        try {
            return new JSONArray(preferences.getString(key(roomId), "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String key(String roomId) {
        String value = roomId == null ? "unknown" : roomId.trim().toLowerCase();
        value = value.replaceAll("[^a-z0-9_-]", "_");
        return "room_" + value;
    }
}
