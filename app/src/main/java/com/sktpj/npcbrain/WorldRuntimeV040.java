package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONObject;

final class WorldRuntimeV040 {
    private final WorldClock clock;
    private final WorldStateStore stateStore;

    WorldRuntimeV040(Context context) {
        clock = new WorldClock(context);
        stateStore = new WorldStateStore(context);
        long now = clock.now();
        stateStore.lifeState(NpcId.NPC1, now);
        stateStore.lifeState(NpcId.NPC2, now);
    }

    JSONObject attachUserMessageEvent(String roomId, JSONObject userMessage) {
        JSONObject message = copy(userMessage);
        String existingCauseId = message.optString("cause_event_id", "").trim();
        if (!existingCauseId.isEmpty() && stateStore.eventById(existingCauseId) != null) {
            return message;
        }
        String messageId = message.optString("id", "").trim();
        WorldEvent existingMessageEvent = stateStore.eventByMessageId(messageId);
        if (existingMessageEvent != null) {
            try {
                message.put("cause_event_id", existingMessageEvent.eventId());
            } catch (Exception ignored) {
            }
            return message;
        }

        long messageTime = message.optLong("time_ms", clock.now());
        long worldTime = clock.advanceTo(messageTime);
        stateStore.lifeState(NpcId.NPC1, worldTime);
        stateStore.lifeState(NpcId.NPC2, worldTime);

        Room room = room(roomId);
        JSONObject payload = new JSONObject();
        try {
            payload.put("message_id", message.optString("id", ""));
            payload.put("room", room.toJson());
            payload.put("sender_id", message.optString("sender_id", "user"));
            payload.put("text", message.optString("text", ""));
        } catch (Exception ignored) {
        }

        WorldEvent event = WorldEvent.create(
                "message_received",
                "user",
                "",
                worldTime,
                "",
                payload,
                ""
        );
        stateStore.appendEvent(event);
        try {
            message.put("cause_event_id", event.eventId());
        } catch (Exception ignored) {
        }
        return message;
    }

    LifeState lifeState(String npcId) {
        NpcId id = NpcId.of(npcId);
        return stateStore.lifeState(id, clock.now());
    }

    WorldEvent eventById(String eventId) {
        return stateStore.eventById(eventId);
    }

    Room room(String roomId) {
        if (DemoRuntimeV032.ROOM_NPC1.equals(roomId)) {
            return new Room(roomId, "direct_chat", "user", NpcId.NPC1.value());
        }
        if (DemoRuntimeV032.ROOM_NPC2.equals(roomId)) {
            return new Room(roomId, "direct_chat", "user", NpcId.NPC2.value());
        }
        if (DemoRuntimeV032.ROOM_GROUP.equals(roomId)) {
            return new Room(
                    roomId,
                    "group_chat",
                    "user",
                    NpcId.NPC1.value(),
                    NpcId.NPC2.value()
            );
        }
        return new Room(roomId, "unknown", "user");
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return json == null ? new JSONObject() : new JSONObject(json.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
