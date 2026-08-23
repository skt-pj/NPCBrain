package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NpcStatusPolicyTest {
    @Test
    public void selectorKeepsAddedNpc() {
        List<String> ids = NpcStatusPolicy.selectorNpcIds(
                Arrays.asList("npc1", "npc2", "npc3", "npc3"));
        assertEquals(Arrays.asList("npc1", "npc2", "npc3"), ids);
    }

    @Test
    public void replyStateDistinguishesMessageAndSilentDecision() {
        assertEquals(NpcStatusPolicy.REPLY_SENT,
                NpcStatusPolicy.replyState("npc3", "npc3", false));
        assertEquals(NpcStatusPolicy.REPLY_SILENT,
                NpcStatusPolicy.replyState("npc3", "decision_npc3", false));
        assertEquals(NpcStatusPolicy.REPLY_SPONTANEOUS,
                NpcStatusPolicy.replyState("npc3", "runtime_decision_npc3", false));
        assertEquals(NpcStatusPolicy.REPLY_THINKING,
                NpcStatusPolicy.replyState("npc3", "decision_npc3", true));
    }
}
