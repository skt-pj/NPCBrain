package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class ConversationStore {
    private static final String PREFS = "npcbrain_conversations_v1";
    private static final int MAX_MESSAGES_PER_ROOM = 120;
    private static final String DECISION_PREFIX = "decision_";

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

    synchronized JSONObject appendNpcSilentDecision(
            String roomId,
            String npcId,
            String senderName,
            String action,
            long timeMs,
            String causeEventId,
            JSONArray brainTrace
    ) {
        return append(
                roomId,
                DECISION_PREFIX + safeId(npcId),
                (senderName == null || senderName.trim().isEmpty() ? "NPC" : senderName.trim())
                        + "（返信なし）",
                "返信しませんでした",
                action,
                timeMs,
                causeEventId,
                brainTrace
        );
    }

    synchronized JSONObject setCauseEventId(String roomId, String messageId, String causeEventId) {
        if (messageId == null || messageId.trim().isEmpty()) return new JSONObject();
        JSONArray source = load(roomId);
        JSONObject matched = null;
        try {
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null || !messageId.equals(item.optString("id", ""))) continue;
                item.put("cause_event_id", causeEventId == null ? "" : causeEventId.trim());
                matched = new JSONObject(item.toString());
                break;
            }
            if (matched != null) {
                preferences.edit().putString(key(roomId), source.toString()).apply();
                return matched;
            }
        } catch (Exception ignored) {
        }
        return new JSONObject();
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
            String senderId = item.optString("sender_id", "");
            if (senderId.startsWith(DECISION_PREFIX)) continue;
            String sender = item.optString("sender_name", senderId.isEmpty() ? "?" : senderId);
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
            JSONObject message = Message.create(
                    roomId,
                    senderId,
                    senderName,
                    text,
                    action,
                    timeMs,
                    causeEventId,
                    brainTrace
            ).toJson();

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

    private static String safeId(String value) {
        if (value == null || value.trim().isEmpty()) return "npc";
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }
}
