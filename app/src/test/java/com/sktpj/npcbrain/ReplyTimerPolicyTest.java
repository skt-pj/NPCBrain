package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplyTimerPolicyTest {
    @Test
    public void wakeMustBeGroundedFutureWithinBound() {
        long now = 1_000_000L;
        assertFalse(ReplyTimerPolicy.isValidWake(now, now - 1L));
        assertFalse(ReplyTimerPolicy.isValidWake(now, now + ReplyTimerPolicy.MIN_DELAY_MS - 1L));
        assertTrue(ReplyTimerPolicy.isValidWake(now, now + ReplyTimerPolicy.MIN_DELAY_MS));
        assertTrue(ReplyTimerPolicy.isValidWake(now, now + ReplyTimerPolicy.MAX_DELAY_MS));
        assertFalse(ReplyTimerPolicy.isValidWake(now, now + ReplyTimerPolicy.MAX_DELAY_MS + 1L));
    }

    @Test
    public void dueAndDelayedMessageIdAreDeterministic() {
        ReplyTimerTask task = new ReplyTimerTask(
                "conversation|npc3|direct_npc3|message-7",
                426500,
                ReplyTimerBinding.MODE_CONVERSATION,
                "npc3",
                "direct_npc3",
                "message-7",
                "event-7",
                null,
                null,
                5000L,
                "meal",
                1000L);
        assertFalse(ReplyTimerPolicy.isDue(task, 4999L));
        assertTrue(ReplyTimerPolicy.isDue(task, 5000L));
        assertEquals("reply_timer_npc3_message-7", ReplyTimerPolicy.delayedReplyMessageId(task));
        assertEquals(
                ReplyTimerPolicy.delayedReplyMessageId(task),
                ReplyTimerPolicy.delayedReplyMessageId(task));
    }
}
