package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DungeonDeathTest {
    @Test
    public void zeroHpIsTerminalAndDoesNotRestart() {
        DungeonState state = openState(10);
        state.floor = 4;
        state.turn = 37;
        state.seed = 987654321L;
        state.hp = 0;

        DungeonStepResult result = DungeonEngine.stepDetailed(
                state,
                traits(),
                DungeonPersonalityPolicy.Direction.WAIT);

        assertSame(state, result.state);
        assertEquals(4, result.state.floor);
        assertEquals(37, result.state.turn);
        assertEquals(987654321L, result.state.seed);
        assertEquals(0, result.state.hp);
        assertTrue(result.state.lastAction.contains("死亡"));
    }

    @Test
    public void fatalEnemyHitStopsFurtherTurns() {
        DungeonState state = openState(1);
        state.enemies.add(new DungeonState.Enemy("enemy", 3, 2, 4));

        DungeonStepResult fatal = DungeonEngine.stepDetailed(
                state,
                traits(),
                DungeonPersonalityPolicy.Direction.WAIT);

        assertEquals(0, fatal.state.hp);
        assertEquals(1, fatal.state.turn);
        assertEquals(1, fatal.state.floor);
        assertTrue(fatal.state.lastAction.contains("死亡"));

        DungeonStepResult afterDeath = DungeonEngine.stepDetailed(
                fatal.state,
                traits(),
                DungeonPersonalityPolicy.Direction.WAIT);

        assertSame(fatal.state, afterDeath.state);
        assertEquals(1, afterDeath.state.turn);
        assertEquals(0, afterDeath.state.hp);
    }

    private static DungeonState openState(int hp) {
        int width = 5;
        int height = 5;
        int[][] tiles = new int[height][width];
        boolean[][] visited = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = x == 0 || y == 0 || x == width - 1 || y == height - 1
                        ? DungeonState.WALL : DungeonState.FLOOR;
            }
        }
        tiles[3][3] = DungeonState.STAIRS;
        return new DungeonState(
                1,
                0,
                width,
                height,
                tiles,
                visited,
                2,
                2,
                hp,
                10,
                1L,
                "",
                new ArrayList<>());
    }

    private static DungeonPersonalityPolicy.Traits traits() {
        return new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50);
    }
}
