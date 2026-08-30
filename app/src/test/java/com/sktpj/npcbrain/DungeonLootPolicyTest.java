package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class DungeonLootPolicyTest {
    @Test
    public void chestLootIsWorthMoreThanEnemyDrop() {
        DungeonState state = DungeonGenerator.generate(424242L, 4);
        DungeonItem enemy = DungeonLootPolicy.enemyDrop("npc1", state, 1000L);
        DungeonItem chest = DungeonLootPolicy.chestLoot("npc1", state, 7, 8, 1000L);
        assertTrue(DungeonLootPolicy.chestIsHighValue(enemy, chest));
        assertEquals(DungeonItem.SOURCE_ENEMY, enemy.source);
        assertEquals(DungeonItem.SOURCE_CHEST, chest.source);
    }

    @Test
    public void dungeonItemJsonRoundTripKeepsEconomyMetadata() {
        DungeonItem original = new DungeonItem(
                "chest:3:4:5", "希少な魔導具", 1880L,
                DungeonItem.SOURCE_CHEST, 3, 987654L);
        DungeonItem restored = DungeonItem.fromJson(original.toJson());
        assertNotNull(restored);
        assertEquals(original.itemId, restored.itemId);
        assertEquals(original.name, restored.name);
        assertEquals(original.value, restored.value);
        assertEquals(original.source, restored.source);
        assertEquals(original.floor, restored.floor);
        assertEquals(original.acquiredAtMs, restored.acquiredAtMs);
    }

    @Test
    public void generatedFloorContainsReachableChestDeterministically() {
        DungeonState first = DungeonGenerator.generate(123456789L, 3);
        DungeonState second = DungeonGenerator.generate(123456789L, 3);
        assertTrue(first.hasChest());
        assertTrue(second.hasChest());
        assertEquals(chestSignature(first), chestSignature(second));
        assertTrue(DungeonGenerator.isReachable(
                first, first.playerX, first.playerY, first.stairsX(), first.stairsY()));
    }

    private static String chestSignature(DungeonState state) {
        StringBuilder builder = new StringBuilder();
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                if (state.tileAt(x, y) == DungeonState.CHEST) {
                    if (builder.length() > 0) builder.append('|');
                    builder.append(x).append(',').append(y);
                }
            }
        }
        return builder.toString();
    }
}
