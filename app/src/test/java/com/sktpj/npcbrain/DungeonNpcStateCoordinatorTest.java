package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DungeonNpcStateCoordinatorTest {
    @Test
    public void onlyAcceptedParticipationCanAdvance() {
        DungeonObjective none = DungeonObjective.none();
        DungeonState state = DungeonGenerator.generate(1234L, 1);

        assertTrue(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, participation(DungeonParticipationState.ACCEPT), none, state));
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, DungeonParticipationState.initial(), none, state));
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, participation(DungeonParticipationState.REFUSE), none, state));
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, participation(DungeonParticipationState.HESITATE), none, state));
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, participation(DungeonParticipationState.WITHDRAW), none, state));
    }

    @Test
    public void hardWorldStateStillStopsAcceptedNpc() {
        DungeonParticipationState accepted = participation(DungeonParticipationState.ACCEPT);
        DungeonState alive = DungeonGenerator.generate(4321L, 1);
        assertTrue(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, accepted, DungeonObjective.reachTop(1L), alive));

        DungeonState dead = DungeonGenerator.generate(4321L, 1);
        dead.hp = 0;
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, accepted, DungeonObjective.none(), dead));

        DungeonState completed = DungeonGenerator.generate(4321L, DungeonObjective.TOP_FLOOR);
        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true, accepted, DungeonObjective.reachTop(1L), completed));

        assertFalse(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                false, accepted, DungeonObjective.none(), alive));
    }

    @Test
    public void objectiveNoneDoesNotAddANewGate() {
        assertTrue(DungeonNpcStateCoordinator.canAdvanceSnapshot(
                true,
                participation(DungeonParticipationState.ACCEPT),
                DungeonObjective.none(),
                null));
    }

    private static DungeonParticipationState participation(String stance) {
        return new DungeonParticipationState(stance, 0.5, 0.5, 0.5, "", 1L);
    }
}
