package com.sktpj.npcbrain;

/**
 * Compatibility bridge retained for existing DungeonActivity wiring.
 * Solo exploration is owned by NpcWorldRuntimeV200 from v2.0.0.
 */
final class DungeonSoloProgressBridge {
    private DungeonSoloProgressBridge() {
    }

    static synchronized void install(DungeonActivity activity) {
        // Intentionally no-op. The screen observes persisted dungeon state only.
    }
}
