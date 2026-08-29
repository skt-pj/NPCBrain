package com.sktpj.npcbrain;

/**
 * Legacy compatibility shell.
 *
 * Dungeon is an ordinary accessible area. Participation/consent state must not create a pause,
 * disable controls, alter the objective button, or add special willingness UI.
 */
final class DungeonConsentBridge {
    private DungeonConsentBridge() {
    }

    static void install(DungeonActivity activity) {
        // Intentionally no-op. Kept only so older source references remain binary/source compatible.
    }

    static boolean isParticipationPause(DungeonActivity activity) {
        return false;
    }
}
