package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DungeonSharedWorldTest {
    @Test
    public void sameSharedFloorReplacesNpcSpecificMapAndEnemies() {
        DungeonState canonical = DungeonGenerator.generate(111L, 1);
        DungeonState other = DungeonGenerator.generate(999L, 1);
        other.turn = 12;
        other.hp = 7;
        int otherX = other.playerX;
        int otherY = other.playerY;

        DungeonSharedFloor shared = DungeonSharedFloor.fromState(
                canonical, canonical.playerX, canonical.playerY, 1L);
        DungeonState attached = shared.attach(other, false);

        assertEquals(canonical.seed, attached.seed);
        assertEquals(canonical.stairsX(), attached.stairsX());
        assertEquals(canonical.stairsY(), attached.stairsY());
        assertEquals(canonical.enemies.size(), attached.enemies.size());
        assertEquals(12, attached.turn);
        assertEquals(7, attached.hp);
        assertEquals(otherX, attached.playerX);
        assertEquals(otherY, attached.playerY);
        assertSameTiles(canonical, attached);
    }

    @Test
    public void sharedEnemyDamageIsVisibleToAnotherAttachedActor() {
        DungeonState canonical = openState(7, 7, 2, 2, 5, 5);
        canonical.enemies.add(new DungeonState.Enemy("enemy", 3, 2, 4));
        DungeonSharedFloor shared = DungeonSharedFloor.fromState(canonical, 2, 2, 1L);

        DungeonState actorA = shared.attach(openState(7, 7, 2, 2, 5, 5), false);
        DungeonState.Enemy enemy = actorA.enemies.get(0);
        assertTrue(DungeonEngine.playerAttack(actorA, enemy));
        assertEquals(2, enemy.hp);

        DungeonSharedFloor updated = shared.withWorld(actorA);
        DungeonState actorBBase = openState(7, 7, 4, 4, 5, 5);
        DungeonState actorB = updated.attach(actorBBase, false);
        assertEquals(2, actorB.enemies.get(0).hp);
    }

    @Test
    public void fogRemainsActorSpecificOnOneSharedFloor() {
        DungeonState canonical = openState(7, 7, 2, 2, 5, 5);
        DungeonSharedFloor shared = DungeonSharedFloor.fromState(canonical, 2, 2, 1L);
        DungeonState aBase = openState(7, 7, 2, 2, 5, 5);
        DungeonState bBase = openState(7, 7, 4, 4, 5, 5);
        aBase.markVisited(3, 2);

        DungeonState actorA = shared.attach(aBase, false);
        DungeonState actorB = shared.attach(bBase, false);

        assertTrue(actorA.visited[2][3]);
        assertFalse(actorB.visited[2][3]);
    }

    @Test
    public void peerOccupiedCellCannotBeEntered() {
        DungeonState state = openState(7, 7, 2, 2, 5, 5);
        DungeonTurnContext.register(
                state,
                "npc1",
                1L,
                state.playerX,
                state.playerY,
                Arrays.asList(new DungeonActorContext("npc2", 1, 3, 2, 10, 10)));

        DungeonEngine.stepDetailed(
                state,
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50),
                DungeonPersonalityPolicy.Direction.RIGHT);

        assertEquals(2, state.playerX);
        assertEquals(2, state.playerY);
        assertTrue(state.lastAction.contains("他の冒険者"));
    }

    @Test
    public void enemyTargetsCloserPeerAndDamagesOnlyThatActor() {
        DungeonState state = openState(9, 7, 2, 2, 7, 5);
        state.enemies.add(new DungeonState.Enemy("enemy", 5, 2, 4));
        DungeonTurnContext.register(
                state,
                "npc1",
                1L,
                state.playerX,
                state.playerY,
                Arrays.asList(new DungeonActorContext("npc2", 1, 6, 2, 10, 10)));

        DungeonEngine.stepDetailed(
                state,
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50),
                DungeonPersonalityPolicy.Direction.WAIT);

        assertEquals(10, state.hp);
        List<DungeonActorContext> peers = DungeonTurnContext.peers(state);
        assertEquals(1, peers.size());
        assertEquals("npc2", peers.get(0).npcId);
        assertEquals(9, peers.get(0).hp);
    }

    @Test
    public void peerActorStatesStayIndependentWhileWorldMatches() {
        DungeonState canonical = DungeonGenerator.generate(333L, 1);
        DungeonSharedFloor shared = DungeonSharedFloor.fromState(
                canonical, canonical.playerX, canonical.playerY, 4L);
        DungeonState aBase = DungeonGenerator.generate(1L, 1);
        DungeonState bBase = DungeonGenerator.generate(2L, 1);
        aBase.turn = 3;
        bBase.turn = 18;
        aBase.hp = 4;
        bBase.hp = 9;

        DungeonState actorA = shared.attach(aBase, false);
        DungeonState actorB = shared.attach(bBase, false);

        assertSameTiles(actorA, actorB);
        assertEquals(actorA.seed, actorB.seed);
        assertNotEquals(actorA.turn, actorB.turn);
        assertNotEquals(actorA.hp, actorB.hp);
    }

    private static void assertSameTiles(DungeonState expected, DungeonState actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        for (int y = 0; y < expected.height; y++) {
            for (int x = 0; x < expected.width; x++) {
                assertEquals(expected.tiles[y][x], actual.tiles[y][x]);
            }
        }
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
                42L,
                "",
                new ArrayList<>());
    }
}
