package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcBrainResetPolicyTest {
    @Test
    public void replyTimerScopeMatchesOnlySelectedNpc() {
        assertTrue(ReplyTimerStore.sameNpc("npc1", "npc1"));
        assertTrue(ReplyTimerStore.sameNpc(" npc3 ", "npc3"));
        assertFalse(ReplyTimerStore.sameNpc("npc1", "npc2"));
        assertFalse(ReplyTimerStore.sameNpc("", "npc1"));
        assertFalse(ReplyTimerStore.sameNpc(null, "npc1"));
    }
}
