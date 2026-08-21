package com.sktpj.npcbrain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ConversationUiPolicyTest {
    @Test
    public void processingIndicatorIsShownOnlyInOriginRoom() {
        assertTrue(ConversationUiPolicy.showsProcessingInRoom(true, "room_a", "room_a"));
        assertFalse(ConversationUiPolicy.showsProcessingInRoom(true, "room_b", "room_a"));
        assertFalse(ConversationUiPolicy.showsProcessingInRoom(true, null, "room_a"));
    }

    @Test
    public void noIndicatorWhenProcessingIsFinished() {
        assertFalse(ConversationUiPolicy.showsProcessingInRoom(false, "room_a", "room_a"));
    }
}
