package com.sktpj.npcbrain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Executes DungeonEngine against canonical snapshots without using DungeonStore as runtime SSOT. */
final class DungeonDomainAdapterV210 {
    private final Context appContext;
    private final WorldDatabaseV210 database;
    private final NpcRegistryStore registry;

    DungeonDomainAdapterV210(Context context, WorldDatabaseV210 database) {
        appContext = context.getApplicationContext();
        this.database = database;
        registry = new NpcRegistryStore(appContext);
    }

    JSONObject reduceOneTurn(String npcId, long nowMs) {
        SQLiteDatabase db = database.getReadableDatabase();
        CanonicalNpcStateV210 canonical = database.loadNpc(db, npcId);
        if (!canonical.active() || canonical.dead() || !canonical.dungeonPresent()) return null;

        DungeonState actor = DungeonState.fromJson(canonical.dungeonActor());
        if (actor == null) {
            long seed = stableSeed(npcId, nowMs);
            actor = DungeonGenerator.generate(seed, 1);
            actor.lastAction = "探索を開始";
        }
        if (actor.hp <= 0) return payload(actor, null, new JSONArray(), "dungeon_death");

        JSONObject sharedJson = database.loadDungeonWorld(db, "floor_" + actor.floor);
        DungeonSharedFloor shared = DungeonSharedFloor.fromJson(sharedJson);
        if (shared == null) {
            shared = DungeonSharedFloor.fromState(actor, actor.playerX, actor.playerY, 1L);
        }
        DungeonState working = shared == null ? actor : shared.attach(actor, false);
        if (working == null) working = actor;

        List<DungeonActorContext> peers = peerContexts(db, npcId, working.floor);
        DungeonTurnContext.register(
                working,
                npcId,
                shared == null ? 0L : shared.revision,
                working.playerX,
                working.playerY,
                peers);

        DungeonPersonalityPolicy.Traits traits = traits(npcId);
        DungeonMindStore.Snapshot mind = new DungeonMindStore(appContext).load(npcId);
        DungeonObjective objective = new DungeonObjectiveStore(appContext).load(npcId);
        if (objective == null) objective = DungeonObjective.none();
        DungeonPlan plan = mind == null ? null : mind.plan;
        if (plan == null || !plan.matches(objective)) {
            plan = DungeonPlan.local(objective, traits, working, "Canonical Worldの合法実行");
        }
        DungeonIntent intent = exactBrainIntent(working, traits, mind);
        DungeonStepResult step = DungeonEngine.stepDetailed(working, traits, intent, plan);
        DungeonState next = step == null || step.state == null ? working : step.state;
        DungeonPerception.refreshExploration(next);

        DungeonSharedFloor nextShared;
        if (next.floor == working.floor && shared != null) {
            nextShared = shared.withWorld(next);
        } else {
            nextShared = DungeonSharedFloor.fromState(next, next.playerX, next.playerY, 1L);
        }
        JSONArray peerUpdates = peerUpdates(working, next.floor == working.floor);
        String eventType = next.hp <= 0
                ? "dungeon_death"
                : next.floor != working.floor ? "dungeon_floor_changed" : "dungeon_action";
        return payload(next, nextShared, peerUpdates, eventType);
    }

    private JSONObject payload(
            DungeonState next,
            DungeonSharedFloor shared,
            JSONArray peerUpdates,
            String eventType
    ) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("dungeon_present", next != null && next.hp > 0);
            payload.put("dungeon_actor", next == null ? new JSONObject() : next.toJson());
            payload.put("dungeon_world", shared == null ? new JSONObject() : shared.toJson());
            payload.put("peer_actor_updates", peerUpdates == null ? new JSONArray() : peerUpdates);
            payload.put("event_type", eventType);
            if (next != null) {
                payload.put("action", next.lastAction == null ? "" : next.lastAction);
                payload.put("floor", next.floor);
                payload.put("turn", next.turn);
                payload.put("hp", next.hp);
            }
        } catch (Exception ignored) {
        }
        return payload;
    }

    private List<DungeonActorContext> peerContexts(SQLiteDatabase db, String ownerId, int floor) {
        List<DungeonActorContext> peers = new ArrayList<>();
        for (String peerId : registry.activeNpcIds()) {
            if (ownerId.equals(peerId)) continue;
            CanonicalNpcStateV210 peer = database.loadNpc(db, peerId);
            if (!peer.active() || peer.dead() || !peer.dungeonPresent()) continue;
            DungeonState state = DungeonState.fromJson(peer.dungeonActor());
            if (state == null || state.floor != floor || state.hp <= 0) continue;
            peers.add(new DungeonActorContext(
                    peerId,
                    floor,
                    state.playerX,
                    state.playerY,
                    state.hp,
                    state.maxHp));
        }
        return peers;
    }

    private static JSONArray peerUpdates(DungeonState working, boolean sameFloor) {
        JSONArray result = new JSONArray();
        if (!sameFloor) return result;
        for (DungeonActorContext peer : DungeonTurnContext.peers(working)) {
            JSONObject json = new JSONObject();
            try {
                json.put("npc_id", peer.npcId);
                json.put("floor", peer.floor);
                json.put("player_x", peer.x);
                json.put("player_y", peer.y);
                json.put("hp", peer.hp);
                result.put(json);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private DungeonIntent exactBrainIntent(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonMindStore.Snapshot mind
    ) {
        if (mind != null && mind.intent != null
                && mind.intent.isBrain()
                && mind.intent.floor == state.floor
                && mind.intent.turn == state.turn) {
            return mind.intent;
        }
        return DungeonIntent.localFallback(state, traits, "Canonical Brain planを合法実行");
    }

    private DungeonPersonalityPolicy.Traits traits(String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
        return new DungeonPersonalityPolicy.Traits(
                character.traitPercent(CharacterStateStore.extraversionKey()),
                character.traitPercent(CharacterStateStore.neuroticismKey()),
                character.traitPercent(CharacterStateStore.agreeablenessKey()),
                character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                character.traitPercent(CharacterStateStore.opennessKey()));
    }

    private static long stableSeed(String npcId, long nowMs) {
        long bucket = Math.max(1L, nowMs / 86_400_000L);
        return 0x4e5043425241494eL ^ ((long) npcId.hashCode() << 17) ^ bucket;
    }
}
