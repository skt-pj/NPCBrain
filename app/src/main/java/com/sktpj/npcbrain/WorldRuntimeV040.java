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
        syncLifeState(NpcId.NPC1, now);
        syncLifeState(NpcId.NPC2, now);
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
        LifeState npc1State = syncLifeState(NpcId.NPC1, worldTime);
        LifeState npc2State = syncLifeState(NpcId.NPC2, worldTime);

        Room room = room(roomId);
        JSONObject payload = new JSONObject();
        JSONObject lifeEventIds = new JSONObject();
        try {
            lifeEventIds.put(NpcId.NPC1.value(), npc1State.currentActivityEventId());
            lifeEventIds.put(NpcId.NPC2.value(), npc2State.currentActivityEventId());
            payload.put("message_id", message.optString("id", ""));
            payload.put("room", room.toJson());
            payload.put("sender_id", message.optString("sender_id", "user"));
            payload.put("text", message.optString("text", ""));
            payload.put("life_event_ids", lifeEventIds);
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
        return syncLifeState(id, clock.now());
    }

    String messageCauseForNpc(String npcId, String triggerEventId) {
        NpcId id = NpcId.of(npcId);
        LifeState state = syncLifeState(id, clock.now());
        String lifeCauseId = state.currentActivityEventId();
        if (lifeCauseId.isEmpty()) return triggerEventId == null ? "" : triggerEventId.trim();

        JSONObject payload = new JSONObject();
        try {
            payload.put("trigger_event_id", triggerEventId == null ? "" : triggerEventId.trim());
            payload.put("current_activity_event_id", lifeCauseId);
            payload.put("current_schedule_entry_id", state.currentScheduleEntryId());
            payload.put("current_activity", state.currentActivity());
            payload.put("location", state.location());
        } catch (Exception ignored) {
        }
        WorldEvent contextEvent = WorldEvent.create(
                "conversation_context",
                id.value(),
                "",
                clock.now(),
                state.location(),
                payload,
                lifeCauseId
        );
        stateStore.appendEvent(contextEvent);
        return contextEvent.eventId();
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

    private LifeState syncLifeState(NpcId npcId, long worldTime) {
        DailySchedule schedule = DailySchedule.defaultFor(npcId);
        ScheduleSlot slot = schedule.slotAt(worldTime);
        LifeState current = stateStore.lifeState(npcId, worldTime);
        JSONObject scheduleJson = schedule.toJson();

        boolean sameEntry = slot.entryId().equals(current.currentScheduleEntryId());
        boolean sameActivity = slot.activity().equals(current.currentActivity());
        boolean sameLocation = slot.location().equals(current.location());
        if (sameEntry && sameActivity && sameLocation) {
            LifeState refreshed = current.withSchedule(worldTime, scheduleJson);
            stateStore.saveLifeState(refreshed);
            return refreshed;
        }

        String causeEventId = current.currentActivityEventId();
        boolean hasPreviousActivity = !"idle".equals(current.currentActivity())
                && !current.currentActivity().trim().isEmpty();
        if (hasPreviousActivity) {
            JSONObject endPayload = transitionPayload(current, slot);
            String endType = current.currentScheduleEntryId().isEmpty()
                    ? "activity_interrupted"
                    : "activity_ended";
            WorldEvent ended = WorldEvent.create(
                    endType,
                    npcId.value(),
                    "",
                    worldTime,
                    current.location(),
                    endPayload,
                    causeEventId
            );
            stateStore.appendEvent(ended);
            causeEventId = ended.eventId();
        }

        if (!slot.location().equals(current.location())) {
            JSONObject locationPayload = new JSONObject();
            try {
                locationPayload.put("from", current.location());
                locationPayload.put("to", slot.location());
                locationPayload.put("schedule_entry_id", slot.entryId());
            } catch (Exception ignored) {
            }
            WorldEvent moved = WorldEvent.create(
                    "location_changed",
                    npcId.value(),
                    "",
                    worldTime,
                    slot.location(),
                    locationPayload,
                    causeEventId
            );
            stateStore.appendEvent(moved);
            causeEventId = moved.eventId();
        }

        long scheduledStartAt = schedule.slotStartTimeMs(worldTime, slot);
        JSONObject startPayload = new JSONObject();
        try {
            startPayload.put("schedule_entry", slot.toJson());
            startPayload.put("scheduled_start_at", scheduledStartAt);
            startPayload.put("observed_at", worldTime);
        } catch (Exception ignored) {
        }
        WorldEvent started = WorldEvent.create(
                "activity_started",
                npcId.value(),
                "",
                worldTime,
                slot.location(),
                startPayload,
                causeEventId
        );
        stateStore.appendEvent(started);

        LifeState updated = current.transitionTo(
                worldTime,
                slot.location(),
                slot.activity(),
                scheduledStartAt,
                slot.goal(),
                slot.context(),
                slot.entryId(),
                started.eventId(),
                scheduleJson
        );
        stateStore.saveLifeState(updated);
        return updated;
    }

    private static JSONObject transitionPayload(LifeState current, ScheduleSlot next) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("previous_activity", current.currentActivity());
            payload.put("previous_location", current.location());
            payload.put("previous_schedule_entry_id", current.currentScheduleEntryId());
            payload.put("previous_activity_started_at", current.activityStartedAtMs());
            payload.put("next_schedule_entry", next.toJson());
        } catch (Exception ignored) {
        }
        return payload;
    }

    private static JSONObject copy(JSONObject json) {
        try {
            return json == null ? new JSONObject() : new JSONObject(json.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
