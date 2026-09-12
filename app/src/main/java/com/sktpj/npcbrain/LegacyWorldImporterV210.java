package com.sktpj.npcbrain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One-shot, non-destructive bootstrap from v2.0 stores into the v2.1 canonical DB. */
final class LegacyWorldImporterV210 {
    private final Context appContext;
    private final WorldDatabaseV210 database;
    private final Runnable beforeCommitHook;

    LegacyWorldImporterV210(Context context, WorldDatabaseV210 database) {
        this(context, database, null);
    }

    LegacyWorldImporterV210(Context context, WorldDatabaseV210 database, Runnable beforeCommitHook) {
        appContext = context.getApplicationContext();
        this.database = database;
        this.beforeCommitHook = beforeCommitHook;
    }

    synchronized boolean importIfNeeded() {
        SQLiteDatabase db = database.getWritableDatabase();
        String state = database.metaString(db, WorldDatabaseV210.META_MIGRATION_STATE, "NOT_STARTED");
        if ("COMPLETED".equals(state)) return false;

        // Read every legacy source before taking the canonical SQLite write transaction.
        // The transaction below therefore contains canonical writes only and cannot be held
        // while SharedPreferences/world-clock/dungeon compatibility stores do their own work.
        PreparedMigration prepared = prepareLegacySnapshot(System.currentTimeMillis());

        db.beginTransaction();
        try {
            // A second importer may have completed while the legacy snapshot was prepared.
            if ("COMPLETED".equals(database.metaString(
                    db, WorldDatabaseV210.META_MIGRATION_STATE, "NOT_STARTED"))) {
                db.setTransactionSuccessful();
                return false;
            }
            database.putMeta(db, WorldDatabaseV210.META_MIGRATION_STATE, "IMPORTING");

            for (CanonicalNpcStateV210 npc : prepared.npcs) {
                database.upsertNpc(db, npc, 1L);
            }
            for (Map.Entry<Integer, JSONObject> entry : prepared.dungeonWorlds.entrySet()) {
                int floor = entry.getKey();
                database.upsertDungeonWorld(
                        db, "floor_" + floor, floor, entry.getValue(), 1L);
            }

            JSONObject payload = new JSONObject();
            payload.put("source_version", "2.0.0");
            payload.put("source_version_code", 84);
            payload.put("npc_count", prepared.npcs.size());
            payload.put("historical_conversation_projection_preserved", true);
            payload.put("historical_memory_projection_preserved", true);
            WorldEventV210 baseline = new WorldEventV210(
                    1L,
                    UUID.randomUUID().toString(),
                    1L,
                    prepared.worldTime,
                    "migration_baseline",
                    "",
                    prepared.participants,
                    "",
                    "migration-v210",
                    "",
                    "migration:v210:baseline",
                    payload);
            database.insertEvent(db, baseline);
            database.putMeta(db, WorldDatabaseV210.META_REVISION, "1");
            database.putMeta(db, WorldDatabaseV210.META_WORLD_TIME, Long.toString(prepared.worldTime));
            database.putMeta(db, WorldDatabaseV210.META_LAST_ADVANCED, Long.toString(prepared.worldTime));
            database.putMeta(db, WorldDatabaseV210.META_NEXT_SEQUENCE, "2");
            database.putMeta(db, WorldDatabaseV210.META_MIGRATION_STATE, "COMPLETED");
            database.setProjectionCheckpoint(db, WorldProjectionRunnerV210.CONVERSATION, 1L);
            database.setProjectionCheckpoint(db, WorldProjectionRunnerV210.MEMORY, 1L);
            if (beforeCommitHook != null) beforeCommitHook.run();
            db.setTransactionSuccessful();
            return true;
        } catch (Exception error) {
            throw new IllegalStateException("v2.1 world migration failed", error);
        } finally {
            db.endTransaction();
        }
    }

    private PreparedMigration prepareLegacySnapshot(long now) {
        try {
            NpcRegistryStore registry = new NpcRegistryStore(appContext);
            List<String> active = registry.activeNpcIds();
            JSONArray participants = new JSONArray();
            List<CanonicalNpcStateV210> npcs = new ArrayList<>();
            Map<Integer, JSONObject> dungeonWorlds = new LinkedHashMap<>();
            long worldTime = now;
            NpcWorldStateCoordinator legacy = new NpcWorldStateCoordinator(appContext);
            DungeonWorldStore dungeonWorldStore = new DungeonWorldStore(appContext);

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
                boolean present = old.optBoolean("dungeon_present", false)
                        && !old.optBoolean("dead", false);
                canonical.put("dungeon_present", present);
                JSONObject dungeon = copy(old.optJSONObject("dungeon"));
                canonical.put("dungeon_actor", dungeon);
                npcs.add(CanonicalNpcStateV210.fromJson(npcId, canonical));

                int floor = dungeon.optInt("floor", 0);
                if (floor > 0 && !dungeonWorlds.containsKey(floor)) {
                    DungeonSharedFloor shared = dungeonWorldStore.load(floor);
                    if (shared != null) dungeonWorlds.put(floor, shared.toJson());
                }
            }
            return new PreparedMigration(npcs, participants, dungeonWorlds, worldTime);
        } catch (Exception error) {
            throw new IllegalStateException("v2.1 legacy snapshot preparation failed", error);
        }
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static final class PreparedMigration {
        final List<CanonicalNpcStateV210> npcs;
        final JSONArray participants;
        final Map<Integer, JSONObject> dungeonWorlds;
        final long worldTime;

        PreparedMigration(
                List<CanonicalNpcStateV210> npcs,
                JSONArray participants,
                Map<Integer, JSONObject> dungeonWorlds,
                long worldTime
        ) {
            this.npcs = npcs;
            this.participants = participants;
            this.dungeonWorlds = dungeonWorlds;
            this.worldTime = worldTime;
        }
    }
}
