package com.sktpj.npcbrain;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** SQLite SSOT for mutable world state and the ordered event journal. */
final class WorldDatabaseV210 extends SQLiteOpenHelper {
    static final String DB_NAME = "npcbrain_world_v210.db";
    static final int DB_VERSION = 1;

    static final String META_SCHEMA_VERSION = "schema_version";
    static final String META_REVISION = "revision";
    static final String META_WORLD_TIME = "world_time_ms";
    static final String META_LAST_ADVANCED = "last_advanced_time_ms";
    static final String META_NEXT_SEQUENCE = "next_event_sequence";
    static final String META_MIGRATION_STATE = "migration_state";

    WorldDatabaseV210(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE world_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE npc_runtime ("
                + "npc_id TEXT PRIMARY KEY,"
                + "state_version INTEGER NOT NULL,"
                + "state_json TEXT NOT NULL,"
                + "updated_revision INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE dungeon_world ("
                + "world_id TEXT PRIMARY KEY,"
                + "floor INTEGER NOT NULL,"
                + "state_json TEXT NOT NULL,"
                + "updated_revision INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_dungeon_world_floor ON dungeon_world(floor)");
        db.execSQL("CREATE TABLE world_event ("
                + "sequence INTEGER PRIMARY KEY,"
                + "event_id TEXT UNIQUE NOT NULL,"
                + "aggregate_revision INTEGER NOT NULL,"
                + "world_time_ms INTEGER NOT NULL,"
                + "event_type TEXT NOT NULL,"
                + "actor_id TEXT NOT NULL DEFAULT '',"
                + "participant_ids TEXT NOT NULL DEFAULT '[]',"
                + "location TEXT NOT NULL DEFAULT '',"
                + "correlation_id TEXT NOT NULL DEFAULT '',"
                + "causation_id TEXT NOT NULL DEFAULT '',"
                + "idempotency_key TEXT UNIQUE,"
                + "payload TEXT NOT NULL DEFAULT '{}')");
        db.execSQL("CREATE INDEX idx_world_event_time ON world_event(world_time_ms)");
        db.execSQL("CREATE INDEX idx_world_event_type ON world_event(event_type)");
        db.execSQL("CREATE INDEX idx_world_event_actor ON world_event(actor_id)");
        db.execSQL("CREATE TABLE processed_command ("
                + "idempotency_key TEXT PRIMARY KEY,"
                + "command_type TEXT NOT NULL,"
                + "committed_revision INTEGER NOT NULL,"
                + "result_json TEXT NOT NULL DEFAULT '{}')");
        db.execSQL("CREATE TABLE projection_checkpoint ("
                + "projector_id TEXT PRIMARY KEY,"
                + "last_sequence INTEGER NOT NULL)");
        putMeta(db, META_SCHEMA_VERSION, Integer.toString(DB_VERSION));
        putMeta(db, META_REVISION, "0");
        putMeta(db, META_WORLD_TIME, "0");
        putMeta(db, META_LAST_ADVANCED, "0");
        putMeta(db, META_NEXT_SEQUENCE, "1");
        putMeta(db, META_MIGRATION_STATE, "NOT_STARTED");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("World DB migration required: " + oldVersion + " -> " + newVersion);
    }

    long metaLong(SQLiteDatabase db, String key, long fallback) {
        String value = metaString(db, key, Long.toString(fallback));
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    String metaString(SQLiteDatabase db, String key, String fallback) {
        Cursor cursor = db.query("world_meta", new String[]{"value"}, "key=?",
                new String[]{key}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : fallback;
        } finally {
            cursor.close();
        }
    }

    void putMeta(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value == null ? "" : value);
        db.insertWithOnConflict("world_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    CanonicalNpcStateV210 loadNpc(SQLiteDatabase db, String npcId) {
        String id = NpcId.of(npcId).value();
        Cursor cursor = db.query("npc_runtime", new String[]{"state_json"}, "npc_id=?",
                new String[]{id}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) return CanonicalNpcStateV210.empty(id);
            return CanonicalNpcStateV210.fromJson(id, new JSONObject(cursor.getString(0)));
        } catch (Exception ignored) {
            return CanonicalNpcStateV210.empty(id);
        } finally {
            cursor.close();
        }
    }

