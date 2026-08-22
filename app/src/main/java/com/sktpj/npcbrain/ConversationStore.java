package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class ConversationStore {
    private static final String PREFS = "npcbrain_conversations_v1";
    private static final int MAX_MESSAGES_PER_ROOM = 120;
    private static final String DECISION_PREFIX = "decision_";
    private static final String RUNTIME_DECISION_PREFIX = "runtime_decision_";

    private final SharedPreferences preferences;

    ConversationStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized JSONObject appendUserMessage(String roomId, String text, long timeMs) {
        return append(
                "",
                roomId,
                "user",
                "あなた",
                text,
                "",
                timeMs,
                "",
                new JSONArray(),
                false
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
                "",
                roomId,
                senderId,
                senderName,
                text,
                action,
                timeMs,
                causeEventId,
                brainTrace,
                false
        );
    }

    synchronized JSONObject appendNpcMessageWithId(
            String messageId,
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
                messageId,
                roomId,
                senderId,
                senderName,
                text,
                action,
                timeMs,
                causeEventId,
                brainTrace,
                true
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
                "",
                roomId,
                DECISION_PREFIX + safeId(npcId),
                (senderName == null || senderName.trim().isEmpty() ? "NPC" : senderName.trim())
                        + "（返信なし）",
                "返信しませんでした",
                action,
                timeMs,
                causeEventId,
                brainTrace,
                false
        );
    }

    synchronized JSONObject appendNpcRuntimeDecision(
            String messageId,
            String roomId,
            String npcId,
            String senderName,
            String decision,
            String action,
            long timeMs,
            String causeEventId,
            JSONArray brainTrace
    ) {
        String normalizedDecision = decision == null ? "" : decision.trim().toLowerCase(java.util.Locale.US);
        boolean deferred = BrainCommunicationDecision.DEFER.equals(normalizedDecision);
        String baseName = senderName == null || senderName.trim().isEmpty()
                ? "NPC"
                : senderName.trim();
        return append(
                messageId,
                roomId,
                RUNTIME_DECISION_PREFIX + safeId(npcId),
                baseName + (deferred ? "（後で送信）" : "（自発送信なし）"),
                deferred ? "後で送信すると判断しました" : "自発送信しませんでした",
                action,
                timeMs,
                causeEventId,
                brainTrace,
                true
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

    synchronized JSONObject messageById(String roomId, String messageId) {
        JSONObject item = findById(load(roomId), messageId);
        if (item == null) return null;
        try {
            return new JSONObject(item.toString());
        } catch (Exception ignored) {
            return null;
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
            if (isDebugDecisionSender(senderId)) continue;
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

    static boolean isDebugDecisionSender(String senderId) {
        String value = senderId == null ? "" : senderId.trim();
        return value.startsWith(DECISION_PREFIX) || value.startsWith(RUNTIME_DECISION_PREFIX);
    }

    private JSONObject append(
            String fixedId,
            String roomId,
            String senderId,
            String senderName,
            String text,
            String action,
            long timeMs,
            String causeEventId,
            JSONArray brainTrace,
            boolean idempotentById
    ) {
        try {
            JSONArray source = load(roomId);
            String normalizedId = fixedId == null ? "" : fixedId.trim();
            if (idempotentById && !normalizedId.isEmpty()) {
                JSONObject existing = findById(source, normalizedId);
                if (existing != null) return new JSONObject(existing.toString());
            }

            JSONObject message = (normalizedId.isEmpty()
                    ? Message.create(
                            roomId,
                            senderId,
                            senderName,
                            text,
                            action,
                            timeMs,
                            causeEventId,
                            brainTrace)
                    : Message.createWithId(
                            normalizedId,
                            roomId,
                            senderId,
                            senderName,
                            text,
                            action,
                            timeMs,
                            causeEventId,
                            brainTrace)).toJson();

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

    private static JSONObject findById(JSONArray source, String messageId) {
        if (source == null || messageId == null || messageId.trim().isEmpty()) return null;
        for (int i = source.length() - 1; i >= 0; i--) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && messageId.equals(item.optString("id", ""))) return item;
        }
        return null;
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
