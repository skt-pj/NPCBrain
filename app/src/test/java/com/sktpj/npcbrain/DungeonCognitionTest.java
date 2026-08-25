package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DungeonCognitionTest {
    @Test
    public void hiddenStairsAreNotGroundedUntilExplored() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        DungeonPerception.refreshExploration(state);

        JSONObject hidden = DungeonPerception.buildRuntimeJson(state, "periodic");
        JSONObject hiddenStairs = hidden.optJSONObject("stairs");
        assertNotNull(hiddenStairs);
        assertFalse(hiddenStairs.optBoolean("known", true));
        assertFalse(hiddenStairs.has("x"));
        assertFalse(hiddenStairs.has("y"));

        state.markVisited(7, 7);
        JSONObject known = DungeonPerception.buildRuntimeJson(state, "stairs_spotted");
        JSONObject knownStairs = known.optJSONObject("stairs");
        assertTrue(knownStairs.optBoolean("known", false));
        assertEquals(7, knownStairs.optInt("x", -1));
        assertEquals(7, knownStairs.optInt("y", -1));
    }

    @Test
    public void invisibleEnemiesAreNotGrounded() {
        DungeonState state = openState(13, 13, 2, 2, 10, 10);
        state.enemies.add(new DungeonState.Enemy("far", 10, 2, 4));
        state.enemies.add(new DungeonState.Enemy("near", 3, 2, 4));
        DungeonPerception.refreshExploration(state);

        JSONObject runtime = DungeonPerception.buildRuntimeJson(state, "enemy_spotted");
        assertEquals(1, runtime.optJSONArray("visible_enemies").length());
        assertEquals("near", runtime.optJSONArray("visible_enemies")
                .optJSONObject(0).optString("id"));
    }

    @Test
    public void cognitionGateTriggersOnSalientChangesAndPeriodicBoundary() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        DungeonPerception.refreshExploration(state);
        DungeonCognitionGate.Signal first = DungeonCognitionGate.snapshot(state);
        assertEquals(DungeonCognitionGate.FLOOR_START,
                DungeonCognitionGate.reason(null, first, 0));

        state.enemies.add(new DungeonState.Enemy("enemy", 3, 2, 4));
        DungeonCognitionGate.Signal enemy = DungeonCognitionGate.snapshot(state);
        assertEquals(DungeonCognitionGate.ENEMY_SPOTTED,
                DungeonCognitionGate.reason(first, enemy, 0));

        DungeonCognitionGate.Signal same = DungeonCognitionGate.snapshot(state);
        state.turn = 11;
        DungeonCognitionGate.Signal t11 = DungeonCognitionGate.snapshot(state);
        assertEquals("", DungeonCognitionGate.reason(same, t11, 0));
        state.turn = 12;
        DungeonCognitionGate.Signal t12 = DungeonCognitionGate.snapshot(state);
        assertEquals(DungeonCognitionGate.PERIODIC,
                DungeonCognitionGate.reason(same, t12, 0));

        assertTrue(DungeonCognitionGate.isCognitionTrigger(DungeonCognitionGate.FLOOR_START));
        assertTrue(DungeonCognitionGate.isCognitionTrigger(DungeonCognitionGate.ENEMY_SPOTTED));
        assertTrue(DungeonCognitionGate.isCognitionTrigger(DungeonCognitionGate.STAIRS_SPOTTED));
        assertTrue(DungeonCognitionGate.isCognitionTrigger(DungeonCognitionGate.HP_RISK));
        assertTrue(DungeonCognitionGate.isCognitionTrigger(DungeonCognitionGate.COMBAT_CHANGE));
        assertTrue(DungeonCognitionGate.isCognitionTrigger(DungeonCognitionGate.PERIODIC));
        assertFalse(DungeonCognitionGate.isCognitionTrigger(""));
    }

    @Test
    public void pendingTriggerKeepsHighestPriorityIncludingProgressStall() {
        assertEquals(DungeonCognitionGate.PROGRESS_STALLED,
                DungeonCognitionGate.mergePending("", DungeonCognitionGate.PROGRESS_STALLED));
        assertEquals(DungeonCognitionGate.PROGRESS_STALLED,
                DungeonCognitionGate.mergePending(
                        DungeonCognitionGate.ENEMY_SPOTTED,
                        DungeonCognitionGate.PROGRESS_STALLED));
        assertEquals(DungeonCognitionGate.OBJECTIVE_CHANGED,
                DungeonCognitionGate.mergePending(
                        DungeonCognitionGate.PERIODIC,
                        DungeonCognitionGate.OBJECTIVE_CHANGED));
    }

    @Test
    public void intentParserRejectsIllegalEnvironmentAction() throws Exception {
        JSONObject invalid = new JSONObject()
                .put("type", "teleport")
                .put("direction", "up")
                .put("intent", "engage")
                .put("confidence", 2.0);
        assertEquals(null, DungeonIntent.fromEnvironmentAction(invalid, 1, 0, "bad"));

        JSONObject valid = new JSONObject()
                .put("type", "move")
                .put("direction", "right")
                .put("intent", "explore")
                .put("confidence", 2.0);
        DungeonIntent intent = DungeonIntent.fromEnvironmentAction(valid, 2, 8, "go");
        assertNotNull(intent);
        assertEquals(DungeonIntent.EXPLORE, intent.mode);
        assertEquals(DungeonPersonalityPolicy.Direction.RIGHT, intent.preferredDirection);
        assertEquals(1.0, intent.confidence, 0.0001);
    }

    @Test
    public void seekStairsDoesNotUseHiddenStairCoordinate() {
        DungeonState eastHidden = openState(15, 15, 2, 2, 12, 2);
        DungeonState southHidden = openState(15, 15, 2, 2, 2, 12);
        DungeonPerception.refreshExploration(eastHidden);
        DungeonPerception.refreshExploration(southHidden);
        assertFalse(DungeonPerception.stairsKnown(eastHidden));
        assertFalse(DungeonPerception.stairsKnown(southHidden));

        DungeonPersonalityPolicy.Traits traits =
                new DungeonPersonalityPolicy.Traits(0, 0, 0, 100, 0);
        DungeonIntent seek = new DungeonIntent(
                DungeonIntent.SEEK_STAIRS,
                DungeonPersonalityPolicy.Direction.WAIT,
                "", 1.0, "", DungeonIntent.SOURCE_BRAIN, 1, 0);

        double scoreA = DungeonPersonalityPolicy.scoreDirection(
                eastHidden, traits, DungeonPersonalityPolicy.Direction.RIGHT, seek);
        double scoreB = DungeonPersonalityPolicy.scoreDirection(
                southHidden, traits, DungeonPersonalityPolicy.Direction.RIGHT, seek);
        assertEquals(scoreA, scoreB, 0.0001);
    }

    @Test
    public void brainPreferredDirectionCannotForceWallMove() {
        DungeonState state = openState(7, 7, 1, 1, 5, 5);
        state.tiles[1][2] = DungeonState.WALL;
        DungeonPerception.refreshExploration(state);
        DungeonPersonalityPolicy.Traits traits =
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50);
        DungeonIntent intent = new DungeonIntent(
                DungeonIntent.EXPLORE,
                DungeonPersonalityPolicy.Direction.RIGHT,
                "", 1.0, "", DungeonIntent.SOURCE_BRAIN, 1, 0);

        assertEquals(-Double.MAX_VALUE,
                DungeonPersonalityPolicy.scoreDirection(
                        state, traits, DungeonPersonalityPolicy.Direction.RIGHT, intent),
                0.0);
    }

    private static DungeonState openState(
            int width,
            int height,
            int playerX,
            int playerY,
            int stairsX,
            int stairsY
    ) {
        int[][] tiles = new int[height][width];
        boolean[][] visited = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = x == 0 || y == 0 || x == width - 1 || y == height - 1
                        ? DungeonState.WALL : DungeonState.FLOOR;
            }
        }
        tiles[stairsY][stairsX] = DungeonState.STAIRS;
        List<DungeonState.Enemy> enemies = new ArrayList<>();
        return new DungeonState(
                1,
                0,
                width,
                height,
                tiles,
                visited,
                playerX,
                playerY,
                10,
                10,
                1L,
                "",
                enemies);
    }
}
