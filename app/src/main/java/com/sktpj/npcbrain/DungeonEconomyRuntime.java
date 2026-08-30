package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;

final class DungeonEconomyRuntime {
    private static final String PREFS = "npcbrain_dungeon_economy_runtime_v042";

    private final SharedPreferences preferences;
    private final DungeonStore dungeonStore;
    private final DungeonInventoryStore inventoryStore;

    DungeonEconomyRuntime(Context context) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        dungeonStore = new DungeonStore(app);
        inventoryStore = new DungeonInventoryStore(app);
    }

    synchronized boolean process(String npcId, DungeonState supplied, long nowMs) {
        String id = NpcId.of(npcId).value();
        DungeonState state = supplied == null ? dungeonStore.load(id) : supplied;
        if (state == null || state.hp <= 0) return false;
        boolean changed = ensureChestInitialized(state);

        String turnMarker = state.floor + ":" + state.turn;
        String turnKey = "last_turn_" + id;
        if (!preferences.contains(turnKey)) {
            preferences.edit().putString(turnKey, turnMarker).commit();
        } else if (state.lastAction != null
                && state.lastAction.contains("敵を倒した")
                && !turnMarker.equals(preferences.getString(turnKey, ""))) {
            DungeonItem drop = DungeonLootPolicy.enemyDrop(id, state, nowMs);
            if (inventoryStore.addIfAbsent(id, drop)) {
                state.lastAction = appendLoot(state.lastAction, drop);
                changed = true;
            }
            preferences.edit().putString(turnKey, turnMarker).commit();
        } else if (!turnMarker.equals(preferences.getString(turnKey, ""))) {
            preferences.edit().putString(turnKey, turnMarker).commit();
        }

        if (state.tileAt(state.playerX, state.playerY) == DungeonState.CHEST) {
            int chestX = state.playerX;
            int chestY = state.playerY;
            DungeonItem treasure = DungeonLootPolicy.chestLoot(id, state, chestX, chestY, nowMs);
            boolean newlyAdded = inventoryStore.addIfAbsent(id, treasure);
            state.tiles[chestY][chestX] = DungeonState.FLOOR;
            if (newlyAdded) state.lastAction = appendLoot("宝箱を開けた", treasure);
            changed = true;
        }

        if (changed) dungeonStore.save(id, state);
        return changed;
    }

    private boolean ensureChestInitialized(DungeonState state) {
        String key = "chest_initialized_floor_" + state.floor;
        if (state.hasChest()) {
            if (!preferences.getBoolean(key, false)) preferences.edit().putBoolean(key, true).commit();
            return false;
        }
        if (preferences.getBoolean(key, false)) return false;

        int[] position = legacyChestPosition(state);
        preferences.edit().putBoolean(key, true).commit();
        if (position == null) return false;
        state.tiles[position[1]][position[0]] = DungeonState.CHEST;
        return true;
    }

    private static int[] legacyChestPosition(DungeonState state) {
        Random random = new Random(state.seed ^ (state.floor * 0x9E3779B97F4A7C15L));
        for (int attempt = 0; attempt < Math.max(80, state.width * state.height * 2); attempt++) {
            int x = 1 + random.nextInt(Math.max(1, state.width - 2));
            int y = 1 + random.nextInt(Math.max(1, state.height - 2));
            if (state.tileAt(x, y) != DungeonState.FLOOR) continue;
            if (x == state.playerX && y == state.playerY) continue;
            if (state.enemyAt(x, y) != null) continue;
            if (Math.abs(x - state.playerX) + Math.abs(y - state.playerY) < 2) continue;
            return new int[]{x, y};
        }
        return null;
    }

    private static String appendLoot(String action, DungeonItem item) {
        String base = action == null ? "" : action.trim();
        String loot = item.name + "（評価額 ¥" + item.value + "）を入手";
        return base.isEmpty() ? loot : base + " / " + loot;
    }
}
