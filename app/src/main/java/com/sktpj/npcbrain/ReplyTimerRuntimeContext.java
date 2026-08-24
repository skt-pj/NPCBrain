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
            runtime.put("runtime_decision_contract", runtimeDecisionContract());

            if ("conversational_message".equals(mode)) {
                runtime.put("conversation_reassessment_policy", conversationPolicy());
                runtime.put("dungeon_participation_policy", dungeonParticipationPolicy());
            }

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

    static JSONObject runtimeDecisionContract() {
        JSONObject policy = new JSONObject();
        try {
            policy.put("tool", ReplyTimerToolSession.TOOL_NAME);
            policy.put("required_once_per_global_workspace_cycle", true);
            policy.put("no_state_write_operation", ReplyTimerToolSession.OP_NONE);
            policy.put("instruction",
                    "Invoke the runtime decision tool exactly once before the final Global Workspace JSON. Choose none when no runtime state write is appropriate. Tool invocation is independent of whether npc_utterance is empty, a reply is deferred, or the character speaks.");
        } catch (Exception ignored) {
        }
        return policy;
    }

    static JSONObject conversationPolicy() {
        JSONObject policy = new JSONObject();
        try {
            policy.put("direct_question_salience", true);
            policy.put("repeated_unresolved_question_recheck", true);
            policy.put("silence_is_character_choice_not_default", true);
            policy.put("instruction",
                    "A message directly addressed to this NPC, especially a question, normally deserves conscious social evaluation. Use recent_room_transcript to notice when the user is asking the same or a similar unresolved question again. Re-evaluate each new message instead of carrying forward silence mechanically. The NPC may still stay silent, refuse, defer or answer, but silence must follow from the character's grounded personality, relationship, current state or situation; do not use 'conversation need not continue' as a default suppression rule.");
        } catch (Exception ignored) {
        }
        return policy;
    }

    static JSONObject dungeonParticipationPolicy() {
        JSONObject policy = new JSONObject();
        try {
            policy.put("decision_owner", "global_workspace");
            policy.put("structured_tool", ReplyTimerToolSession.TOOL_NAME);
            policy.put("operation", ReplyTimerToolSession.OP_DUNGEON_PARTICIPATION);
            policy.put("independent_of_visible_utterance", true);
            policy.put("instruction",
                    "When the current message concerns whether this NPC will join, refuse, hesitate about, reconsider or withdraw from the dungeon, Global Workspace must record that integrated decision with the runtime tool. Consider fear, personality, current affect, memory, relationship and grounded danger together. Do not wait for numeric thresholds, do not require an explicit reason sentence, and do not infer the decision later from emitted wording. Record the decision even when the NPC ultimately stays silent or schedules a later conversational reply.");
        } catch (Exception ignored) {
        }
        return policy;
    }
}
