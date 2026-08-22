package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
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
    public void personalityChangesDirectionScores() {
        DungeonState combat = openState(5, 5, 2, 2, 2, 1);
        for (int y = 0; y < combat.height; y++) {
            for (int x = 0; x < combat.width; x++) combat.visited[y][x] = true;
        }
        combat.enemies.add(new DungeonState.Enemy("enemy", 3, 2, 4));

        DungeonPersonalityPolicy.Traits outgoing = new DungeonPersonalityPolicy.Traits(100, 0, 0, 0, 0);
        DungeonPersonalityPolicy.Traits cautious = new DungeonPersonalityPolicy.Traits(0, 100, 100, 0, 0);

        double outgoingToward = DungeonPersonalityPolicy.scoreDirection(
                combat, outgoing, DungeonPersonalityPolicy.Direction.RIGHT);
        double outgoingAway = DungeonPersonalityPolicy.scoreDirection(
                combat, outgoing, DungeonPersonalityPolicy.Direction.LEFT);
        double cautiousToward = DungeonPersonalityPolicy.scoreDirection(
                combat, cautious, DungeonPersonalityPolicy.Direction.RIGHT);
        double cautiousAway = DungeonPersonalityPolicy.scoreDirection(
                combat, cautious, DungeonPersonalityPolicy.Direction.LEFT);

        assertTrue(outgoingToward > outgoingAway);
        assertTrue(cautiousAway > cautiousToward);

        DungeonState progress = openState(5, 5, 2, 2, 2, 1);
        for (int y = 0; y < progress.height; y++) {
            for (int x = 0; x < progress.width; x++) progress.visited[y][x] = true;
        }
        DungeonPersonalityPolicy.Traits diligent = new DungeonPersonalityPolicy.Traits(0, 0, 0, 100, 0);
        assertTrue(DungeonPersonalityPolicy.scoreDirection(
                progress, diligent, DungeonPersonalityPolicy.Direction.UP)
                > DungeonPersonalityPolicy.scoreDirection(
                progress, diligent, DungeonPersonalityPolicy.Direction.LEFT));

        DungeonState explore = openState(5, 5, 2, 2, 3, 3);
        for (int y = 0; y < explore.height; y++) {
            for (int x = 0; x < explore.width; x++) explore.visited[y][x] = true;
        }
        explore.visited[1][2] = false;
        DungeonPersonalityPolicy.Traits curious = new DungeonPersonalityPolicy.Traits(0, 0, 0, 0, 100);
        assertTrue(DungeonPersonalityPolicy.scoreDirection(
                explore, curious, DungeonPersonalityPolicy.Direction.UP)
                > DungeonPersonalityPolicy.scoreDirection(
                explore, curious, DungeonPersonalityPolicy.Direction.LEFT));
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
