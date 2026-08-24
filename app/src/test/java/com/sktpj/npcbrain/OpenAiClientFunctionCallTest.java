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
    public void extractsScheduleReplyTimerArgumentsFromResponsesOutput() throws Exception {
        JSONObject response = new JSONObject()
                .put("output", new JSONArray().put(new JSONObject()
                        .put("type", "function_call")
                        .put("call_id", "call_1")
                        .put("name", ReplyTimerToolSession.TOOL_NAME)
                        .put("arguments", "{\"wake_at_ms\":123456789,\"reason\":\"食事が終わった後\"}")));

        JSONObject args = OpenAiClient.extractFunctionArgumentsForTest(
                response, ReplyTimerToolSession.TOOL_NAME);
        assertEquals(123456789L, args.getLong("wake_at_ms"));
        assertEquals("食事が終わった後", args.getString("reason"));
        assertEquals(2, args.length());
    }

    @Test
    public void doesNotTreatOtherFunctionsAsReplyTimer() throws Exception {
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
