package com.sktpj.npcbrain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One-shot, non-destructive bootstrap from v2.0 stores into the v2.1 canonical DB. */
final class LegacyWorldImporterV210 {
    private final Context appContext;
    private final WorldDatabaseV210 database;

    LegacyWorldImporterV210(Context context, WorldDatabaseV210 database) {
        appContext = context.getApplicationContext();
        this.database = database;
    }

    synchronized boolean importIfNeeded() {
        SQLiteDatabase db = database.getWritableDatabase();
        String state = database.metaString(db, WorldDatabaseV210.META_MIGRATION_STATE, "NOT_STARTED");
        if ("COMPLETED".equals(state)) return false;

        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            database.putMeta(db, WorldDatabaseV210.META_MIGRATION_STATE, "IMPORTING");
            NpcRegistryStore registry = new NpcRegistryStore(appContext);
            List<String> active = registry.activeNpcIds();
            JSONArray participants = new JSONArray();
            Set<Integer> importedFloors = new HashSet<>();
            long worldTime = now;
            NpcWorldStateCoordinator legacy = new NpcWorldStateCoordinator(appContext);
            for (String npcId : active) {
                participants.put(npcId);
                JSONObject old = legacy.snapshot(npcId, now).toJson();
                worldTime = Math.max(worldTime, old.optLong("world_time_ms", now));
                JSONObject canonical = new JSONObject();
                canonical.put("npc_id", npcId);
                canonical.put("state_version", 1L);
                canonical.put("active", old.optBoolean("active", true));
                canonical.put("dead", old.optBoolean("dead", false));
                canonical.put("location", old.optString("effective_location", "unknown"));
                canonical.put("activity", old.optString("effective_activity", "idle"));
                JSONObject life = copy(old.optJSONObject("life"));
                canonical.put("goal", life.optString("current_goal", ""));
                canonical.put("life_state", life);
                canonical.put("inner_life", copy(old.optJSONObject("inner_life")));
                canonical.put("intention", new JSONObject());
                canonical.put("relationships", copy(old.optJSONObject("relationships")));
                boolean present = old.optBoolean("dungeon_present", false) && !old.optBoolean("dead", false);
                canonical.put("dungeon_present", present);
                JSONObject dungeon = copy(old.optJSONObject("dungeon"));
                canonical.put("dungeon_actor", dungeon);
                CanonicalNpcStateV210 npc = CanonicalNpcStateV210.fromJson(npcId, canonical);
                database.upsertNpc(db, npc, 1L);

                int floor = dungeon.optInt("floor", 0);
                if (floor > 0 && importedFloors.add(floor)) {
                    DungeonSharedFloor shared = new DungeonWorldStore(appContext).load(floor);
                    if (shared != null) {
                        database.upsertDungeonWorld(db, "floor_" + floor, floor, shared.toJson(), 1L);
                    }
                }
            }

            JSONObject payload = new JSONObject();
            payload.put("source_version", "2.0.0");
            payload.put("source_version_code", 84);
            payload.put("npc_count", active.size());
            payload.put("historical_conversation_projection_preserved", true);
            payload.put("historical_memory_projection_preserved", true);
            WorldEventV210 baseline = new WorldEventV210(
                    1L,
                    UUID.randomUUID().toString(),
                    1L,
                    worldTime,
                    "migration_baseline",
                    "",
                    participants,
                    "",
                    "migration-v210",
                    "",
                    "migration:v210:baseline",
                    payload);
            database.insertEvent(db, baseline);
            database.putMeta(db, WorldDatabaseV210.META_REVISION, "1");
            database.putMeta(db, WorldDatabaseV210.META_WORLD_TIME, Long.toString(worldTime));
            database.putMeta(db, WorldDatabaseV210.META_LAST_ADVANCED, Long.toString(worldTime));
            database.putMeta(db, WorldDatabaseV210.META_NEXT_SEQUENCE, "2");
            database.putMeta(db, WorldDatabaseV210.META_MIGRATION_STATE, "COMPLETED");
            database.setProjectionCheckpoint("conversation_v210", 1L);
            database.setProjectionCheckpoint("memory_v210", 1L);
            db.setTransactionSuccessful();
            return true;
        } catch (Exception error) {
            throw new IllegalStateException("v2.1 world migration failed", error);
        } finally {
            db.endTransaction();
        }
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
