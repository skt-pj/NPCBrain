package com.sktpj.npcbrain;

final class ConversationUiPolicy {
    private ConversationUiPolicy() {}

    static boolean showsProcessingInRoom(boolean processing, String currentRoomId, String processingRoomId) {
        return processing
                && currentRoomId != null
                && currentRoomId.equals(processingRoomId);
    }

    static boolean consumesSystemBack(String currentRoomId) {
        return currentRoomId != null;
    }
}
