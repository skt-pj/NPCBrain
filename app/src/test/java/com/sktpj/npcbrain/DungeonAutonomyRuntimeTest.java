package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DungeonAutonomyRuntimeTest {
    @Test
    public void onlyExplicitBrainEnterActionStartsDungeon() {
        assertTrue(DungeonAutonomyRuntime.wantsDungeon(decision("ENTER_DUNGEON")));
        assertTrue(DungeonAutonomyRuntime.wantsDungeon(decision("enter_dungeon")));
        assertFalse(DungeonAutonomyRuntime.wantsDungeon(decision("KEEP_CURRENT_ACTIVITY")));
        assertFalse(DungeonAutonomyRuntime.wantsDungeon(decision("ダンジョンに行くかもしれない")));
        assertFalse(DungeonAutonomyRuntime.wantsDungeon(null));
    }

    private static BrainEngine.Decision decision(String action) {
        return new BrainEngine.Decision(
                action,
                "",
                action,
                "",
                BrainCommunicationDecision.none(),
                new JSONObject());
    }
}
