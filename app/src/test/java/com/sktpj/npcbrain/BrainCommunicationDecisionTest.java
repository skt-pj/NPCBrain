package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrainCommunicationDecisionTest {
    @Test
    public void missingCommunicationDefaultsToNone() {
        BrainCommunicationDecision decision = BrainCommunicationDecision.fromJson(new JSONObject());
        assertTrue(decision.valid());
        assertTrue(decision.isNone());
        assertEquals("", decision.targetId());
        assertEquals(0L, decision.deferUntilMs());
    }

    @Test
    public void parsesSendAndDefer() throws Exception {
        JSONObject sendRoot = new JSONObject()
                .put("communication", new JSONObject()
                        .put("decision", "send")
                        .put("target_id", "npc2")
                        .put("defer_until_ms", 0));
        BrainCommunicationDecision send = BrainCommunicationDecision.fromJson(sendRoot);
        assertTrue(send.valid());
        assertTrue(send.isSend());
        assertEquals("npc2", send.targetId());

        JSONObject deferRoot = new JSONObject()
                .put("communication", new JSONObject()
                        .put("decision", "defer")
                        .put("target_id", "user")
                        .put("defer_until_ms", 12345L));
        BrainCommunicationDecision defer = BrainCommunicationDecision.fromJson(deferRoot);
        assertTrue(defer.valid());
        assertTrue(defer.isDefer());
        assertEquals(12345L, defer.deferUntilMs());
    }

    @Test
    public void unknownDecisionIsInvalid() throws Exception {
        BrainCommunicationDecision decision = BrainCommunicationDecision.fromJson(
                new JSONObject().put("communication", new JSONObject().put("decision", "later"))
        );
        assertFalse(decision.valid());
        assertEquals("later", decision.decision());
    }
}
