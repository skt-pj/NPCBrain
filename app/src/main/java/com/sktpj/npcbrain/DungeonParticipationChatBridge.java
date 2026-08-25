package com.sktpj.npcbrain;

import android.content.Context;

/**
 * v0.4.27: participation is owned by the same Global Workspace cycle that makes the
 * character decision. The old post-hoc utterance/keyword observer is intentionally retired.
 */
final class DungeonParticipationChatBridge {
    private DungeonParticipationChatBridge() {
    }

    static void install(DemoActivityV032 activity) {
        ConversationSendQueueBridge.install(activity);
    }

    static void process(Context context) {
        // No-op by design. Do not reconstruct a psychological decision from emitted text.
    }
}
