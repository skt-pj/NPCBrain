package com.sktpj.npcbrain;

import org.json.JSONObject;

final class ReplyTimerTask {
    final String sourceKey;
    final int jobId;
    final String mode;
    final String npcId;
    final String roomId;
    final String sourceMessageId;
    final String sourceEventId;
    final JSONObject sourceMessage;
    final JSONObject sourceEvent;
    final long wakeAtMs;
    final String reason;
    final long createdAtMs;

    ReplyTimerTask(
            String sourceKey,
            int jobId,
            String mode,
            String npcId,
            String roomId,
            String sourceMessageId,
            String sourceEventId,
            JSONObject sourceMessage,
            JSONObject sourceEvent,
            long wakeAtMs,
            String reason,
            long createdAtMs
    ) {
        this.sourceKey = safe(sourceKey);
        this.jobId = Math.max(0, jobId);
        this.mode = safe(mode);
        this.npcId = safe(npcId);
        this.roomId = safe(roomId);
        this.sourceMessageId = safe(sourceMessageId);
        this.sourceEventId = safe(sourceEventId);
        this.sourceMessage = copy(sourceMessage);
        this.sourceEvent = copy(sourceEvent);
        this.wakeAtMs = Math.max(0L, wakeAtMs);
        this.reason = safe(reason);
        this.createdAtMs = Math.max(0L, createdAtMs);
    }

    static ReplyTimerTask fromBinding(
            ReplyTimerBinding binding,
            int jobId,
            long wakeAtMs,
            String reason,
            long createdAtMs
    ) {
        return new ReplyTimerTask(
                binding.sourceKey(), jobId, binding.mode, binding.npcId, binding.roomId,
                binding.sourceMessageId, binding.sourceEventId, binding.sourceMessage,
                binding.sourceEvent, wakeAtMs, reason, createdAtMs
        );
    }

    static ReplyTimerTask fromJson(JSONObject json) {
        if (json == null) return null;
        ReplyTimerTask task = new ReplyTimerTask(
                json.optString("source_key", ""), json.optInt("job_id", 0),
                json.optString("mode", ""), json.optString("npc_id", ""),
                json.optString("room_id", ""), json.optString("source_message_id", ""),
                json.optString("source_event_id", ""), json.optJSONObject("source_message"),
                json.optJSONObject("source_event"), json.optLong("wake_at_ms", 0L),
                json.optString("reason", ""), json.optLong("created_at_ms", 0L)
        );
        return task.isValid() ? task : null;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("source_key", sourceKey);
            json.put("job_id", jobId);
            json.put("mode", mode);
            json.put("npc_id", npcId);
            json.put("room_id", roomId);
            json.put("source_message_id", sourceMessageId);
            json.put("source_event_id", sourceEventId);
            json.put("source_message", copy(sourceMessage));
            json.put("source_event", copy(sourceEvent));
            json.put("wake_at_ms", wakeAtMs);
            json.put("reason", reason);
            json.put("created_at_ms", createdAtMs);
        } catch (Exception ignored) {
        }
        return json;
    }

    boolean isValid() {
        if (sourceKey.isEmpty() || jobId <= 0 || npcId.isEmpty() || wakeAtMs <= 0L) return false;
        if (ReplyTimerBinding.MODE_CONVERSATION.equals(mode)) {
            return !roomId.isEmpty() && !sourceMessageId.isEmpty();
        }
        return ReplyTimerBinding.MODE_SPONTANEOUS.equals(mode) && !sourceEventId.isEmpty();
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
