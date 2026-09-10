package com.sktpj.npcbrain;

import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

/** Read-only canonical world queries. Querying never advances or creates world state. */
final class WorldQueryServiceV210 {
    private final WorldDatabaseV210 database;

    WorldQueryServiceV210(WorldDatabaseV210 database) {
        this.database = database;
    }

    JSONObject snapshot(String npcId) {
        SQLiteDatabase db = database.getReadableDatabase();
        CanonicalNpcStateV210 npc = database.loadNpc(db, npcId);
        long revision = database.metaLong(db, WorldDatabaseV210.META_REVISION, 0L);
        long worldTime = database.metaLong(db, WorldDatabaseV210.META_WORLD_TIME, 0L);
        JSONObject result = new JSONObject();
        try {
            result.put("revision", revision);
            result.put("world_time_ms", worldTime);
            result.put("npc", npc.toJson());
            JSONObject actor = npc.dungeonActor();
            if (npc.dungeonPresent() && actor.length() > 0) {
                int floor = Math.max(1, actor.optInt("floor", 1));
                result.put("dungeon_world", database.loadDungeonWorld(db, "floor_" + floor));
            } else {
                result.put("dungeon_world", new JSONObject());
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    long revision() {
        return database.metaLong(database.getReadableDatabase(), WorldDatabaseV210.META_REVISION, 0L);
    }

    long worldTimeMs() {
        return database.metaLong(database.getReadableDatabase(), WorldDatabaseV210.META_WORLD_TIME, 0L);
    }

    long lastAdvancedTimeMs() {
        return database.metaLong(database.getReadableDatabase(), WorldDatabaseV210.META_LAST_ADVANCED, 0L);
    }

    JSONObject diagnostics() {
        JSONObject result = database.diagnostics();
        try {
            long latest = Math.max(0L, result.optLong("next_event_sequence", 1L) - 1L);
            long conversation = database.projectionCheckpoint("conversation_v210");
            long memory = database.projectionCheckpoint("memory_v210");
            result.put("conversation_projection_checkpoint", conversation);
            result.put("conversation_projection_lag", Math.max(0L, latest - conversation));
            result.put("memory_projection_checkpoint", memory);
            result.put("memory_projection_lag", Math.max(0L, latest - memory));
        } catch (Exception ignored) {
        }
        return result;
    }
}
