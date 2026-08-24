package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OpenAiClientFunctionCallTest {
    @Test
    public void extractsRuntimeDecisionArgumentsFromResponsesOutput() throws Exception {
        JSONObject response = new JSONObject()
                .put("output", new JSONArray().put(new JSONObject()
                        .put("type", "function_call")
                        .put("call_id", "call_1")
                        .put("name", ReplyTimerToolSession.TOOL_NAME)
                        .put("arguments", "{\"operation\":\"schedule_reply_timer\",\"wake_at_ms\":123456789,\"reason\":\"食事が終わった後\",\"participation_decision\":\"none\",\"willingness\":0.5,\"fear\":0.5,\"resolve\":0.5,\"personal_reason\":\"\"}")));

        JSONObject args = OpenAiClient.extractFunctionArgumentsForTest(
                response, ReplyTimerToolSession.TOOL_NAME);
        assertEquals(ReplyTimerToolSession.OP_REPLY_TIMER, args.getString("operation"));
        assertEquals(123456789L, args.getLong("wake_at_ms"));
        assertEquals("食事が終わった後", args.getString("reason"));
        assertEquals(8, args.length());
    }

    @Test
    public void structuredToolExposesParticipationOperation() {
        JSONObject policy = ReplyTimerRuntimeContext.dungeonParticipationPolicy();
        assertEquals("global_workspace", policy.optString("decision_owner"));
        assertEquals(ReplyTimerToolSession.TOOL_NAME, policy.optString("structured_tool"));
        assertEquals(ReplyTimerToolSession.OP_DUNGEON_PARTICIPATION,
                policy.optString("operation"));
    }

    @Test
    public void doesNotTreatOtherFunctionsAsRuntimeDecision() throws Exception {
        JSONObject response = new JSONObject()
                .put("output", new JSONArray().put(new JSONObject()
                        .put("type", "function_call")
                        .put("call_id", "call_2")
                        .put("name", "other_tool")
                        .put("arguments", "{}")));
        assertNull(OpenAiClient.extractFunctionArgumentsForTest(
                response, ReplyTimerToolSession.TOOL_NAME));
    }

    @Test
    public void toolIsScopedToGlobalWorkspacePrompt() {
        assertTrue(OpenAiClient.isGlobalWorkspacePrompt(
                "You are the existing Global Workspace of a brain-inspired NPC cognitive architecture."));
        assertFalse(OpenAiClient.isGlobalWorkspacePrompt(
                "You are the perception function inside a brain-inspired NPC cognitive architecture."));
    }
}
