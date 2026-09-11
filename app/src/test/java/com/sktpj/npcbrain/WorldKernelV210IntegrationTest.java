package com.sktpj.npcbrain;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Android-backed contract tests for T-WK-210 transaction/replay guarantees. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class WorldKernelV210IntegrationTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        resetKernel();
        context.deleteDatabase(WorldDatabaseV210.DB_NAME);
        context.getSharedPreferences("npcbrain_conversations_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        clearNpcMemory("npc1");
        clearNpcMemory("npc2");
    }

    @After
    public void tearDown() throws Exception {
        resetKernel();
        context.deleteDatabase(WorldDatabaseV210.DB_NAME);
    }

    @Test
    public void schemaDiagnosticsAndIdempotencyAreCanonicalAndReadOnly() {
        WorldKernelV210 kernel = WorldKernelV210.get(context);
        WorldDatabaseV210 database = kernel.database();
        SQLiteDatabase db = database.getWritableDatabase();
        assertEquals(1, tableCount(db, "world_meta"));
        assertEquals(1, tableCount(db, "npc_runtime"));
        assertEquals(1, tableCount(db, "dungeon_world"));
        assertEquals(1, tableCount(db, "world_event"));
        assertEquals(1, tableCount(db, "processed_command"));
        assertEquals(1, tableCount(db, "projection_checkpoint"));

        seedNpc(database, "npc1", 1L, false);
        JSONObject payload = new JSONObject();
        put(payload, "dynamic_state", json("valence", 0.4));
        WorldCommandV210 command = WorldCommandV210.of(
                WorldCommandV210.UPSERT_DYNAMIC_STATE, 1000L, "npc1", "idem:dynamic", payload);
        WorldCommitResultV210 first = kernel.commit(command);
        long stateAfterFirst = database.loadNpc(db, "npc1").stateVersion();
        int eventCountAfterFirst = rowCount(db, "world_event");
        WorldCommitResultV210 second = kernel.commit(command);

        assertTrue(first.committed);
        assertFalse(first.duplicate);
        assertFalse(second.committed);
        assertTrue(second.duplicate);
        assertEquals(first.revision, second.revision);
        assertEquals(stateAfterFirst, database.loadNpc(db, "npc1").stateVersion());
        assertEquals(eventCountAfterFirst, rowCount(db, "world_event"));

        WorldQueryServiceV210 query = new WorldQueryServiceV210(database);
        long revisionBefore = query.revision();
        JSONObject diagnostics = query.diagnostics();
        assertEquals(revisionBefore, diagnostics.optLong("revision", -1L));
        assertTrue(diagnostics.has("world_time_ms"));
        assertTrue(diagnostics.has("last_advanced_time_ms"));
        assertTrue(diagnostics.has("conversation_projection_checkpoint"));
        assertTrue(diagnostics.has("memory_projection_checkpoint"));
        assertTrue(diagnostics.has("projection_lag"));
        assertFalse(diagnostics.toString().toLowerCase().contains("api_key"));
        assertEquals(revisionBefore, query.revision());
    }

    @Test
    public void oneCommandHasOneRevisionAndRollbackRestoresStateJournalAndProcessedCommand() {
        WorldKernelV210 kernel = WorldKernelV210.get(context);
        WorldDatabaseV210 database = kernel.database();
        SQLiteDatabase db = database.getWritableDatabase();
        seedNpc(database, "npc1", 1L, true);
        seedNpc(database, "npc2", 1L, true);

        JSONObject payload = new JSONObject();
        put(payload, "dungeon_present", true);
        put(payload, "dungeon_actor", dungeonActor(1, 10, 1, 1));
        JSONArray peerUpdates = new JSONArray();
        peerUpdates.put(json("npc_id", "npc2", "floor", 1, "hp", 9, "player_x", 2, "player_y", 2));
        put(payload, "peer_actor_updates", peerUpdates);
        WorldCommitResultV210 result = kernel.commit(WorldCommandV210.of(
                WorldCommandV210.UPSERT_DUNGEON_STATE, 2000L, "npc1", "tx:multi", payload));
        assertTrue(result.committed);
        assertEquals(1L, result.revision);
        assertTrue(result.events.size() >= 2);
        long expectedSequence = result.events.get(0).sequence;
        for (WorldEventV210 event : result.events) {
            assertEquals(result.revision, event.aggregateRevision);
            assertEquals(expectedSequence++, event.sequence);
        }

        long beforeRevision = database.metaLong(db, WorldDatabaseV210.META_REVISION, -1L);
        long beforeState = database.loadNpc(db, "npc1").stateVersion();
        int beforeEvents = rowCount(db, "world_event");
        long nextSequence = database.metaLong(db, WorldDatabaseV210.META_NEXT_SEQUENCE, 1L);
        database.insertEvent(db, new WorldEventV210(
                nextSequence,
                "forced-collision-event",
                beforeRevision,
                2000L,
                "fixture",
                "npc1",
                new JSONArray(),
                "",
                "",
                "",
                "tx:rollback:event:0",
                new JSONObject()));
        database.putMeta(db, WorldDatabaseV210.META_NEXT_SEQUENCE, Long.toString(nextSequence + 1L));
        beforeEvents++;

        JSONObject dynamic = new JSONObject();
        put(dynamic, "dynamic_state", json("stress", 0.9));
        try {
            kernel.commit(WorldCommandV210.of(
                    WorldCommandV210.UPSERT_DYNAMIC_STATE, 2100L, "npc1", "tx:rollback", dynamic));
            fail("unique event idempotency collision must fail the transaction");
        } catch (RuntimeException expected) {
            // expected
        }
        assertEquals(beforeRevision, database.metaLong(db, WorldDatabaseV210.META_REVISION, -1L));
        assertEquals(beforeState, database.loadNpc(db, "npc1").stateVersion());
        assertEquals(beforeEvents, rowCount(db, "world_event"));
        assertNull(database.processedResult(db, "tx:rollback"));
    }

    @Test
    public void sequentialCommitsPreserveMonotonicRevisionAndNoLostActorUpdate() {
        WorldKernelV210 kernel = WorldKernelV210.get(context);
        WorldDatabaseV210 database = kernel.database();
        SQLiteDatabase db = database.getWritableDatabase();
        seedNpc(database, "npc1", 1L, false);
        seedNpc(database, "npc2", 1L, false);

        JSONObject firstPayload = new JSONObject();
        put(firstPayload, "dynamic_state", json("stress", 0.2));
        JSONObject secondPayload = new JSONObject();
        put(secondPayload, "dynamic_state", json("stress", 0.8));
        WorldCommitResultV210 first = kernel.commit(WorldCommandV210.of(
                WorldCommandV210.UPSERT_DYNAMIC_STATE, 2500L, "npc1", "serialized:1", firstPayload));
        WorldCommitResultV210 second = kernel.commit(WorldCommandV210.of(
                WorldCommandV210.UPSERT_DYNAMIC_STATE, 2500L, "npc2", "serialized:2", secondPayload));

        assertTrue(first.committed);
        assertTrue(second.committed);
        assertEquals(1L, first.revision);
        assertEquals(2L, second.revision);
        assertEquals(2L, database.metaLong(db, WorldDatabaseV210.META_REVISION, -1L));
        assertEquals(2L, database.loadNpc(db, "npc1").stateVersion());
        assertEquals(2L, database.loadNpc(db, "npc2").stateVersion());
        List<WorldEventV210> events = database.eventsAfter(0L, 10);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).sequence);
        assertEquals(2L, events.get(1).sequence);
        assertEquals(1L, events.get(0).aggregateRevision);
        assertEquals(2L, events.get(1).aggregateRevision);
    }

    @Test
    public void staleBrainResultCannotOverwriteNewerActorState() {
        WorldKernelV210 kernel = WorldKernelV210.get(context);
        WorldDatabaseV210 database = kernel.database();
        SQLiteDatabase db = database.getWritableDatabase();
        seedNpc(database, "npc1", 1L, false);

        JSONObject stalePayload = new JSONObject();
        put(stalePayload, "basis_state_version", 0L);
        put(stalePayload, "dynamic_state", json("valence", -0.8));
        WorldCommitResultV210 stale = kernel.commit(WorldCommandV210.of(
                WorldCommandV210.APPLY_BRAIN_DECISION, 3000L, "npc1", "brain:stale", stalePayload));
        assertEquals("stale_brain_result", stale.result.optString("status"));
        CanonicalNpcStateV210 afterStale = database.loadNpc(db, "npc1");
        assertEquals(1L, afterStale.stateVersion());
        assertEquals(0, afterStale.dynamicState().length());

        JSONObject currentPayload = new JSONObject();
        put(currentPayload, "basis_state_version", 1L);
        put(currentPayload, "dynamic_state", json("valence", 0.7));
        WorldCommitResultV210 current = kernel.commit(WorldCommandV210.of(
                WorldCommandV210.APPLY_BRAIN_DECISION, 3100L, "npc1", "brain:current", currentPayload));
        assertTrue(current.committed);
        CanonicalNpcStateV210 afterCurrent = database.loadNpc(db, "npc1");
        assertEquals(2L, afterCurrent.stateVersion());
        assertEquals(0.7, afterCurrent.dynamicState().optDouble("valence"), 0.0001);
    }

    @Test
    public void projectionFailureStopsCheckpointAndReplayIsIdempotentForConversationAndMemory() {
        WorldKernelV210 kernel = WorldKernelV210.get(context);
        WorldDatabaseV210 database = kernel.database();
        seedNpc(database, "npc1", 1L, false);
        seedNpc(database, "npc2", 1L, false);
        String roomId = NpcPeerRoomPolicy.roomId("npc1", "npc2");
        assertFalse(roomId.isEmpty());

        commitPeerMessage(kernel, "peer-message-1", roomId, "npc1", "one", 4000L);
        commitPeerMessage(kernel, "peer-message-2", roomId, "npc2", "two", 4100L);
        long canonicalRevision = new WorldQueryServiceV210(database).revision();

        WorldProjectionRunnerV210 failing = new WorldProjectionRunnerV210(
                context,
                database,
                (projectorId, event) -> {
                    if (WorldProjectionRunnerV210.MEMORY.equals(projectorId) && event.sequence == 2L) {
                        throw new IllegalStateException("intentional projection failure");
                    }
                });
        try {
            failing.runPending();
            fail("fault injector must stop memory projection");
        } catch (IllegalStateException expected) {
            // expected
        }
        assertEquals(2L, database.projectionCheckpoint(WorldProjectionRunnerV210.CONVERSATION));
        assertEquals(1L, database.projectionCheckpoint(WorldProjectionRunnerV210.MEMORY));
        assertEquals(canonicalRevision, new WorldQueryServiceV210(database).revision());

        WorldProjectionRunnerV210 normal = new WorldProjectionRunnerV210(context, database);
        normal.runPending();
        assertEquals(2L, database.projectionCheckpoint(WorldProjectionRunnerV210.MEMORY));
        ConversationStore conversations = new ConversationStore(context);
        assertEquals(2, conversations.messageCount(roomId));
        assertEquals(2, new MemoryStore(NpcContexts.storage(context, "npc1")).maintenanceEpisodes().length());
        assertEquals(2, new MemoryStore(NpcContexts.storage(context, "npc2")).maintenanceEpisodes().length());

        database.setProjectionCheckpoint(WorldProjectionRunnerV210.CONVERSATION, 0L);
        database.setProjectionCheckpoint(WorldProjectionRunnerV210.MEMORY, 0L);
        normal.runPending();
        assertEquals(2, conversations.messageCount(roomId));
        assertEquals(2, new MemoryStore(NpcContexts.storage(context, "npc1")).maintenanceEpisodes().length());
        assertEquals(2, new MemoryStore(NpcContexts.storage(context, "npc2")).maintenanceEpisodes().length());
        assertEquals(canonicalRevision, new WorldQueryServiceV210(database).revision());
    }

    @Test
    public void migrationFailureRollsBackCompletelyAndRetryCompletesOnceWithoutDeletingLegacyRegistry() {
        WorldDatabaseV210 database = new WorldDatabaseV210(context);
        List<String> legacyIds = new NpcRegistryStore(context).activeNpcIds();
        LegacyWorldImporterV210 failing = new LegacyWorldImporterV210(
                context,
                database,
                () -> { throw new IllegalStateException("intentional migration failure"); });
        try {
            failing.importIfNeeded();
            fail("migration fault must escape");
        } catch (IllegalStateException expected) {
            // expected
        }
        SQLiteDatabase db = database.getReadableDatabase();
        assertEquals("NOT_STARTED", database.metaString(
                db, WorldDatabaseV210.META_MIGRATION_STATE, "NOT_STARTED"));
        assertEquals(0, rowCount(db, "npc_runtime"));
        assertEquals(0, rowCount(db, "world_event"));
        assertEquals(0, rowCount(db, "projection_checkpoint"));

        LegacyWorldImporterV210 normal = new LegacyWorldImporterV210(context, database);
        assertTrue(normal.importIfNeeded());
        assertEquals("COMPLETED", database.metaString(
                db, WorldDatabaseV210.META_MIGRATION_STATE, "NOT_STARTED"));
        assertFalse(normal.importIfNeeded());
        assertEquals(legacyIds, new NpcRegistryStore(context).activeNpcIds());
        database.close();
    }

    @Test
    public void simulationCatchUpIsBoundedConvergentAndSameTargetIsNoOp() {
        WorldSimulationDriverV210 driver = new WorldSimulationDriverV210(context);
        long start = driver.query().lastAdvancedTimeMs();
        long target = start + WorldSimulationDriverV210.MAX_STEP_INTERVAL_MS
                * (WorldSimulationDriverV210.MAX_CATCH_UP_STEPS + 2L);
        WorldSimulationDriverV210.AdvanceResult first = driver.advanceTo(target);
        assertEquals(WorldSimulationDriverV210.MAX_CATCH_UP_STEPS, first.steps);
        assertFalse(first.caughtUp);
        WorldSimulationDriverV210.AdvanceResult second = driver.advanceTo(target);
        assertEquals(2, second.steps);
        assertTrue(second.caughtUp);
        long revision = driver.query().revision();
        WorldSimulationDriverV210.AdvanceResult third = driver.advanceTo(target);
        assertEquals(0, third.steps);
        assertTrue(third.caughtUp);
        assertEquals(revision, driver.query().revision());
    }

    private void commitPeerMessage(
            WorldKernelV210 kernel,
            String messageId,
            String roomId,
            String actor,
            String text,
            long timeMs
    ) {
        JSONObject payload = new JSONObject();
        put(payload, "message_id", messageId);
        put(payload, "room_id", roomId);
        put(payload, "sender_name", actor);
        put(payload, "text", text);
        put(payload, "participant_ids", new JSONArray().put("npc1").put("npc2"));
        put(payload, "observed_wall_time_ms", timeMs);
        put(payload, "defer_projection", true);
        kernel.commit(WorldCommandV210.of(
                WorldCommandV210.NPC_POST_MESSAGE, timeMs, actor, "message:" + messageId, payload));
    }

    private static void seedNpc(WorldDatabaseV210 database, String npcId, long stateVersion, boolean dungeon) {
        JSONObject raw = new JSONObject();
        put(raw, "npc_id", npcId);
        put(raw, "state_version", stateVersion);
        put(raw, "active", true);
        put(raw, "dead", false);
        put(raw, "location", dungeon ? "dungeon_floor_1" : "home");
        put(raw, "activity", dungeon ? "dungeon_exploration" : "idle");
        put(raw, "goal", dungeon ? "dungeon_exploration" : "");
        put(raw, "life_state", new JSONObject());
        put(raw, "dynamic_state", new JSONObject());
        put(raw, "inner_life", new JSONObject());
        put(raw, "intention", new JSONObject());
        put(raw, "relationships", new JSONObject());
        put(raw, "dungeon_present", dungeon);
        put(raw, "dungeon_actor", dungeon ? dungeonActor(1, 10, 1, 1) : new JSONObject());
        database.upsertNpc(database.getWritableDatabase(), CanonicalNpcStateV210.fromJson(npcId, raw), 0L);
    }

    private static JSONObject dungeonActor(int floor, int hp, int x, int y) {
        return json("floor", floor, "hp", hp, "player_x", x, "player_y", y, "last_action", "wait");
    }

    private void clearNpcMemory(String npcId) {
        NpcContexts.storage(context, npcId)
                .getSharedPreferences("npcbrain_memory_v2", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private static int tableCount(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{table});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private static int rowCount(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private static JSONObject json(Object... pairs) {
        JSONObject result = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            put(result, String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    private static void put(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void resetKernel() throws Exception {
        Field field = WorldKernelV210.class.getDeclaredField("instance");
        field.setAccessible(true);
        WorldKernelV210 current = (WorldKernelV210) field.get(null);
        if (current != null) current.database().close();
        field.set(null, null);
    }
}
