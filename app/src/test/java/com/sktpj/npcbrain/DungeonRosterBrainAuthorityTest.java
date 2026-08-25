package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DungeonRosterBrainAuthorityTest {
    @Test
    public void backgroundExecutorUsesExactTurnBrainIntent() {
        DungeonState state = openState();
        DungeonIntent brain = new DungeonIntent(
                DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.Direction.RIGHT,
                "enemy",
                0.9,
                "attack now",
                DungeonIntent.SOURCE_BRAIN,
                state.floor,
                state.turn);
        DungeonMindStore.Snapshot mind = new DungeonMindStore.Snapshot(
                brain,
                null,
                new JSONArray(),
                new JSONObject(),
                DungeonMindStore.STATE_BRAIN,
                "",
                1L);

        DungeonIntent selected = DungeonRosterBridge.backgroundTurnIntent(
                state,
                new DungeonPersonalityPolicy.Traits(0, 100, 100, 0, 0),
                mind);

        assertTrue(selected.isBrain());
        assertEquals(DungeonPersonalityPolicy.Direction.RIGHT, selected.preferredDirection);
        assertEquals(DungeonIntent.ENGAGE, selected.mode);
    }

    @Test
    public void backgroundExecutorDoesNotReuseStaleOneTurnBrainAction() {
        DungeonState state = openState();
        DungeonIntent stale = new DungeonIntent(
                DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.Direction.RIGHT,
                "enemy",
                0.9,
                "old attack",
                DungeonIntent.SOURCE_BRAIN,
                state.floor,
                state.turn - 1);
        DungeonMindStore.Snapshot mind = new DungeonMindStore.Snapshot(
                stale,
                null,
                new JSONArray(),
                new JSONObject(),
                DungeonMindStore.STATE_BRAIN,
                "",
                1L);

        DungeonIntent selected = DungeonRosterBridge.backgroundTurnIntent(
                state,
                new DungeonPersonalityPolicy.Traits(100, 0, 0, 100, 100),
                mind);

        assertEquals(DungeonIntent.SOURCE_LOCAL, selected.source);
        assertEquals(state.turn, selected.turn);
        assertEquals(DungeonPersonalityPolicy.Direction.WAIT, selected.preferredDirection);
    }

    private static DungeonState openState() {
        int width = 7;
        int height = 7;
        int[][] tiles = new int[height][width];
        boolean[][] visited = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = x == 0 || y == 0 || x == width - 1 || y == height - 1
                        ? DungeonState.WALL : DungeonState.FLOOR;
            }
        }
        tiles[5][5] = DungeonState.STAIRS;
        DungeonState state = new DungeonState(
                1,
                7,
                width,
                height,
                tiles,
                visited,
                3,
                3,
                10,
                10,
                99L,
                "",
                new ArrayList<>());
        DungeonPerception.refreshExploration(state);
        return state;
    }
}
