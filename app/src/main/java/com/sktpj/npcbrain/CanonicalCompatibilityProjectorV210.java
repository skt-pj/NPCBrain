package com.sktpj.npcbrain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.util.List;

/** Keeps legacy UI readers synchronized from canonical state; never acts as a world writer. */
final class CanonicalCompatibilityProjectorV210 {
    private static final String PROJECTOR = "compat_state_v210";
    private static final int BATCH = 64;

    private final Context appContext;
    private final WorldDatabaseV210 database;

    CanonicalCompatibilityProjectorV210(Context context, WorldDatabaseV210 database) {
        appContext = context.getApplicationContext();
        this.database = database;
    }

    synchronized void runPending() {
        while (true) {
            long checkpoint = database.projectionCheckpoint(PROJECTOR);
            List<WorldEventV210> events = database.eventsAfter(checkpoint, BATCH);
            if (events.isEmpty()) return;
            for (WorldEventV210 event : events) {
                project(event);
                database.setProjectionCheckpoint(PROJECTOR, event.sequence);
            }
            if (events.size() < BATCH) return;
        }
    }

    private void project(WorldEventV210 event) {
        String npcId = normalizedNpc(event.actorId);
        if (npcId.isEmpty()) return;
        SQLiteDatabase db = database.getReadableDatabase();
        CanonicalNpcStateV210 canonical = database.loadNpc(db, npcId);

        JSONObject lifeJson = canonical.lifeState();
        if (lifeJson.length() > 0) {
            try {
                LifeState life = LifeState.fromJson(
                        lifeJson,
                        NpcId.of(npcId),
                        database.metaLong(db, WorldDatabaseV210.META_WORLD_TIME, 0L));
                new WorldStateStore(appContext).saveLifeState(life);
            } catch (Exception ignored) {
            }
        }

        JSONObject inner = canonical.innerLife();
        if (inner.length() > 0) {
            try {
                CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
                double e = character.traitPercent(CharacterStateStore.extraversionKey()) / 100.0;
                double n = character.traitPercent(CharacterStateStore.neuroticismKey()) / 100.0;
                double o = character.traitPercent(CharacterStateStore.opennessKey()) / 100.0;
                NpcInnerLifeState state = NpcInnerLifeState.fromJson(
                        inner,
                        database.metaLong(db, WorldDatabaseV210.META_WORLD_TIME, 0L),
                        e, n, o, npcId);
                new NpcInnerLifeStore(NpcContexts.storage(appContext, npcId)).save(state);
            } catch (Exception ignored) {
            }
        }

        JSONObject dungeon = canonical.dungeonActor();
        try {
            DungeonPresenceStore presence = new DungeonPresenceStore(appContext);
            presence.setPresent(npcId, canonical.dungeonPresent() && !canonical.dead());
            if (dungeon.length() > 0) {
                DungeonState state = DungeonState.fromJson(dungeon);
                if (state != null) new DungeonStore(appContext).saveRaw(npcId, state);
            }
            JSONObject shared = event.payload.optJSONObject("dungeon_world");
            if (shared != null && shared.length() > 0) {
                DungeonSharedFloor floor = DungeonSharedFloor.fromJson(shared);
                if (floor != null) new DungeonWorldStore(appContext).save(floor);
            }
        } catch (Exception ignored) {
        }
    }

    private static String normalizedNpc(String value) {
        try {
            return NpcId.of(value).value();
        } catch (Exception ignored) {
            return "";
        }
    }
}
