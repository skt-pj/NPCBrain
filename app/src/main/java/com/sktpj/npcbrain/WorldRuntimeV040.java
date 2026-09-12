package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * Compatibility facade retained for the pre-v2 conversation UI.
 *
 * Since v2.1.1 this class is read-through/write-through to the canonical world. It owns no clock,
 * no WorldStateStore and no simulation loop. UI construction/resume calls therefore cannot create
 * or advance world state through this compatibility API.
 */
final class WorldRuntimeV040 {
    private static final String DIRECT_PREFIX = "direct_";
    private static final int EVENT_BATCH = 128;

    private final Context appContext;
    private final NpcRegistryStore npcRegistry;
    private final WorldKernelV210 kernel;
    private final WorldDatabaseV210 database;
    private final WorldQueryServiceV210 query;

    WorldRuntimeV040(Context context) {
        appContext = context.getApplicationContext();
        npcRegistry = new NpcRegistryStore(appContext);
        kernel = WorldKernelV210.get(appContext);
        database = kernel.database();
        new LegacyWorldImporterV210(appContext, database).importIfNeeded();
        query = new WorldQueryServiceV210(database);
    }

    JSONObject attachUserMessageEvent(String roomId, JSONObject userMessage) {
        JSONObject message = copy(userMessage);
        String messageId = message.optString("id", "").trim();
        if (!messageId.isEmpty()) {
            try { message.put("cause_event_id", messageId); } catch (Exception ignored) {}
        }
        return message;
    }

    WorldEvent attachIncomingMessageEvent(String roomId, JSONObject incomingMessage) {
        JSONObject message = copy(incomingMessage);
        String messageId = message.optString("id", "").trim();
        if (messageId.isEmpty()) return null;
        WorldEvent existing = eventById(messageId);
        if (existing != null) return existing;

        // ConversationStore writes are canonical before the compatibility runtime sees them. If an
        // old caller supplies a detached message, return a non-persisted compatibility view instead
        // of creating a second event stream.
        JSONObject payload = new JSONObject();
        try {
            payload.put("message_id", messageId);
            payload.put("room", room(roomId).toJson());
            payload.put("sender_id", message.optString("sender_id", "user"));
            payload.put("text", message.optString("text", ""));
        } catch (Exception ignored) {
        }
        return new WorldEvent(
                messageId,
                "message_posted",
                message.optString("sender_id", "user"),
                "",
                message.optLong("time_ms", now()),
                "",
                payload,
                message.optString("cause_event_id", ""));
    }

    /** Compatibility no-op: time is advanced only by WorldSimulationDriverV210. */
    void syncAllNow() {
        // Intentionally empty.
    }

    JSONArray events() {
        JSONArray result = new JSONArray();
        long checkpoint = 0L;
        while (true) {
            List<WorldEventV210> batch = database.eventsAfter(checkpoint, EVENT_BATCH);
            if (batch.isEmpty()) break;
            for (WorldEventV210 event : batch) {
                result.put(legacyEventJson(event));
                checkpoint = Math.max(checkpoint, event.sequence);
            }
            if (batch.size() < EVENT_BATCH) break;
        }
        return result;
    }

    long now() {
        long value = query.worldTimeMs();
        return value > 0L ? value : System.currentTimeMillis();
    }

    LifeState lifeState(String npcId) {
        String id = NpcId.of(npcId).value();
        JSONObject snapshot = query.snapshot(id);
        JSONObject npc = snapshot.optJSONObject("npc");
        JSONObject life = npc == null ? null : npc.optJSONObject("life_state");
        return LifeState.fromJson(life, NpcId.of(id), snapshot.optLong("world_time_ms", now()));
    }

    LifeState updateScheduleEntry(
            String npcId,
            ScheduleSlot replacement,
            String reason
    ) {
        String id = NpcId.of(npcId).value();
        LifeState current = lifeState(id);
        DailySchedule schedule = scheduleFor(NpcId.of(id), current);
        DailySchedule updated = schedule.replaceSlot(replacement);
        LifeState next = current.withSchedule(now(), updated.toJson());
        JSONObject payload = new JSONObject();
        try {
            payload.put("life_state", next.toJson());
            payload.put("event_type", "schedule_changed");
            payload.put("action", reason == null ? "schedule_changed" : reason.trim());
        } catch (Exception ignored) {
        }
        kernel.commit(WorldCommandV210.of(
                WorldCommandV210.UPSERT_LIFE_STATE,
                System.currentTimeMillis(),
                id,
                "schedule:" + id + ":" + UUID.randomUUID(),
                payload));
        return lifeState(id);
    }

    String messageCauseForNpc(String npcId, String triggerEventId) {
        // The committed message/world event is already the causal anchor. Do not append a second
        // compatibility-only conversation_context event.
        return triggerEventId == null ? "" : triggerEventId.trim();
    }

    WorldEvent eventById(String eventId) {
        String wanted = eventId == null ? "" : eventId.trim();
        if (wanted.isEmpty()) return null;
        long checkpoint = 0L;
        while (true) {
            List<WorldEventV210> batch = database.eventsAfter(checkpoint, EVENT_BATCH);
            if (batch.isEmpty()) return null;
            for (WorldEventV210 event : batch) {
                if (wanted.equals(event.eventId)) return legacyEvent(event);
                checkpoint = Math.max(checkpoint, event.sequence);
            }
            if (batch.size() < EVENT_BATCH) return null;
        }
    }

    Room room(String roomId) {
        String directNpcId = directNpcId(roomId);
        if (!directNpcId.isEmpty()) {
            return new Room(roomId, "direct_chat", "user", directNpcId);
        }
        List<String> peer = NpcPeerRoomPolicy.participants(roomId);
        if (!peer.isEmpty()) {
            return new Room(roomId, "npc_peer_chat", peer.toArray(new String[0]));
        }
        if (DemoRuntimeV032.ROOM_GROUP.equals(roomId)) {
            List<String> activeNpcIds = npcRegistry.activeNpcIds();
            String[] members = new String[activeNpcIds.size() + 1];
            members[0] = "user";
            for (int i = 0; i < activeNpcIds.size(); i++) members[i + 1] = activeNpcIds.get(i);
            return new Room(roomId, "group_chat", members);
        }
        return new Room(roomId, "unknown", "user");
    }

    private String directNpcId(String roomId) {
        if (roomId == null || !roomId.startsWith(DIRECT_PREFIX)) return "";
        String raw = roomId.substring(DIRECT_PREFIX.length()).trim();
        if (raw.isEmpty()) return "";
        try {
            String id = NpcId.of(raw).value();
            return npcRegistry.contains(id) ? id : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private DailySchedule scheduleFor(NpcId npcId, LifeState current) {
        DailySchedule saved = DailySchedule.fromJson(npcId, current.dailySchedule());
        if (saved != null) return saved;
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId.value()));
        return DailySchedule.profileFor(npcId, character.age(), character.occupation());
    }

    private static WorldEvent legacyEvent(WorldEventV210 event) {
        if (event == null) return null;
        return new WorldEvent(
                event.eventId,
                event.eventType,
                event.actorId,
                event.participantIds.length() > 0 ? event.participantIds.optString(0, "") : "",
                event.worldTimeMs,
                event.location,
                event.payload,
                event.causationId);
    }

    private static JSONObject legacyEventJson(WorldEventV210 event) {
        WorldEvent legacy = legacyEvent(event);
        return legacy == null ? new JSONObject() : legacy.toJson();
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return json == null ? new JSONObject() : new JSONObject(json.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
