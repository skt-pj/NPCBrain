package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DungeonProgressPolicyTest {
    @Test
    public void brainEvadeExpiresWhenThreatIsNoLongerImmediate() {
        DungeonState state = openState(9, 9, 4, 4, 7, 7, 10);
        state.enemies.add(new DungeonState.Enemy("enemy", 7, 4, 4));
        DungeonPerception.refreshExploration(state);
        DungeonIntent evade = brainIntent(DungeonIntent.EVADE, 0);

        assertEquals(DungeonIntent.EXPLORE,
                DungeonPersonalityPolicy.effectiveMode(state, evade));

        state.enemies.get(0).x = 6;
        assertEquals(DungeonIntent.EVADE,
                DungeonPersonalityPolicy.effectiveMode(state, evade));

        state.turn = 3;
        assertEquals(DungeonIntent.EXPLORE,
                DungeonPersonalityPolicy.effectiveMode(state, evade));
    }

    @Test
    public void criticalLowHpCanKeepEvadingWhileThreatIsNear() {
        DungeonState state = openState(9, 9, 4, 4, 7, 7, 3);
        state.enemies.add(new DungeonState.Enemy("enemy", 7, 4, 4));
        DungeonPerception.refreshExploration(state);
        state.turn = 20;

        assertEquals(DungeonIntent.EVADE,
                DungeonPersonalityPolicy.effectiveMode(
                        state, brainIntent(DungeonIntent.EVADE, 0)));
    }

    @Test
    public void knownStairsUseKnownBfsInsteadOfManhattanWallDirection() {
        int width = 7;
        int height = 7;
        int[][] tiles = filled(width, height, DungeonState.WALL);
        boolean[][] visited = new boolean[height][width];
        int[][] path = {
                {1, 1}, {1, 2}, {1, 3}, {2, 3}, {3, 3}, {4, 3}
        };
        for (int[] point : path) {
            tiles[point[1]][point[0]] = DungeonState.FLOOR;
            visited[point[1]][point[0]] = true;
        }
        tiles[3][4] = DungeonState.STAIRS;
        DungeonState state = new DungeonState(
                1, 0, width, height, tiles, visited,
                1, 1, 10, 10, 4L, "", new ArrayList<>());

        assertEquals(DungeonPersonalityPolicy.Direction.DOWN,
                DungeonPersonalityPolicy.progressDirection(state));
    }

    @Test
    public void frontierRouteDoesNotDependOnHiddenTileContents() {
        DungeonState floorHidden = frontierState(DungeonState.FLOOR);
        DungeonState wallHidden = frontierState(DungeonState.WALL);

        assertEquals(DungeonPersonalityPolicy.Direction.RIGHT,
                DungeonPersonalityPolicy.progressDirection(floorHidden));
        assertEquals(DungeonPersonalityPolicy.Direction.RIGHT,
                DungeonPersonalityPolicy.progressDirection(wallHidden));
    }

    @Test
    public void generatedFloorsWithoutEnemiesEventuallyReachStairs() {
        DungeonPersonalityPolicy.Traits cautious =
                new DungeonPersonalityPolicy.Traits(10, 95, 90, 60, 45);
        for (long seed = 1; seed <= 24; seed++) {
            DungeonState state = DungeonGenerator.generate(seed * 991L, 1);
            state.enemies.clear();
            int initialFloor = state.floor;
            for (int turn = 0; turn < 360 && state.floor == initialFloor; turn++) {
                DungeonIntent local = DungeonIntent.localFallback(state, cautious, "progress-test");
                state = DungeonEngine.step(state, cautious, local);
            }
            assertTrue("seed=" + seed + " floor did not progress", state.floor > initialFloor);
        }
    }

    @Test
    public void staleEvadeIntentStillClearsKnownStairsWhileEnemyChases() {
        DungeonState state = corridorChaseState();
        DungeonPersonalityPolicy.Traits cautious =
                new DungeonPersonalityPolicy.Traits(5, 100, 100, 80, 20);
        DungeonIntent staleEvade = brainIntent(DungeonIntent.EVADE, 0);
        int initialFloor = state.floor;

        for (int i = 0; i < 12 && state.floor == initialFloor; i++) {
            state = DungeonEngine.step(state, cautious, staleEvade);
        }

        assertTrue("stale evade should not prevent floor clear", state.floor > initialFloor);
    }

    private static DungeonIntent brainIntent(String mode, int turn) {
        return new DungeonIntent(
                mode,
                DungeonPersonalityPolicy.Direction.LEFT,
                "",
                1.0,
                "test",
                DungeonIntent.SOURCE_BRAIN,
                1,
                turn);
    }

    private static DungeonState frontierState(int hiddenTile) {
        int width = 12;
        int height = 7;
        int[][] tiles = filled(width, height, DungeonState.WALL);
        boolean[][] visited = new boolean[height][width];
        for (int x = 2; x <= 7; x++) {
            tiles[2][x] = DungeonState.FLOOR;
            visited[2][x] = true;
        }
        // x=8 is six cells from the player and therefore genuinely unobserved.
        // Its hidden tile type must not change the known frontier route.
        tiles[2][8] = hiddenTile;
        tiles[5][10] = DungeonState.STAIRS;
        return new DungeonState(
                1, 0, width, height, tiles, visited,
                2, 2, 10, 10, 9L, "", new ArrayList<>());
    }

    private static DungeonState corridorChaseState() {
        int width = 10;
        int height = 5;
        int[][] tiles = filled(width, height, DungeonState.WALL);
        boolean[][] visited = new boolean[height][width];
        for (int x = 1; x <= 8; x++) {
            tiles[2][x] = DungeonState.FLOOR;
            visited[2][x] = true;
        }
        tiles[2][8] = DungeonState.STAIRS;
        List<DungeonState.Enemy> enemies = new ArrayList<>();
        enemies.add(new DungeonState.Enemy("chaser", 1, 2, 5));
        return new DungeonState(
                1, 0, width, height, tiles, visited,
                2, 2, 10, 10, 17L, "", enemies);
    }

    private static DungeonState openState(
            int width,
            int height,
            int playerX,
            int playerY,
            int stairsX,
            int stairsY,
            int hp
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
        return new DungeonState(
                1, 0, width, height, tiles, visited,
                playerX, playerY, hp, 10, 1L, "", new ArrayList<>());
    }

    private static int[][] filled(int width, int height, int value) {
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) result[y][x] = value;
        }
        return result;
    }
}
