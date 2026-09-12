package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/** Canonical gateway for user/NPC communication actions. */
final class WorldConversationGatewayV210 {
    private final Context appContext;
    private final WorldKernelV210 kernel;
    private final NpcRegistryStore registry;

    WorldConversationGatewayV210(Context context) {
        appContext = context.getApplicationContext();
        kernel = WorldKernelV210.get(appContext);
        new LegacyWorldImporterV210(appContext, kernel.database()).importIfNeeded();
        WorldProjectionRunnerV210 runner = new WorldProjectionRunnerV210(appContext, kernel.database());
        kernel.attachProjectionRunner(runner);
        registry = new NpcRegistryStore(appContext);
    }

    JSONObject postMessage(
            String fixedMessageId,
            String roomId,
            String senderId,
            String senderName,
            String text,
            String action,
            long timeMs,
            String causationId,
            JSONArray brainTrace
    ) {
        String sender = safe(senderId);
        boolean user = "user".equals(sender);
        JSONObject payload = new JSONObject();
        String logicalMessageId = safe(fixedMessageId);
        String commandKey = logicalMessageId.isEmpty()
                ? "message:" + UUID.randomUUID()
                : "message:" + logicalMessageId;
        try {
            payload.put("message_id", logicalMessageId);
            payload.put("room_id", safe(roomId));
            payload.put("sender_name", safe(senderName));
            payload.put("text", safe(text));
            payload.put("action", safe(action));
            payload.put("causation_id", safe(causationId));
            payload.put("observed_wall_time_ms", Math.max(0L, timeMs));
            payload.put("brain_trace", brainTrace == null ? new JSONArray() : new JSONArray(brainTrace.toString()));
            payload.put("participant_ids", participants(roomId));
        } catch (Exception ignored) {
        }
        WorldCommitResultV210 commit = kernel.commit(WorldCommandV210.of(
                user ? WorldCommandV210.USER_POST_MESSAGE : WorldCommandV210.NPC_POST_MESSAGE,
                timeMs,
                sender,
                commandKey,
                payload));
        String projectedId = logicalMessageId;
        if (projectedId.isEmpty()) {
            if (!commit.events.isEmpty()) projectedId = commit.events.get(0).eventId;
            else {
                JSONArray ids = commit.result.optJSONArray("event_ids");
                if (ids != null) projectedId = ids.optString(0, "");
            }
        }
        return new ConversationStore(appContext).messageById(roomId, projectedId);
    }

    JSONObject postDecision(
            String fixedMessageId,
            String roomId,
            String npcId,
            String senderName,
            String decision,
            String action,
            long timeMs,
            String causationId,
            JSONArray brainTrace
    ) {
        String id = NpcId.of(npcId).value();
        String normalizedDecision = safe(decision).toLowerCase(java.util.Locale.US);
        boolean deferred = BrainCommunicationDecision.DEFER.equals(normalizedDecision);
        String logicalId = safe(fixedMessageId);
        String key = logicalId.isEmpty()
                ? "communication:" + UUID.randomUUID()
                : "communication:" + logicalId;
        JSONObject payload = new JSONObject();
        try {
            payload.put("message_id", logicalId);
            payload.put("room_id", safe(roomId));
            payload.put("sender_name", safe(senderName));
            payload.put("action", safe(action));
            payload.put("causation_id", safe(causationId));
            payload.put("observed_wall_time_ms", Math.max(0L, timeMs));
            payload.put("brain_trace", brainTrace == null ? new JSONArray() : new JSONArray(brainTrace.toString()));
            payload.put("participant_ids", participants(roomId));
        } catch (Exception ignored) {
        }
        WorldCommitResultV210 commit = kernel.commit(WorldCommandV210.of(
                deferred ? WorldCommandV210.COMMUNICATION_DEFERRED : WorldCommandV210.COMMUNICATION_SKIPPED,
                timeMs,
                id,
                key,
                payload));
        String projectedId = logicalId;
        if (projectedId.isEmpty()) {
            if (!commit.events.isEmpty()) projectedId = commit.events.get(0).eventId;
            else {
                JSONArray ids = commit.result.optJSONArray("event_ids");
                if (ids != null) projectedId = ids.optString(0, "");
            }
        }
        return new ConversationStore(appContext).messageById(roomId, projectedId);
    }

    private JSONArray participants(String roomId) {
        JSONArray result = new JSONArray();
        String room = safe(roomId);
        if (room.startsWith("direct_")) {
            String raw = room.substring("direct_".length());
            try { result.put(NpcId.of(raw).value()); } catch (Exception ignored) {}
            return result;
        }
        List<String> peer = NpcPeerRoomPolicy.participants(room);
        if (!peer.isEmpty()) {
            for (String npcId : peer) result.put(npcId);
            return result;
        }
        if (DemoRuntimeV032.ROOM_GROUP.equals(room)) {
            for (String npcId : registry.activeNpcIds()) result.put(npcId);
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
