package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DungeonCombatFeedbackTest {
    private static final DungeonPersonalityPolicy.Traits TRAITS =
            new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50);

    @Test
    public void adjacentKillingAttackEmitsHitAndDefeat() {
        DungeonState state = openState(5, 5, 2, 2, 3, 3);
        state.enemies.add(new DungeonState.Enemy("enemy-a", 3, 2, 2));

        DungeonStepResult result = DungeonEngine.stepDetailed(
                state, TRAITS, DungeonPersonalityPolicy.Direction.RIGHT);

        assertEquals(0, state.enemies.get(0).hp);
        DungeonCombatEvent hit = single(result.events, DungeonCombatEvent.PLAYER_HIT);
        DungeonCombatEvent defeated = single(result.events, DungeonCombatEvent.ENEMY_DEFEATED);
        assertEquals(2, hit.sourceX);
        assertEquals(2, hit.sourceY);
        assertEquals(3, hit.targetX);
        assertEquals(2, hit.targetY);
        assertEquals(2, hit.damage);
        assertEquals("enemy-a", hit.targetId);
        assertEquals(hit.targetX, defeated.targetX);
        assertEquals(hit.targetY, defeated.targetY);
        assertEquals(0, count(result.events, DungeonCombatEvent.PLAYER_DAMAGED));
    }

    @Test
    public void survivingAdjacentEnemyEmitsHitThenPlayerDamage() {
        DungeonState state = openState(5, 5, 2, 2, 3, 3);
        state.enemies.add(new DungeonState.Enemy("enemy-b", 3, 2, 4));

        DungeonStepResult result = DungeonEngine.stepDetailed(
                state, TRAITS, DungeonPersonalityPolicy.Direction.RIGHT);

        assertEquals(2, state.enemies.get(0).hp);
        assertEquals(9, state.hp);
        assertEquals(1, count(result.events, DungeonCombatEvent.PLAYER_HIT));
        assertEquals(1, count(result.events, DungeonCombatEvent.PLAYER_DAMAGED));
        DungeonCombatEvent damaged = single(result.events, DungeonCombatEvent.PLAYER_DAMAGED);
        assertEquals(3, damaged.sourceX);
        assertEquals(2, damaged.sourceY);
        assertEquals(2, damaged.targetX);
        assertEquals(2, damaged.targetY);
        assertEquals(1, damaged.damage);
    }

    @Test
    public void movingWithDistantEnemyDoesNotEmitCombatImpact() {
        DungeonState state = openState(7, 7, 2, 2, 5, 5);
        state.enemies.add(new DungeonState.Enemy("enemy-c", 5, 2, 4));

        DungeonStepResult result = DungeonEngine.stepDetailed(
                state, TRAITS, DungeonPersonalityPolicy.Direction.DOWN);

        assertEquals(0, count(result.events, DungeonCombatEvent.PLAYER_HIT));
        assertEquals(0, count(result.events, DungeonCombatEvent.ENEMY_DEFEATED));
        assertEquals(0, count(result.events, DungeonCombatEvent.PLAYER_DAMAGED));
        assertEquals(3, state.playerY);
    }

    @Test
    public void waitWithAdjacentEnemyEmitsOnlyPlayerDamage() {
        DungeonState state = openState(5, 5, 2, 2, 3, 3);
        state.enemies.add(new DungeonState.Enemy("enemy-d", 2, 1, 4));

        DungeonStepResult result = DungeonEngine.stepDetailed(
                state, TRAITS, DungeonPersonalityPolicy.Direction.WAIT);

        assertEquals(0, count(result.events, DungeonCombatEvent.PLAYER_HIT));
        assertEquals(0, count(result.events, DungeonCombatEvent.ENEMY_DEFEATED));
        assertEquals(1, count(result.events, DungeonCombatEvent.PLAYER_DAMAGED));
        assertEquals(9, state.hp);
    }

    @Test
    public void legacyStepMatchesDetailedStateForExplicitDirection() {
        DungeonState legacy = openState(5, 5, 2, 2, 3, 3);
        DungeonState detailed = openState(5, 5, 2, 2, 3, 3);

        DungeonState legacyResult = DungeonEngine.step(
                legacy, TRAITS, DungeonPersonalityPolicy.Direction.LEFT);
        DungeonStepResult detailedResult = DungeonEngine.stepDetailed(
                detailed, TRAITS, DungeonPersonalityPolicy.Direction.LEFT);

        assertEquals(legacyResult.floor, detailedResult.state.floor);
        assertEquals(legacyResult.turn, detailedResult.state.turn);
        assertEquals(legacyResult.playerX, detailedResult.state.playerX);
        assertEquals(legacyResult.playerY, detailedResult.state.playerY);
        assertEquals(legacyResult.hp, detailedResult.state.hp);
        assertEquals(legacyResult.lastAction, detailedResult.state.lastAction);
        assertTrue(detailedResult.events.isEmpty());
        try {
            detailedResult.events.add(new DungeonCombatEvent(
                    DungeonCombatEvent.PLAYER_HIT, 0, 0, 1, 0, 1, "x"));
            assertFalse("events must be immutable", true);
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }

    private static int count(List<DungeonCombatEvent> events, String type) {
        int count = 0;
        for (DungeonCombatEvent event : events) {
            if (type.equals(event.type)) count++;
        }
        return count;
    }

    private static DungeonCombatEvent single(List<DungeonCombatEvent> events, String type) {
        DungeonCombatEvent found = null;
        for (DungeonCombatEvent event : events) {
            if (!type.equals(event.type)) continue;
            if (found != null) throw new AssertionError("multiple events for " + type);
            found = event;
        }
        if (found == null) throw new AssertionError("missing event " + type);
        return found;
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
                visited[y][x] = true;
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
                1L,
                "",
                new ArrayList<>());
    }
}
