package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DungeonNpcStateCoordinatorTest {
    @Test
    public void ordinaryActiveAliveNpcCanAdvanceWithoutConsentState() {
        DungeonObjective none = DungeonObjective.none();
        DungeonState state = DungeonGenerator.generate(1234L, 1);
        assertTrue(DungeonNpcStateCoordinator.canAdvanceSnapshot(true, none, state));
        assertTrue(DungeonNpcStateCoordinator.canAdvanceSnapshot(true, none, null));
    }

    @Test
    public void hardWorldStateStillStopsNpc() {
        DungeonState alive = DungeonGenerator.generate(4321L, 1);
        assertTrue(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, DungeonObjective.reachTop(1L), alive));

        DungeonState dead = DungeonGenerator.generate(4321L, 1);
        dead.hp = 0;
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, DungeonObjective.none(), dead));

        DungeonState completed = DungeonGenerator.generate(4321L, DungeonObjective.TOP_FLOOR);
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, DungeonObjective.reachTop(1L), completed));

        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                false, DungeonObjective.none(), alive));
    }

    @Test
    public void objectiveNoneDoesNotAddANewGate() {
        assertTrue(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true,
                DungeonObjective.none(),
                null));
    }
}
