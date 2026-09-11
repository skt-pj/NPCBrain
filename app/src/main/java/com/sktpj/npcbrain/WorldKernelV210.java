package com.sktpj.npcbrain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Sole production commit authority for mutable world state in v2.1. */
final class WorldKernelV210 {
    private static volatile WorldKernelV210 instance;

    private final WorldDatabaseV210 database;
    private final Object commitLock = new Object();
    private volatile WorldProjectionRunnerV210 projections;

    static WorldKernelV210 get(Context context) {
        WorldKernelV210 current = instance;
        if (current != null) return current;
        synchronized (WorldKernelV210.class) {
            if (instance == null) instance = new WorldKernelV210(context.getApplicationContext());
            return instance;
        }
    }

    private WorldKernelV210(Context context) {
        database = new WorldDatabaseV210(context);
    }

    WorldDatabaseV210 database() {
        return database;
    }

    void attachProjectionRunner(WorldProjectionRunnerV210 runner) {
        if (runner != null) projections = runner;
    }

    WorldCommitResultV210 commit(WorldCommandV210 command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        WorldCommitResultV210 result;
        synchronized (commitLock) {
            result = commitLocked(command);
        }
        WorldProjectionRunnerV210 runner = projections;
        boolean deferProjection = command.payload.optBoolean("defer_projection", false);
        if (runner != null && result.committed && !deferProjection) runner.runPending();
        return result;
    }

    private WorldCommitResultV210 commitLocked(WorldCommandV210 command) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject duplicate = database.processedResult(db, command.idempotencyKey);
            if (duplicate != null) {
                long revision = duplicate.optLong("revision",
                        database.metaLong(db, WorldDatabaseV210.META_REVISION, 0L));
                long worldTime = duplicate.optLong("world_time_ms",
                        database.metaLong(db, WorldDatabaseV210.META_WORLD_TIME, 0L));
                db.setTransactionSuccessful();
                return new WorldCommitResultV210(false, true, revision, worldTime,
                        new ArrayList<>(), duplicate);
            }

            long currentRevision = database.metaLong(db, WorldDatabaseV210.META_REVISION, 0L);
            long currentTime = database.metaLong(db, WorldDatabaseV210.META_WORLD_TIME, 0L);
            boolean advanceTime = WorldCommandV210.ADVANCE_TIME.equals(command.commandType);
            boolean simulationBoundary = command.payload.optBoolean("simulation_boundary", false);
            long commandTime;
            if (advanceTime || simulationBoundary) {
                commandTime = Math.max(currentTime, command.requestedTimeMs);
            } else {
                // External commands never leap the simulation clock. The simulation driver is the
                // only owner of world-time advancement; wall time can be carried inside payloads.
                commandTime = currentTime > 0L ? currentTime : command.requestedTimeMs;
            }

            if (advanceTime && commandTime <= currentTime) {
                long lastAdvanced = database.metaLong(
                        db, WorldDatabaseV210.META_LAST_ADVANCED, 0L);
                if (commandTime > lastAdvanced) {
                    database.putMeta(db, WorldDatabaseV210.META_LAST_ADVANCED,
                            Long.toString(commandTime));
                }
                JSONObject noOp = resultJson(currentRevision, currentTime, "no_op");
                database.recordProcessed(db, command.idempotencyKey, command.commandType,
                        currentRevision, noOp);
                db.setTransactionSuccessful();
                return new WorldCommitResultV210(false, false, currentRevision, currentTime,
                        new ArrayList<>(), noOp);
            }

            long nextRevision = currentRevision + 1L;
            long nextSequence = database.metaLong(db, WorldDatabaseV210.META_NEXT_SEQUENCE, 1L);
            List<PendingEvent> pending = new ArrayList<>();
            JSONObject commandResult = new JSONObject();

            reduce(db, command, currentRevision, nextRevision, commandTime, pending, commandResult);

            List<WorldEventV210> committedEvents = new ArrayList<>();
            for (int i = 0; i < pending.size(); i++) {
                PendingEvent event = pending.get(i);
                WorldEventV210 committed = new WorldEventV210(
                        nextSequence++,
                        UUID.randomUUID().toString(),
                        nextRevision,
                        commandTime,
                        event.type,
                        event.actorId,
                        event.participants,
                        event.location,
                        command.correlationId,
                        event.causationId,
                        command.idempotencyKey + ":event:" + i,
                        event.payload);
                database.insertEvent(db, committed);
                committedEvents.add(committed);
            }

