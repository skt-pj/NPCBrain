package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class DungeonInvitationContextTest {
    @Test
    public void fromDungeonUsesVisibleGroundedStateAndRoundTrips() {
        DungeonState state = state(true, 8);
        DungeonObjective objective = DungeonObjective.custom("初心者向けダンジョンを試す", 300L);

        DungeonInvitationContext context = DungeonInvitationContext.fromDungeon(
                "npc3", state, objective, 5_000L);

        assertNotNull(context);
        assertEquals("npc3", context.npcId);
        assertEquals(5_000L, context.invitedAtMs);
        assertEquals("ダンジョン 2F", context.destinationLabel);
        assertEquals(2, context.floor);
        assertEquals(17, context.turn);
        assertEquals(8, context.hp);
        assertEquals(10, context.maxHp);
        assertEquals("初心者向けダンジョンを試す", context.objectiveLabel);
        assertEquals(1, context.visibleEnemyCount);
        assertEquals(1, context.nearestVisibleEnemyDistance);
        assertEquals("", context.dangerBand);
        assertFalse(context.toJson().has("danger_band"));

        DungeonInvitationContext restored = DungeonInvitationContext.fromJson(context.toJson());
        assertNotNull(restored);
        assertEquals(context.npcId, restored.npcId);
        assertEquals(context.invitedAtMs, restored.invitedAtMs);
        assertEquals(context.destinationLabel, restored.destinationLabel);
        assertEquals(context.floor, restored.floor);
        assertEquals(context.turn, restored.turn);
        assertEquals(context.hp, restored.hp);
        assertEquals(context.maxHp, restored.maxHp);
        assertEquals(context.objectiveLabel, restored.objectiveLabel);
        assertEquals(context.visibleEnemyCount, restored.visibleEnemyCount);
        assertEquals(context.nearestVisibleEnemyDistance, restored.nearestVisibleEnemyDistance);
        assertEquals("", restored.dangerBand);
    }

    @Test
    public void hiddenEnemyDoesNotAffectInvitationGroundedFacts() {
        DungeonInvitationContext nearOnly = DungeonInvitationContext.fromDungeon(
                "npc1", state(false, 10), DungeonObjective.none(), 1L);
        DungeonInvitationContext nearAndHidden = DungeonInvitationContext.fromDungeon(
                "npc1", state(true, 10), DungeonObjective.none(), 1L);

        assertNotNull(nearOnly);
        assertNotNull(nearAndHidden);
        assertEquals(nearOnly.visibleEnemyCount, nearAndHidden.visibleEnemyCount);
        assertEquals(nearOnly.nearestVisibleEnemyDistance,
                nearAndHidden.nearestVisibleEnemyDistance);
        assertEquals("", nearOnly.dangerBand);
        assertEquals("", nearAndHidden.dangerBand);
    }

    @Test
    public void dangerBandDoesNotDeriveSharedPsychologicalRisk() {
        assertEquals("", DungeonInvitationContext.dangerBand(2, 10, 1, 2));
        assertEquals("", DungeonInvitationContext.dangerBand(3, 10, 0, 999));
        assertEquals("", DungeonInvitationContext.dangerBand(6, 10, 0, 999));
        assertEquals("", DungeonInvitationContext.dangerBand(10, 10, 0, 999));
    }

    private static DungeonState state(boolean includeHiddenEnemy, int hp) {
        int size = 11;
        int[][] tiles = new int[size][size];
        boolean[][] visited = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                tiles[y][x] = DungeonState.FLOOR;
                visited[y][x] = true;
            }
        }
        List<DungeonState.Enemy> enemies = new ArrayList<>();
        enemies.add(new DungeonState.Enemy("near", 6, 5, 3));
        if (includeHiddenEnemy) {
            enemies.add(new DungeonState.Enemy("hidden", 0, 0, 3));
        }
        return new DungeonState(
                2,
                17,
                size,
                size,
                tiles,
                visited,
                5,
                5,
                hp,
                10,
                123L,
                "",
                enemies);
    }
}
