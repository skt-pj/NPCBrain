package com.sktpj.npcbrain;

import org.json.JSONObject;

final class ReplyTimerRuntimeContext {
    private static final String MARKER = "Runtime JSON:\n";

    static final class Prepared {
        final String prompt;
        final ReplyTimerBinding binding;

        Prepared(String prompt, ReplyTimerBinding binding) {
            this.prompt = prompt == null ? "" : prompt;
            this.binding = binding;
        }
    }

    private ReplyTimerRuntimeContext() {}

    static Prepared prepare(String npcId, String prompt) {
        if (prompt == null || prompt.isEmpty()) return new Prepared(prompt, null);
        int marker = prompt.indexOf(MARKER);
        if (marker < 0) return new Prepared(prompt, null);
        int jsonStart = marker + MARKER.length();
        int jsonEnd = prompt.indexOf("\n\n", jsonStart);
        if (jsonEnd < 0) return new Prepared(prompt, null);
        try {
            JSONObject runtime = new JSONObject(prompt.substring(jsonStart, jsonEnd));
            String mode = runtime.optString("mode", "");
            if (!"conversational_message".equals(mode)
                    && !"spontaneous_life_event".equals(mode)) {
                return new Prepared(prompt, null);
            }

            long now = Math.max(
                    System.currentTimeMillis(),
                    Math.max(runtime.optLong("now_ms", 0L), runtime.optLong("event_time_ms", 0L)));
            JSONObject lifeJson = runtime.optJSONObject("life_state");
            LifeState lifeState = LifeState.fromJson(
                    lifeJson,
                    NpcId.of(npcId),
                    now);
            runtime.put("reply_timer_grounding", ReplyTimerGrounding.toJson(lifeState, now));

            ReplyTimerBinding binding;
            if ("conversational_message".equals(mode)) {
                JSONObject room = runtime.optJSONObject("room");
                JSONObject message = runtime.optJSONObject("newest_message");
                String roomId = room == null ? "" : room.optString("room_id", "");
                binding = ReplyTimerBinding.conversation(
                        npcId,
                        roomId,
                        message,
                        runtime.optString("cause_event_id", ""),
                        now);
            } else {
                WorldEvent source = WorldEvent.fromJson(runtime.optJSONObject("source_event"));
                binding = ReplyTimerBinding.spontaneous(npcId, source, now);
            }
            if (binding == null || !binding.isValid()) return new Prepared(prompt, null);
            String enriched = prompt.substring(0, jsonStart)
                    + runtime.toString()
                    + prompt.substring(jsonEnd);
            return new Prepared(enriched, binding);
        } catch (Exception ignored) {
            return new Prepared(prompt, null);
        }
    }
}
