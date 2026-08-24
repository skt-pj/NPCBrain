package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReplyTimerRuntimeContextTest {
    @Test
    public void conversationGetsBoundSourceTimerAndReassessmentPolicies() throws Exception {
        long now = System.currentTimeMillis();
        JSONObject life = new JSONObject()
                .put("npc_id", "npc3")
                .put("world_time", now)
                .put("location", "home")
                .put("current_activity", "meal")
                .put("activity_started_at", now - 60_000L)
                .put("current_goal", "eat_dinner")
                .put("active_context", "dinner")
                .put("current_schedule_entry_id", "dinner")
                .put("current_activity_event_id", "activity-event")
                .put("daily_schedule", DailySchedule.defaultFor(NpcId.of("npc3")).toJson());
        JSONObject message = new JSONObject()
                .put("id", "msg-3")
                .put("sender_id", "user")
                .put("text", "ダンジョンに一緒に行く？")
                .put("time_ms", now);
        JSONObject runtime = new JSONObject()
                .put("mode", "conversational_message")
                .put("event_type", "message_received")
                .put("cause_event_id", "event-3")
                .put("event_time_ms", now)
                .put("room", new JSONObject().put("room_id", "direct_npc3"))
                .put("character_id", "npc3")
                .put("life_state", life)
                .put("newest_message", message)
                .put("recent_room_transcript", "user: 前にも聞いたけど、ダンジョンに一緒に行く？");
        String prompt = "Communication event for the NPC runtime.\nRuntime JSON:\n"
                + runtime + "\n\nTreat this as a real messaging situation.";

        ReplyTimerRuntimeContext.Prepared prepared = ReplyTimerRuntimeContext.prepare("npc3", prompt);
        assertNotNull(prepared.binding);
        assertEquals("npc3", prepared.binding.npcId);
        assertEquals("direct_npc3", prepared.binding.roomId);
        assertEquals("msg-3", prepared.binding.sourceMessageId);
        assertEquals("event-3", prepared.binding.sourceEventId);
        assertEquals("conversation|npc3|direct_npc3|msg-3", prepared.binding.sourceKey());

        int marker = prepared.prompt.indexOf("Runtime JSON:\n") + "Runtime JSON:\n".length();
        int end = prepared.prompt.indexOf("\n\n", marker);
        JSONObject enriched = new JSONObject(prepared.prompt.substring(marker, end));
        JSONObject grounding = enriched.getJSONObject("reply_timer_grounding");
        assertTrue(grounding.getBoolean("schedule_reply_timer_available"));
        assertTrue(grounding.getLong("now_ms") >= now);
        assertTrue(grounding.has("current_activity_ends_at_ms"));

        JSONObject runtimeDecision = enriched.getJSONObject("runtime_decision_contract");
        assertEquals(ReplyTimerToolSession.TOOL_NAME, runtimeDecision.getString("tool"));
        assertTrue(runtimeDecision.getBoolean("required_once_per_global_workspace_cycle"));
        assertEquals(ReplyTimerToolSession.OP_NONE,
                runtimeDecision.getString("no_state_write_operation"));
        assertEquals(ReplyTimerToolSession.OP_DUNGEON_PARTICIPATION_AND_REPLY_TIMER,
                runtimeDecision.getString("combined_participation_and_reply_timer_operation"));
        assertTrue(runtimeDecision.getString("instruction").contains("recorded immediately"));

        JSONObject reassessment = enriched.getJSONObject("conversation_reassessment_policy");
        assertTrue(reassessment.getBoolean("direct_question_salience"));
        assertTrue(reassessment.getBoolean("repeated_unresolved_question_recheck"));
        assertTrue(reassessment.getBoolean("silence_is_character_choice_not_default"));
        assertTrue(reassessment.getString("instruction").contains("Re-evaluate each new message"));

        JSONObject participation = enriched.getJSONObject("dungeon_participation_policy");
        assertEquals("global_workspace", participation.getString("decision_owner"));
        assertEquals(ReplyTimerToolSession.TOOL_NAME, participation.getString("structured_tool"));
        assertEquals(ReplyTimerToolSession.OP_DUNGEON_PARTICIPATION,
                participation.getString("operation"));
        assertEquals(ReplyTimerToolSession.OP_DUNGEON_PARTICIPATION_AND_REPLY_TIMER,
                participation.getString("combined_with_reply_timer_operation"));
        assertTrue(participation.getBoolean("independent_of_visible_utterance"));
        assertTrue(participation.getString("instruction").contains("Do not wait for numeric thresholds"));
    }

    @Test
    public void unrelatedRuntimeModeDoesNotExposeTimerBinding() throws Exception {
        JSONObject runtime = new JSONObject()
                .put("mode", "dungeon_turn")
                .put("character_id", "npc1");
        String prompt = "Dungeon.\nRuntime JSON:\n" + runtime + "\n\nContinue.";
        ReplyTimerRuntimeContext.Prepared prepared = ReplyTimerRuntimeContext.prepare("npc1", prompt);
        assertNull(prepared.binding);
        assertFalse(prepared.prompt.contains("reply_timer_grounding"));
        assertFalse(prepared.prompt.contains("runtime_decision_contract"));
        assertFalse(prepared.prompt.contains("conversation_reassessment_policy"));
    }
}
