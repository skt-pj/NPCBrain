package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Observes persisted dungeon state and publishes changes into the shared world event stream. */
final class DungeonWorldEventSynchronizer {
    private static final String PREFS = "npcbrain_dungeon_world_sync_v200";

    private final SharedPreferences syncPrefs;
    private final NpcRegistryStore registry;
    private final DungeonPresenceStore presence;
    private final DungeonStore dungeon;
    private final NpcWorldStateCoordinator worldState;
    private final WorldEventMemoryBridge bridge;

    DungeonWorldEventSynchronizer(Context context) {
        Context app = context.getApplicationContext();
        syncPrefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registry = new NpcRegistryStore(app);
        presence = new DungeonPresenceStore(app);
        dungeon = new DungeonStore(app);
        worldState = new NpcWorldStateCoordinator(app);
        bridge = new WorldEventMemoryBridge(app);
    }

    synchronized void syncAll(long nowMs) {
        for (String npcId : registry.activeNpcIds()) syncNpc(npcId, nowMs);
    }

    private void syncNpc(String npcId, long nowMs) {
        boolean currentPresent = presence.isPresent(npcId);
        DungeonState currentState = dungeon.loadRaw(npcId);
        JSONObject current = snapshot(currentPresent, currentState);
        String key = "npc_" + npcId;
        String previousRaw = syncPrefs.getString(key, "");
        JSONObject previous = parse(previousRaw);

        if (previous == null) {
            if (currentPresent) {
                record("dungeon_entered", npcId, nowMs, currentState, current, true, 0.75);
            }
            syncPrefs.edit().putString(key, current.toString()).apply();
            return;
        }

        boolean previousPresent = previous.optBoolean("present", false);
        if (previousPresent != currentPresent) {
            JSONObject payload = new JSONObject();
            put(payload, "present", currentPresent);
            record("dungeon_presence_changed", npcId, nowMs, currentState, payload, true, 0.70);
        }

        int previousFloor = previous.optInt("floor", 0);
        int currentFloor = current.optInt("floor", 0);
        if (currentFloor > 0 && previousFloor > 0 && previousFloor != currentFloor) {
            JSONObject payload = new JSONObject();
            put(payload, "from_floor", previousFloor);
            put(payload, "to_floor", currentFloor);
            record("dungeon_floor_changed", npcId, nowMs, currentState, payload, true, 0.85);
        }

        int previousHp = previous.optInt("hp", Integer.MIN_VALUE);
        int currentHp = current.optInt("hp", Integer.MIN_VALUE);
        if (previousHp != Integer.MIN_VALUE && currentHp != Integer.MIN_VALUE && previousHp != currentHp) {
            JSONObject payload = new JSONObject();
            put(payload, "from_hp", previousHp);
            put(payload, "to_hp", currentHp);
            record("dungeon_hp_changed", npcId, nowMs, currentState, payload, true, 0.75);
            if (previousHp > 0 && currentHp <= 0) {
                record("dungeon_died", npcId, nowMs, currentState, payload, true, 1.0);
            }
        }

        long previousTurn = previous.optLong("turn", -1L);
        long currentTurn = current.optLong("turn", -1L);
        String previousAction = previous.optString("last_action", "");
        String currentAction = current.optString("last_action", "");
        if (currentTurn >= 0L && currentTurn != previousTurn && !currentAction.equals(previousAction)) {
            JSONObject payload = new JSONObject();
            put(payload, "turn", currentTurn);
            put(payload, "action", currentAction);
            record("dungeon_action", npcId, nowMs, currentState, payload, false, 0.0);
        }

        syncPrefs.edit().putString(key, current.toString()).apply();
    }

    private void record(
            String type,
            String npcId,
            long nowMs,
            DungeonState state,
            JSONObject payload,
            boolean remember,
            double importance
    ) {
        String location = state == null
                ? worldState.snapshot(npcId, nowMs).effectiveLocation()
                : "dungeon_floor_" + state.floor;
        bridge.record(type, npcId, "", nowMs, location, payload, "", remember, false, importance);
    }

    private static JSONObject snapshot(boolean present, DungeonState state) {
        JSONObject json = new JSONObject();
        put(json, "present", present);
        if (state != null) {
            put(json, "floor", state.floor);
            put(json, "hp", state.hp);
            put(json, "turn", state.turn);
            put(json, "last_action", state.lastAction == null ? "" : state.lastAction);
        }
        return json;
    }

    private static JSONObject parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
