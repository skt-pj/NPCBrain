package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DungeonEngineTest {
    @Test
    public void generatedMapsReachStairsAcrossSeeds() {
        for (long seed = 1; seed <= 30; seed++) {
            DungeonState state = DungeonGenerator.generate(seed, 1);
            assertNotNull(state);
            assertEquals(DungeonState.WALL, state.tileAt(0, 0));
            assertTrue(state.stairsX() >= 0);
            assertTrue(DungeonGenerator.isReachable(
                    state,
                    state.playerX,
                    state.playerY,
                    state.stairsX(),
                    state.stairsY()));
        }
    }

    @Test
    public void playerAttackOnlyWorksAtManhattanDistanceOne() {
        DungeonState state = openState(5, 5, 2, 2, 3, 3);
        DungeonState.Enemy adjacent = new DungeonState.Enemy("adjacent", 3, 2, 4);
        DungeonState.Enemy distant = new DungeonState.Enemy("distant", 3, 3, 4);
        state.enemies.add(adjacent);
        state.enemies.add(distant);

        assertTrue(DungeonEngine.playerAttack(state, adjacent));
        assertEquals(2, adjacent.hp);
        assertFalse(DungeonEngine.playerAttack(state, distant));
        assertEquals(4, distant.hp);
        assertTrue(DungeonEngine.canAttack(2, 2, 2, 3));
        assertFalse(DungeonEngine.canAttack(2, 2, 4, 2));
    }

    @Test
    public void onePlayerActionRunsOneEnemyPhaseAndOneTurn() {
        DungeonState state = openState(5, 5, 2, 2, 3, 3);
        state.enemies.add(new DungeonState.Enemy("enemy", 3, 2, 4));
        DungeonPersonalityPolicy.Traits traits = new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50);

        DungeonState next = DungeonEngine.step(
                state,
                traits,
                DungeonPersonalityPolicy.Direction.WAIT);

        assertEquals(1, next.turn);
        assertEquals(9, next.hp);
    }

    @Test
    public void movingOntoStairsAdvancesFloorAndRegeneratesMap() {
        DungeonState state = openState(5, 5, 1, 2, 2, 2);
        state.seed = 12345L;
        DungeonPersonalityPolicy.Traits traits = new DungeonPersonalityPolicy.Traits(50, 50, 50, 100, 50);

        DungeonState next = DungeonEngine.step(
                state,
                traits,
                DungeonPersonalityPolicy.Direction.RIGHT);

        assertEquals(2, next.floor);
        assertEquals(1, next.turn);
        assertEquals(DungeonGenerator.WIDTH, next.width);
        assertEquals(DungeonGenerator.HEIGHT, next.height);
        assertNotEquals(state.seed, next.seed);
        assertTrue(next.lastAction.contains("2F"));
    }

    @Test
    public void traitsDoNotCreateLocalPsychologicalDirectionDecision() {
        DungeonState state = openState(7, 7, 3, 3, 5, 5);
        DungeonPerception.refreshExploration(state);
        state.enemies.add(new DungeonState.Enemy("enemy", 4, 3, 4));
        DungeonPersonalityPolicy.Traits outgoing =
                new DungeonPersonalityPolicy.Traits(100, 0, 0, 0, 100);
        DungeonPersonalityPolicy.Traits cautious =
                new DungeonPersonalityPolicy.Traits(0, 100, 100, 100, 0);
        DungeonIntent local = new DungeonIntent(
                DungeonIntent.EXPLORE,
                DungeonPersonalityPolicy.Direction.WAIT,
                "",
                0.25,
                "local",
                DungeonIntent.SOURCE_LOCAL,
                1,
                0);
        DungeonPlan neutral = new DungeonPlan(
                0.5, 0.5, 0.5, 0.5,
                "neutral", DungeonPlan.SOURCE_LOCAL,
                DungeonObjective.NONE, 0, 1, 0);

        assertEquals(
                DungeonPersonalityPolicy.scoreDirection(
                        state, outgoing, DungeonPersonalityPolicy.Direction.RIGHT, local, neutral),
                DungeonPersonalityPolicy.scoreDirection(
                        state, cautious, DungeonPersonalityPolicy.Direction.RIGHT, local, neutral),
                0.000001);
        assertEquals(
                DungeonPersonalityPolicy.scoreDirection(
                        state, outgoing, DungeonPersonalityPolicy.Direction.LEFT, local, neutral),
                DungeonPersonalityPolicy.scoreDirection(
                        state, cautious, DungeonPersonalityPolicy.Direction.LEFT, local, neutral),
                0.000001);
    }

    @Test
    public void legalSameCycleBrainDirectionExecutesWithoutLocalPsychologicalOverride() {
        DungeonState state = openState(7, 7, 3, 3, 5, 5);
        DungeonPerception.refreshExploration(state);
        DungeonIntent brain = new DungeonIntent(
                DungeonIntent.EXPLORE,
                DungeonPersonalityPolicy.Direction.RIGHT,
                "",
                1.0,
                "Brain chose right",
                DungeonIntent.SOURCE_BRAIN,
                1,
                0);
        DungeonPlan plan = new DungeonPlan(
                0.0, 0.0, 1.0, 1.0,
                "plan", DungeonPlan.SOURCE_BRAIN,
                DungeonObjective.NONE, 0, 1, 0);
        DungeonPersonalityPolicy.Traits traits =
                new DungeonPersonalityPolicy.Traits(0, 100, 100, 100, 0);

        assertEquals(DungeonPersonalityPolicy.Direction.RIGHT,
                DungeonEngine.legalBrainDirection(state, brain));
        DungeonState next = DungeonEngine.step(state, traits, brain, plan);
        assertEquals(4, next.playerX);
        assertEquals(3, next.playerY);
    }

    @Test
    public void hardWorldConstraintCanRejectBrainDirection() {
        DungeonState state = openState(7, 7, 3, 3, 5, 5);
        state.tiles[2][3] = DungeonState.WALL;
        DungeonPerception.refreshExploration(state);
        DungeonIntent brain = new DungeonIntent(
                DungeonIntent.EXPLORE,
                DungeonPersonalityPolicy.Direction.UP,
                "",
                1.0,
                "Brain chose blocked tile",
                DungeonIntent.SOURCE_BRAIN,
                1,
                0);

        assertNull(DungeonEngine.legalBrainDirection(state, brain));
        DungeonState next = DungeonEngine.step(
                state,
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50),
                brain,
                null);
        assertFalse(next.playerX == 3 && next.playerY == 2);
    }

    @Test
    public void stateJsonRoundTripPreservesProgress() {
        DungeonState state = DungeonGenerator.generate(9876L, 4);
        state.turn = 31;
        state.hp = 7;
        state.lastAction = "test-action";
        state.markVisited(state.playerX, state.playerY);

        DungeonState restored = DungeonState.fromJson(state.toJson());

        assertNotNull(restored);
        assertEquals(state.floor, restored.floor);
        assertEquals(state.turn, restored.turn);
        assertEquals(state.hp, restored.hp);
        assertEquals(state.playerX, restored.playerX);
        assertEquals(state.playerY, restored.playerY);
        assertEquals(state.stairsX(), restored.stairsX());
        assertEquals(state.stairsY(), restored.stairsY());
        assertEquals(state.enemies.size(), restored.enemies.size());
        assertEquals("test-action", restored.lastAction);
        assertEquals("npc1_state", DungeonStore.key("npc1"));
        assertEquals("npc2_state", DungeonStore.key("npc2"));
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
