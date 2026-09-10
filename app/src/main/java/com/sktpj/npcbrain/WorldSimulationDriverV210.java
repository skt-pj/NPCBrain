package com.sktpj.npcbrain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.util.List;

/** The only time-advance coordinator used by foreground, background job and resume paths. */
final class WorldSimulationDriverV210 {
    static final int MAX_CATCH_UP_STEPS = 48;
    static final long MAX_STEP_INTERVAL_MS = 30L * 60L * 1000L;

    private final Context appContext;
    private final WorldKernelV210 kernel;
    private final WorldDatabaseV210 database;
    private final WorldQueryServiceV210 query;
    private final WorldProjectionRunnerV210 projections;
    private final CanonicalCompatibilityProjectorV210 compatibility;
    private final CanonicalLifeReducerV210 lifeReducer;
    private final DungeonDomainAdapterV210 dungeon;
    private final NpcRegistryStore registry;

    WorldSimulationDriverV210(Context context) {
        appContext = context.getApplicationContext();
        kernel = WorldKernelV210.get(appContext);
        database = kernel.database();
        new LegacyWorldImporterV210(appContext, database).importIfNeeded();
        query = new WorldQueryServiceV210(database);
        projections = new WorldProjectionRunnerV210(appContext, database);
        compatibility = new CanonicalCompatibilityProjectorV210(appContext, database);
        kernel.attachProjectionRunner(projections);
        lifeReducer = new CanonicalLifeReducerV210(appContext);
        dungeon = new DungeonDomainAdapterV210(appContext, database);
        registry = new NpcRegistryStore(appContext);
        projections.runPending();
        compatibility.runPending();
    }

    synchronized AdvanceResult advanceTo(long targetWallTimeMs) {
        long target = Math.max(0L, targetWallTimeMs);
        long cursor = query.lastAdvancedTimeMs();
        if (target <= cursor) {
            projections.runPending();
            compatibility.runPending();
            return new AdvanceResult(0, cursor, target <= cursor);
        }

        int steps = 0;
        while (cursor < target && steps < MAX_CATCH_UP_STEPS) {
            long boundary = Math.min(target, cursor + MAX_STEP_INTERVAL_MS);
            advanceBoundary(boundary);
            cursor = query.lastAdvancedTimeMs();
            if (cursor < boundary) break;
            steps++;
        }
        projections.runPending();
        compatibility.runPending();
        return new AdvanceResult(steps, cursor, cursor >= target);
    }

    private void advanceBoundary(long boundary) {
        List<String> active = registry.activeNpcIds();
        for (String npcId : active) {
            SQLiteDatabase db = database.getReadableDatabase();
            CanonicalNpcStateV210 before = database.loadNpc(db, npcId);
            if (!before.active() || before.dead()) continue;

            CanonicalLifeReducerV210.Result reduced = lifeReducer.reduce(npcId, before, boundary);
            JSONObject lifePayload = new JSONObject();
            try {
                lifePayload.put("life_state", reduced.life.toJson());
                lifePayload.put("event_type", reduced.lifeChanged ? "activity_started" : "life_state_refreshed");
                lifePayload.put("action", reduced.life.currentActivity());
            } catch (Exception ignored) {
            }
            kernel.commit(WorldCommandV210.of(
                    WorldCommandV210.UPSERT_LIFE_STATE,
                    boundary,
                    npcId,
                    "life:" + npcId + ":" + boundary,
                    lifePayload));

            JSONObject innerPayload = new JSONObject();
            try {
                innerPayload.put("inner_life", reduced.innerLife.toJson());
                innerPayload.put("event_type", "inner_life_advanced");
                innerPayload.put("append_local_thought", reduced.appendLocalThought);
            } catch (Exception ignored) {
            }
            kernel.commit(WorldCommandV210.of(
                    WorldCommandV210.UPSERT_INNER_LIFE,
                    boundary,
                    npcId,
                    "inner:" + npcId + ":" + boundary,
                    innerPayload));

            CanonicalNpcStateV210 afterNeeds = database.loadNpc(database.getReadableDatabase(), npcId);
            if (afterNeeds.dungeonPresent() && !afterNeeds.dead()) {
                JSONObject dungeonPayload = dungeon.reduceOneTurn(npcId, boundary);
                if (dungeonPayload != null) {
                    kernel.commit(WorldCommandV210.of(
                            WorldCommandV210.UPSERT_DUNGEON_STATE,
                            boundary,
                            npcId,
                            "dungeon:" + npcId + ":" + boundary,
                            dungeonPayload));
                }
            }
        }

        kernel.commit(WorldCommandV210.of(
                WorldCommandV210.ADVANCE_TIME,
                boundary,
                "",
                "advance:" + boundary,
                new JSONObject()));
        compatibility.runPending();
    }

    WorldQueryServiceV210 query() {
        return query;
    }

    WorldKernelV210 kernel() {
        return kernel;
    }

    static final class AdvanceResult {
        final int steps;
        final long advancedToMs;
        final boolean caughtUp;

        AdvanceResult(int steps, long advancedToMs, boolean caughtUp) {
            this.steps = Math.max(0, steps);
            this.advancedToMs = Math.max(0L, advancedToMs);
            this.caughtUp = caughtUp;
        }
    }
}