    void upsertNpc(SQLiteDatabase db, CanonicalNpcStateV210 state, long revision) {
        ContentValues values = new ContentValues();
        values.put("npc_id", state.npcId());
        values.put("state_version", state.stateVersion());
        values.put("state_json", state.toJson().toString());
        values.put("updated_revision", revision);
        db.insertWithOnConflict("npc_runtime", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    JSONObject loadDungeonWorld(SQLiteDatabase db, String worldId) {
        Cursor cursor = db.query("dungeon_world", new String[]{"state_json"}, "world_id=?",
                new String[]{worldId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? new JSONObject(cursor.getString(0)) : new JSONObject();
        } catch (Exception ignored) {
            return new JSONObject();
        } finally {
            cursor.close();
        }
    }

    void upsertDungeonWorld(SQLiteDatabase db, String worldId, int floor, JSONObject state, long revision) {
        ContentValues values = new ContentValues();
        values.put("world_id", worldId);
        values.put("floor", floor);
        values.put("state_json", state == null ? "{}" : state.toString());
        values.put("updated_revision", revision);
        db.insertWithOnConflict("dungeon_world", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    JSONObject processedResult(SQLiteDatabase db, String idempotencyKey) {
        Cursor cursor = db.query("processed_command", new String[]{"result_json"},
                "idempotency_key=?", new String[]{idempotencyKey}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? new JSONObject(cursor.getString(0)) : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            cursor.close();
        }
    }

    void recordProcessed(
            SQLiteDatabase db,
            String idempotencyKey,
            String commandType,
            long revision,
            JSONObject result
    ) {
        ContentValues values = new ContentValues();
        values.put("idempotency_key", idempotencyKey);
        values.put("command_type", commandType);
        values.put("committed_revision", revision);
        values.put("result_json", result == null ? "{}" : result.toString());
        db.insertOrThrow("processed_command", null, values);
    }

    void insertEvent(SQLiteDatabase db, WorldEventV210 event) {
        ContentValues values = new ContentValues();
        values.put("sequence", event.sequence);
        values.put("event_id", event.eventId);
        values.put("aggregate_revision", event.aggregateRevision);
        values.put("world_time_ms", event.worldTimeMs);
        values.put("event_type", event.eventType);
        values.put("actor_id", event.actorId);
        values.put("participant_ids", event.participantIds.toString());
        values.put("location", event.location);
        values.put("correlation_id", event.correlationId);
        values.put("causation_id", event.causationId);
        if (event.idempotencyKey.isEmpty()) values.putNull("idempotency_key");
        else values.put("idempotency_key", event.idempotencyKey);
        values.put("payload", event.payload.toString());
        db.insertOrThrow("world_event", null, values);
    }

    List<WorldEventV210> eventsAfter(long sequence, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("world_event",
                new String[]{"sequence","event_id","aggregate_revision","world_time_ms","event_type",
                        "actor_id","participant_ids","location","correlation_id","causation_id",
                        "idempotency_key","payload"},
                "sequence>?", new String[]{Long.toString(Math.max(0L, sequence))},
                null, null, "sequence ASC", Integer.toString(Math.max(1, limit)));
        List<WorldEventV210> result = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                JSONArray participants;
                JSONObject payload;
                try { participants = new JSONArray(cursor.getString(6)); }
                catch (Exception ignored) { participants = new JSONArray(); }
                try { payload = new JSONObject(cursor.getString(11)); }
                catch (Exception ignored) { payload = new JSONObject(); }
                result.add(new WorldEventV210(
                        cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3),
                        cursor.getString(4), cursor.getString(5), participants, cursor.getString(7),
                        cursor.getString(8), cursor.getString(9), cursor.isNull(10) ? "" : cursor.getString(10),
                        payload));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    long projectionCheckpoint(String projectorId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("projection_checkpoint", new String[]{"last_sequence"},
                "projector_id=?", new String[]{projectorId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    void setProjectionCheckpoint(String projectorId, long sequence) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("projector_id", projectorId);
        values.put("last_sequence", Math.max(0L, sequence));
        db.insertWithOnConflict("projection_checkpoint", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    JSONObject diagnostics() {
        SQLiteDatabase db = getReadableDatabase();
        JSONObject result = new JSONObject();
        try {
            result.put("schema_version", metaLong(db, META_SCHEMA_VERSION, 0L));
            result.put("revision", metaLong(db, META_REVISION, 0L));
            result.put("world_time_ms", metaLong(db, META_WORLD_TIME, 0L));
            result.put("last_advanced_time_ms", metaLong(db, META_LAST_ADVANCED, 0L));
            result.put("next_event_sequence", metaLong(db, META_NEXT_SEQUENCE, 1L));
            result.put("migration_state", metaString(db, META_MIGRATION_STATE, "NOT_STARTED"));
        } catch (Exception ignored) {
        }
        return result;
    }
}
