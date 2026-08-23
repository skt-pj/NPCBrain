package com.sktpj.npcbrain;

final class ConversationUiPolicy {
    private ConversationUiPolicy() {}

    static boolean showsProcessingInRoom(boolean processing, String currentRoomId, String processingRoomId) {
        return processing
                && currentRoomId != null
                && currentRoomId.equals(processingRoomId);
    }

    static boolean canSubmitMessage(
            boolean processing,
            String processingRoomId,
            boolean hasQueuedUserMessage
    ) {
        if (hasQueuedUserMessage) return false;
        if (!processing) return true;
        return processingRoomId == null || processingRoomId.trim().isEmpty();
    }

    static boolean consumesSystemBack(String currentRoomId) {
        return currentRoomId != null;
    }
}
