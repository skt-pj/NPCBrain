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

    @Test
    public void chatCanSubmitDuringSpontaneousButNotUserProcessing() {
        assertTrue(ConversationUiPolicy.canSubmitMessage(false, null, false));
        assertTrue(ConversationUiPolicy.canSubmitMessage(true, null, false));
        assertTrue(ConversationUiPolicy.canSubmitMessage(true, "", false));
        assertFalse(ConversationUiPolicy.canSubmitMessage(true, "room_a", false));
        assertFalse(ConversationUiPolicy.canSubmitMessage(false, null, true));
        assertFalse(ConversationUiPolicy.canSubmitMessage(true, null, true));
    }

    @Test
    public void chatConsumesSystemBackButRoomListDoesNot() {
        assertTrue(ConversationUiPolicy.consumesSystemBack("room_a"));
        assertFalse(ConversationUiPolicy.consumesSystemBack(null));
    }
}
