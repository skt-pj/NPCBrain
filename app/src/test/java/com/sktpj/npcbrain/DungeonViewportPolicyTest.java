package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DungeonViewportPolicyTest {
    @Test
    public void initialViewportCentersPlayerAndClampsAtMapEdges() {
        assertEquals(4, DungeonViewportPolicy.initialStart(8, 9, 17));
        assertEquals(0, DungeonViewportPolicy.initialStart(1, 9, 17));
        assertEquals(8, DungeonViewportPolicy.initialStart(16, 9, 17));
        assertEquals(0, DungeonViewportPolicy.initialStart(4, 9, 7));
    }

    @Test
    public void cameraStaysFixedWhilePlayerMovesInsideViewportDeadZone() {
        assertEquals(4, DungeonViewportPolicy.followStart(4, 8, 9, 17));
        assertEquals(4, DungeonViewportPolicy.followStart(4, 6, 9, 17));
        assertEquals(4, DungeonViewportPolicy.followStart(4, 10, 9, 17));
        assertEquals(4, DungeonViewportPolicy.followStart(4, 11, 9, 17));
        assertEquals(4, DungeonViewportPolicy.followStart(4, 5, 9, 17));
    }

    @Test
    public void cameraOnlyFollowsWhenPlayerEntersOuterEdgeCell() {
        assertEquals(5, DungeonViewportPolicy.followStart(4, 12, 9, 17));
        assertEquals(3, DungeonViewportPolicy.followStart(4, 4, 9, 17));
        assertEquals(8, DungeonViewportPolicy.followStart(8, 16, 9, 17));
        assertEquals(0, DungeonViewportPolicy.followStart(0, 0, 9, 17));
    }
}