            database.putMeta(db, WorldDatabaseV210.META_REVISION, Long.toString(nextRevision));
            database.putMeta(db, WorldDatabaseV210.META_NEXT_SEQUENCE, Long.toString(nextSequence));
            if (advanceTime) {
                database.putMeta(db, WorldDatabaseV210.META_WORLD_TIME, Long.toString(commandTime));
                database.putMeta(db, WorldDatabaseV210.META_LAST_ADVANCED, Long.toString(commandTime));
            }

            try {
                commandResult.put("status", commandResult.optString("status", "committed"));
                commandResult.put("revision", nextRevision);
                commandResult.put("world_time_ms", commandTime);
                JSONArray ids = new JSONArray();
                for (WorldEventV210 event : committedEvents) ids.put(event.eventId);
                commandResult.put("event_ids", ids);
            } catch (Exception ignored) {
            }
            database.recordProcessed(db, command.idempotencyKey, command.commandType,
                    nextRevision, commandResult);
            db.setTransactionSuccessful();
            return new WorldCommitResultV210(true, false, nextRevision, commandTime,
                    committedEvents, commandResult);
        } finally {
            db.endTransaction();
        }
    }

    private void reduce(
            SQLiteDatabase db,
            WorldCommandV210 command,
            long currentRevision,
            long nextRevision,
            long worldTime,
            List<PendingEvent> events,
            JSONObject result
    ) {
        String type = command.commandType;
        if (WorldCommandV210.ADVANCE_TIME.equals(type)) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("from_ms", database.metaLong(db, WorldDatabaseV210.META_WORLD_TIME, 0L));
                payload.put("to_ms", worldTime);
            } catch (Exception ignored) {
            }
            events.add(new PendingEvent("world_time_advanced", "", new JSONArray(), "", "", payload));
            return;
        }

        if (WorldCommandV210.USER_POST_MESSAGE.equals(type)
                || WorldCommandV210.NPC_POST_MESSAGE.equals(type)) {
            validateMessage(command);
            JSONObject payload = copy(command.payload);
            if (!payload.has("observed_wall_time_ms")) {
                try { payload.put("observed_wall_time_ms", command.requestedTimeMs); }
                catch (Exception ignored) {}
            }
            JSONArray participants = array(payload.optJSONArray("participant_ids"));
            events.add(new PendingEvent("message_posted", command.actorId, participants,
                    payload.optString("location", ""), payload.optString("causation_id", ""), payload));
            return;
        }

        if (WorldCommandV210.COMMUNICATION_SKIPPED.equals(type)
                || WorldCommandV210.COMMUNICATION_DEFERRED.equals(type)) {
            CanonicalNpcStateV210 actor = requireActiveNpc(db, command.actorId);
            JSONObject payload = copy(command.payload);
            String eventType = WorldCommandV210.COMMUNICATION_DEFERRED.equals(type)
                    ? "communication_deferred" : "communication_skipped";
            events.add(new PendingEvent(eventType, actor.npcId(),
                    array(payload.optJSONArray("participant_ids")), actor.location(),
                    payload.optString("causation_id", ""), payload));
            return;
        }

        if (WorldCommandV210.UPSERT_LIFE_STATE.equals(type)) {
            CanonicalNpcStateV210 current = database.loadNpc(db, command.actorId);
            CanonicalNpcStateV210 next = current.withLife(command.payload.optJSONObject("life_state"));
            database.upsertNpc(db, next, nextRevision);
            JSONObject payload = copy(command.payload);
            events.add(new PendingEvent(payload.optString("event_type", "life_state_changed"),
                    next.npcId(), participantsOf(next.npcId()), next.location(),
                    payload.optString("causation_id", ""), payload));
            return;
        }

        if (WorldCommandV210.UPSERT_DYNAMIC_STATE.equals(type)) {
            CanonicalNpcStateV210 current = requireActiveNpc(db, command.actorId);
            CanonicalNpcStateV210 next = current.withDynamicState(
                    command.payload.optJSONObject("dynamic_state"));
            database.upsertNpc(db, next, nextRevision);
            JSONObject payload = copy(command.payload);
            events.add(new PendingEvent(payload.optString("event_type", "dynamic_state_changed"),
                    next.npcId(), participantsOf(next.npcId()), next.location(),
                    payload.optString("causation_id", ""), payload));
            return;
        }

        if (WorldCommandV210.UPSERT_INNER_LIFE.equals(type)) {
            CanonicalNpcStateV210 current = database.loadNpc(db, command.actorId);
            CanonicalNpcStateV210 next = current.withInnerLife(command.payload.optJSONObject("inner_life"));
            database.upsertNpc(db, next, nextRevision);
            JSONObject payload = copy(command.payload);
            events.add(new PendingEvent(payload.optString("event_type", "inner_life_changed"),
                    next.npcId(), participantsOf(next.npcId()), next.location(),
                    payload.optString("causation_id", ""), payload));
            return;
        }

        if (WorldCommandV210.UPSERT_DUNGEON_STATE.equals(type)) {
            CanonicalNpcStateV210 current = requireActiveNpc(db, command.actorId);
            JSONObject actorState = copy(command.payload.optJSONObject("dungeon_actor"));
            boolean present = command.payload.optBoolean("dungeon_present", true);
            CanonicalNpcStateV210 next = current.withDungeon(present, actorState);
            if (actorState.optInt("hp", 1) <= 0) next = next.withFlags(next.active(), true);
            database.upsertNpc(db, next, nextRevision);

            JSONObject shared = command.payload.optJSONObject("dungeon_world");
            if (shared != null && shared.length() > 0) {
                int floor = Math.max(1, shared.optInt("floor", actorState.optInt("floor", 1)));
                database.upsertDungeonWorld(db, "floor_" + floor, floor, shared, nextRevision);
            }

            Set<String> participantSet = new LinkedHashSet<>();
            participantSet.add(next.npcId());
            JSONArray suppliedParticipants = command.payload.optJSONArray("participant_ids");
            for (int i = 0; suppliedParticipants != null && i < suppliedParticipants.length(); i++) {
                addNpc(participantSet, suppliedParticipants.optString(i, ""));
            }

            JSONArray peerUpdates = command.payload.optJSONArray("peer_actor_updates");
            for (int i = 0; peerUpdates != null && i < peerUpdates.length(); i++) {
                JSONObject peerUpdate = peerUpdates.optJSONObject(i);
                if (peerUpdate == null) continue;
                String peerId = normalizedNpc(peerUpdate.optString("npc_id", ""));
                if (peerId.isEmpty() || peerId.equals(next.npcId())) continue;
                CanonicalNpcStateV210 peer = database.loadNpc(db, peerId);
                if (!peer.active() || peer.dead() || !peer.dungeonPresent()) continue;
                JSONObject peerActor = peer.dungeonActor();
                int oldHp = peerActor.optInt("hp", 0);
                int oldX = peerActor.optInt("player_x", -1);
                int oldY = peerActor.optInt("player_y", -1);
                int hp = Math.max(0, peerUpdate.optInt("hp", oldHp));
                int x = peerUpdate.optInt("player_x", oldX);
                int y = peerUpdate.optInt("player_y", oldY);
                int floor = Math.max(1, peerUpdate.optInt("floor", peerActor.optInt("floor", 1)));
                try {
                    peerActor.put("hp", hp);
                    peerActor.put("player_x", x);
                    peerActor.put("player_y", y);
                    peerActor.put("floor", floor);
                } catch (Exception ignored) {
                }
                boolean changed = hp != oldHp || x != oldX || y != oldY;
                CanonicalNpcStateV210 peerNext = peer.withDungeon(hp > 0, peerActor);
                if (hp <= 0) peerNext = peerNext.withFlags(peerNext.active(), true);
                database.upsertNpc(db, peerNext, nextRevision);
                participantSet.add(peerId);
                if (changed) {
                    JSONObject peerPayload = new JSONObject();
                    try {
                        peerPayload.put("source_actor_id", next.npcId());
                        peerPayload.put("dungeon_actor", peerActor);
                        peerPayload.put("hp_before", oldHp);
                        peerPayload.put("hp_after", hp);
                    } catch (Exception ignored) {
                    }
                    String peerEventType = hp <= 0
                            ? "dungeon_death"
                            : hp < oldHp ? "dungeon_peer_damaged" : "dungeon_peer_state_changed";
                    events.add(new PendingEvent(peerEventType, peerId,
                            participantsOf(next.npcId(), peerId), peerNext.location(),
                            command.payload.optString("causation_id", ""), peerPayload));
                }
            }

            JSONObject payload = copy(command.payload);
            events.add(new PendingEvent(payload.optString("event_type", "dungeon_action"),
                    next.npcId(), toArray(participantSet), next.location(),
                    payload.optString("causation_id", ""), payload));
            return;
        }

        if (WorldCommandV210.SET_DUNGEON_PRESENCE.equals(type)) {
            CanonicalNpcStateV210 current = database.loadNpc(db, command.actorId);
            boolean present = command.payload.optBoolean("present", false) && !current.dead();
            CanonicalNpcStateV210 next = current.withDungeon(present, current.dungeonActor());
            database.upsertNpc(db, next, nextRevision);
            events.add(new PendingEvent(present ? "dungeon_entered" : "dungeon_left",
                    next.npcId(), participantsOf(next.npcId()), next.location(), "", copy(command.payload)));
            return;
        }

        if (WorldCommandV210.APPLY_RELATIONSHIP.equals(type)) {
            CanonicalNpcStateV210 current = requireActiveNpc(db, command.actorId);
            JSONObject relationshipState = copy(command.payload.optJSONObject("relationships"));
            CanonicalNpcStateV210 next = current.withRelationships(relationshipState);
            database.upsertNpc(db, next, nextRevision);
            JSONArray participants = array(command.payload.optJSONArray("participant_ids"));
            events.add(new PendingEvent("relationship_changed", next.npcId(), participants,
                    next.location(), command.payload.optString("causation_id", ""), copy(command.payload)));
            return;
        }

        if (WorldCommandV210.APPLY_BRAIN_DECISION.equals(type)) {
            CanonicalNpcStateV210 current = requireActiveNpc(db, command.actorId);
            long basisStateVersion = command.payload.optLong("basis_state_version", -1L);
            long basisRevision = command.payload.optLong("basis_revision", -1L);
            boolean stale = basisStateVersion >= 0L
                    ? current.stateVersion() != basisStateVersion
                    : basisRevision >= 0L && basisRevision != currentRevision;
            if (stale) {
                try {
                    result.put("status", "stale_brain_result");
                    result.put("basis_revision", basisRevision);
                    result.put("current_revision", currentRevision);
                    result.put("basis_state_version", basisStateVersion);
                    result.put("current_state_version", current.stateVersion());
                } catch (Exception ignored) {
                }
                events.add(new PendingEvent("stale_brain_result", current.npcId(),
                        participantsOf(current.npcId()), current.location(), "", copy(command.payload)));
                return;
            }

            CanonicalNpcStateV210 next = current;
            JSONObject dynamicState = command.payload.optJSONObject("dynamic_state");
            if (dynamicState != null) next = next.withDynamicState(dynamicState);
            JSONObject nextIntention = command.payload.optJSONObject("intention");
            if (nextIntention != null) next = next.withIntention(nextIntention);
            if (next.stateVersion() != current.stateVersion()) {
                database.upsertNpc(db, next, nextRevision);
            }

            JSONObject communication = command.payload.optJSONObject("communication");
            if (communication != null) {
                String decision = communication.optString("decision", "").trim().toLowerCase();
                JSONObject messagePayload = copy(command.payload.optJSONObject("message"));
                if ("send".equals(decision) && !messagePayload.optString("text", "").trim().isEmpty()) {
                    validateMessagePayload(messagePayload);
                    events.add(new PendingEvent("message_posted", current.npcId(),
                            array(messagePayload.optJSONArray("participant_ids")), current.location(),
                            messagePayload.optString("causation_id", ""), messagePayload));
                } else if ("defer".equals(decision)) {
                    events.add(new PendingEvent("communication_deferred", current.npcId(),
                            array(messagePayload.optJSONArray("participant_ids")), current.location(), "", copy(command.payload)));
                } else {
                    events.add(new PendingEvent("communication_skipped", current.npcId(),
                            array(messagePayload.optJSONArray("participant_ids")), current.location(), "", copy(command.payload)));
                }
            }

            JSONObject brainPayload = copy(command.payload);
            events.add(new PendingEvent("brain_decision_applied", current.npcId(),
                    array(command.payload.optJSONArray("participant_ids")), current.location(),
                    command.payload.optString("causation_id", ""), brainPayload));
            return;
        }

        if (WorldCommandV210.RESET_NPC_BRAIN.equals(type)) {
            CanonicalNpcStateV210 current = database.loadNpc(db, command.actorId);
            JSONObject raw = current.toJson();
            try {
                raw.put("dynamic_state", new JSONObject());
                raw.put("inner_life", new JSONObject());
                raw.put("intention", new JSONObject());
                raw.put("state_version", current.stateVersion() + 1L);
            } catch (Exception ignored) {
            }
            CanonicalNpcStateV210 next = CanonicalNpcStateV210.fromJson(current.npcId(), raw);
            database.upsertNpc(db, next, nextRevision);
            events.add(new PendingEvent("brain_state_reset", next.npcId(), participantsOf(next.npcId()),
                    next.location(), "", new JSONObject()));
            return;
        }

        if (WorldCommandV210.APPEND_WORLD_EVENT.equals(type)) {
            JSONObject payload = copy(command.payload.optJSONObject("event_payload"));
            events.add(new PendingEvent(
                    command.payload.optString("event_type", "world_event"),
                    command.actorId,
                    array(command.payload.optJSONArray("participant_ids")),
                    command.payload.optString("location", ""),
                    command.payload.optString("causation_id", ""),
                    payload));
            return;
        }

        throw new IllegalArgumentException("Unsupported world command: " + type);
    }

    private CanonicalNpcStateV210 requireActiveNpc(SQLiteDatabase db, String npcId) {
        CanonicalNpcStateV210 state = database.loadNpc(db, npcId);
        if (!state.active() || state.dead()) {
            throw new IllegalStateException("actor cannot act: " + state.npcId());
        }
        return state;
    }

    private static void validateMessage(WorldCommandV210 command) {
        if (!"user".equals(command.actorId)) NpcId.of(command.actorId);
        validateMessagePayload(command.payload);
    }

    private static void validateMessagePayload(JSONObject payload) {
        if (payload == null) throw new IllegalArgumentException("message payload required");
        if (payload.optString("room_id", "").trim().isEmpty()) {
            throw new IllegalArgumentException("room_id required");
        }
        if (payload.optString("text", "").trim().isEmpty()) {
            throw new IllegalArgumentException("message text required");
        }
    }

    private static JSONObject resultJson(long revision, long worldTime, String status) {
        JSONObject result = new JSONObject();
        try {
            result.put("revision", revision);
            result.put("world_time_ms", worldTime);
            result.put("status", status);
        } catch (Exception ignored) {
        }
        return result;
    }

    private static JSONArray participantsOf(String... npcIds) {
        JSONArray result = new JSONArray();
        Set<String> unique = new LinkedHashSet<>();
        if (npcIds != null) {
            for (String npcId : npcIds) addNpc(unique, npcId);
        }
        for (String npcId : unique) result.put(npcId);
        return result;
    }

    private static JSONArray toArray(Set<String> values) {
        JSONArray result = new JSONArray();
        if (values != null) for (String value : values) result.put(value);
        return result;
    }

    private static void addNpc(Set<String> values, String raw) {
        if (values == null) return;
        String normalized = normalizedNpc(raw);
        if (!normalized.isEmpty()) values.add(normalized);
    }

    private static String normalizedNpc(String raw) {
        try {
            return NpcId.of(raw).value();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JSONArray array(JSONArray source) {
        try {
            return source == null ? new JSONArray() : new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static final class PendingEvent {
        final String type;
        final String actorId;
        final JSONArray participants;
        final String location;
        final String causationId;
        final JSONObject payload;

        PendingEvent(
                String type,
                String actorId,
                JSONArray participants,
                String location,
                String causationId,
                JSONObject payload
        ) {
            this.type = type == null ? "" : type.trim();
            this.actorId = actorId == null ? "" : actorId.trim();
            this.participants = array(participants);
            this.location = location == null ? "" : location.trim();
            this.causationId = causationId == null ? "" : causationId.trim();
            this.payload = copy(payload);
        }
    }
}
