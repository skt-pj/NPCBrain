package com.sktpj.npcbrain;

import java.util.Random;

final class DungeonLootPolicy {
    private static final String[] ENEMY_NAMES = {
            "傷薬", "鉄の欠片", "魔石の小片", "古びた短剣", "革の護符", "小さな薬草束"
    };
    private static final String[] CHEST_NAMES = {
            "大粒の宝石", "古代の金貨箱", "希少な魔導具", "王家の装飾品", "高級な冒険装備", "精巧な宝飾品"
    };

    private DungeonLootPolicy() {
    }

    static DungeonItem enemyDrop(String npcId, DungeonState state, long acquiredAtMs) {
        int floor = state == null ? 1 : state.floor;
        int turn = state == null ? 0 : state.turn;
        long seed = state == null ? 1L : state.seed;
        String id = "enemy_drop:" + safe(npcId) + ":" + floor + ":" + turn;
        Random random = new Random(mix(seed, id.hashCode()));
        String name = ENEMY_NAMES[random.nextInt(ENEMY_NAMES.length)];
        long value = 20L + random.nextInt(101) + Math.max(0, floor - 1) * 8L;
        return new DungeonItem(id, name, value, DungeonItem.SOURCE_ENEMY, floor, acquiredAtMs);
    }

    static DungeonItem chestLoot(String npcId, DungeonState state, int x, int y, long acquiredAtMs) {
        int floor = state == null ? 1 : state.floor;
        long seed = state == null ? 1L : state.seed;
        String id = "chest:" + floor + ":" + x + ":" + y;
        Random random = new Random(mix(seed, id.hashCode()));
        String name = CHEST_NAMES[random.nextInt(CHEST_NAMES.length)];
        long value = 500L + random.nextInt(2001) + Math.max(0, floor - 1) * 75L;
        return new DungeonItem(id, name, value, DungeonItem.SOURCE_CHEST, floor, acquiredAtMs);
    }

    static boolean chestIsHighValue(DungeonItem enemy, DungeonItem chest) {
        return enemy != null && chest != null && chest.value > enemy.value;
    }

    private static long mix(long seed, long salt) {
        long value = seed ^ (salt * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static String safe(String value) {
        if (value == null) return "npc";
        String cleaned = value.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        return cleaned.isEmpty() ? "npc" : cleaned;
    }
}
