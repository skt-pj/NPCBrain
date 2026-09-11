package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Replays committed events into legacy-compatible read projections. */
final class WorldProjectionRunnerV210 {
    private static final String CONVERSATION = "conversation_v210";
    private static final String MEMORY = "memory_v210";
    private static final int BATCH = 64;

    private final Context appContext;
    private final WorldDatabaseV210 database;
    private final ConversationStore conversations;
    private boolean running;

    WorldProjectionRunnerV210(Context context, WorldDatabaseV210 database) {
        appContext = context.getApplicationContext();
        this.database = database;
        conversations = new ConversationStore(appContext);
    }

    synchronized void runPending() {
        if (running) return;
        running = true;
        try {
            projectConversation();
            projectMemory();
        } finally {
            running = false;
        }
    }

    private void projectConversation() {
        while (true) {
            long checkpoint = database.projectionCheckpoint(CONVERSATION);
            List<WorldEventV210> events = database.eventsAfter(checkpoint, BATCH);
            if (events.isEmpty()) return;
            for (WorldEventV210 event : events) {
                try (WorldProjectionScopeV210 ignored = WorldProjectionScopeV210.enter()) {
                    projectConversationEvent(event);
                }
                database.setProjectionCheckpoint(CONVERSATION, event.sequence);
            }
            if (events.size() < BATCH) return;
        }
    }

    private void projectConversationEvent(WorldEventV210 event) {
        String projectionId = event.payload.optString("message_id", "").trim();
        if (projectionId.isEmpty()) projectionId = event.eventId;
        if ("message_posted".equals(event.eventType)) {
            String roomId = event.payload.optString("room_id", "").trim();
            String text = event.payload.optString("text", "").trim();
            if (roomId.isEmpty() || text.isEmpty()) return;
            String senderName = event.payload.optString("sender_name", "").trim();
            if (senderName.isEmpty()) senderName = "user".equals(event.actorId) ? "あなた" : event.actorId;
            conversations.appendNpcMessageWithId(
                    projectionId,
                    roomId,
                    event.actorId,
                    senderName,
                    text,
                    event.payload.optString("action", ""),
                    event.worldTimeMs,
                    event.causationId,
                    event.payload.optJSONArray("brain_trace"));
            return;
        }
        if ("communication_skipped".equals(event.eventType)
                || "communication_deferred".equals(event.eventType)) {
            String roomId = event.payload.optString("room_id", "").trim();
            if (roomId.isEmpty()) return;
            String decision = "communication_deferred".equals(event.eventType)
                    ? BrainCommunicationDecision.DEFER : BrainCommunicationDecision.SKIP;
            conversations.appendNpcRuntimeDecision(
                    projectionId,
                    roomId,
                    event.actorId,
                    event.payload.optString("sender_name", event.actorId),
                    decision,
                    event.payload.optString("action", ""),
                    event.worldTimeMs,
                    event.causationId,
                    event.payload.optJSONArray("brain_trace"));
        }
    }

    private void projectMemory() {
        while (true) {
            long checkpoint = database.projectionCheckpoint(MEMORY);
            List<WorldEventV210> events = database.eventsAfter(checkpoint, BATCH);
            if (events.isEmpty()) return;
            for (WorldEventV210 event : events) {
                try (WorldProjectionScopeV210 ignored = WorldProjectionScopeV210.enter()) {
                    projectMemoryEvent(event);
                }
                database.setProjectionCheckpoint(MEMORY, event.sequence);
            }
            if (events.size() < BATCH) return;
        }
    }

    private void projectMemoryEvent(WorldEventV210 event) {
        if ("memory_candidate_created".equals(event.eventType)) {
            String npcId = normalizedNpc(event.actorId);
            if (npcId.isEmpty()) return;
            MemoryStore memory = new MemoryStore(NpcContexts.storage(appContext, npcId));
            memory.remember(
                    event.payload.optString("input", ""),
                    event.payload.optString("output", ""),
                    event.payload.optString("memory_summary", ""),
                    event.payload.optDouble("importance", 0.5),
                    event.payload.optJSONArray("semantic_facts"));
            return;
        }
        if (!isRememberable(event.eventType)) return;
        Set<String> participants = new LinkedHashSet<>();
        addNpc(participants, event.actorId);
        for (int i = 0; i < event.participantIds.length(); i++) {
            addNpc(participants, event.participantIds.optString(i, ""));
        }
        for (String npcId : participants) {
            MemoryStore memory = new MemoryStore(NpcContexts.storage(appContext, npcId));
            if (alreadyRemembered(memory, event.eventId)) continue;
            String summary = summary(event);
            memory.remember(
                    event.toJson().toString(),
                    summary,
                    summary,
                    importance(event.eventType),
                    new JSONArray());
        }
    }

    private static boolean alreadyRemembered(MemoryStore memory, String eventId) {
        JSONArray episodes = memory.maintenanceEpisodes();
        String marker = "\"event_id\":\"" + eventId + "\"";
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject item = episodes.optJSONObject(i);
            if (item != null && item.optString("input", "").contains(marker)) return true;
        }
        return false;
    }

    private static boolean isRememberable(String type) {
        return !"world_time_advanced".equals(type)
                && !"life_state_refreshed".equals(type)
                && !"inner_life_advanced".equals(type)
                && !"dynamic_state_changed".equals(type)
                && !"brain_decision_applied".equals(type)
                && !"memory_candidate_created".equals(type)
                && !"stale_brain_result".equals(type)
                && !"communication_skipped".equals(type)
                && !"communication_deferred".equals(type)
                && !"migration_baseline".equals(type)
                && !type.trim().isEmpty();
    }

    private static String summary(WorldEventV210 event) {
        String text = event.payload.optString("text", "").trim();
        if (!text.isEmpty()) return event.eventType + ": " + limit(text, 600);
        String action = event.payload.optString("action", "").trim();
        if (!action.isEmpty()) return event.eventType + ": " + limit(action, 600);
        return event.eventType + (event.location.isEmpty() ? "" : " @ " + event.location);
    }

    private static double importance(String type) {
        if ("message_posted".equals(type)) return 0.80;
        if ("death".equals(type) || "npc_died".equals(type) || "dungeon_death".equals(type)) return 1.0;
        if ("relationship_changed".equals(type)) return 0.85;
        if (type.startsWith("dungeon_")) return 0.70;
        return 0.60;
    }

    private static String normalizedNpc(String raw) {
        try {
            return NpcId.of(raw).value();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void addNpc(Set<String> result, String raw) {
        String normalized = normalizedNpc(raw);
        if (!normalized.isEmpty()) result.add(normalized);
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
