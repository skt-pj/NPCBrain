package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
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

    LifeState updateScheduleEntry(
            String npcId,
            ScheduleSlot replacement,
            long effectiveTimeMs,
            String reason
    ) {
        NpcId id = NpcId.of(npcId);
        long worldTime = clock.advanceTo(effectiveTimeMs);
        LifeState current = stateStore.lifeState(id, worldTime);
        DailySchedule schedule = scheduleFor(id, current);
        DailySchedule updatedSchedule = schedule.replaceSlot(replacement);
        LifeState withUpdatedSchedule = current.withSchedule(worldTime, updatedSchedule.toJson());
        stateStore.saveLifeState(withUpdatedSchedule);
        return syncLifeState(
                id,
                worldTime,
                withUpdatedSchedule,
                updatedSchedule,
                true,
                reason == null ? "schedule_changed" : reason.trim()
        );
    }

    String messageCauseForNpc(String npcId, String triggerEventId) {
        NpcId id = NpcId.of(npcId);
        LifeState state = syncLifeState(id, clock.now());
        String lifeCauseId = state.currentActivityEventId();
        String primaryCauseId = LifeTransitionPolicy.primaryConversationCause(
                triggerEventId,
                lifeCauseId
        );

        JSONObject payload = new JSONObject();
        JSONArray relatedEventIds = new JSONArray();
        try {
            String trigger = triggerEventId == null ? "" : triggerEventId.trim();
            payload.put("trigger_event_id", trigger);
            payload.put("current_activity_event_id", lifeCauseId);
            payload.put("current_schedule_entry_id", state.currentScheduleEntryId());
            payload.put("current_activity", state.currentActivity());
            payload.put("location", state.location());
            if (!lifeCauseId.isEmpty()) relatedEventIds.put(lifeCauseId);
            payload.put("related_event_ids", relatedEventIds);
        } catch (Exception ignored) {
        }
        WorldEvent contextEvent = WorldEvent.create(
                "conversation_context",
                id.value(),
                "",
                clock.now(),
                state.location(),
                payload,
                primaryCauseId
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
        LifeState current = stateStore.lifeState(npcId, worldTime);
        DailySchedule schedule = scheduleFor(npcId, current);
        return syncLifeState(npcId, worldTime, current, schedule, false, "scheduled_transition");
    }

    private LifeState syncLifeState(
            NpcId npcId,
            long worldTime,
            LifeState current,
            DailySchedule schedule,
            boolean interrupted,
            String transitionReason
    ) {
        ScheduleSlot slot = schedule.slotAt(worldTime);
        JSONObject scheduleJson = schedule.toJson();

        if (LifeTransitionPolicy.sameState(
                current.currentScheduleEntryId(),
                current.currentActivity(),
                current.location(),
                slot
        )) {
            LifeState refreshed = current.refreshCurrentSlot(worldTime, slot, scheduleJson);
            stateStore.saveLifeState(refreshed);
            return refreshed;
        }

        long scheduledStartAt = schedule.slotStartTimeMs(worldTime, slot);
        long transitionTime = LifeTransitionPolicy.transitionTime(
                interrupted,
                worldTime,
                scheduledStartAt
        );
        String causeEventId = current.currentActivityEventId();
        boolean hasPreviousActivity = !"idle".equals(current.currentActivity())
                && !current.currentActivity().trim().isEmpty();
        String endType = LifeTransitionPolicy.endEventType(hasPreviousActivity, interrupted);
        if (!endType.isEmpty()) {
            JSONObject endPayload = transitionPayload(current, slot, worldTime, transitionReason);
            WorldEvent ended = WorldEvent.create(
                    endType,
                    npcId.value(),
                    "",
                    transitionTime,
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
                locationPayload.put("observed_at", worldTime);
                locationPayload.put("transition_reason", transitionReason);
            } catch (Exception ignored) {
            }
            WorldEvent moved = WorldEvent.create(
                    "location_changed",
                    npcId.value(),
                    "",
                    transitionTime,
                    slot.location(),
                    locationPayload,
                    causeEventId
            );
            stateStore.appendEvent(moved);
            causeEventId = moved.eventId();
        }

        JSONObject startPayload = new JSONObject();
        try {
            startPayload.put("schedule_entry", slot.toJson());
            startPayload.put("scheduled_start_at", scheduledStartAt);
            startPayload.put("observed_at", worldTime);
            startPayload.put("transition_reason", transitionReason);
        } catch (Exception ignored) {
        }
        WorldEvent started = WorldEvent.create(
                "activity_started",
                npcId.value(),
                "",
                transitionTime,
                slot.location(),
                startPayload,
                causeEventId
        );
        stateStore.appendEvent(started);

        LifeState updated = current.transitionTo(
                worldTime,
                slot.location(),
                slot.activity(),
                transitionTime,
                slot.goal(),
                slot.context(),
                slot.entryId(),
                started.eventId(),
                scheduleJson
        );
        stateStore.saveLifeState(updated);
        return updated;
    }

    private DailySchedule scheduleFor(NpcId npcId, LifeState current) {
        DailySchedule stored = DailySchedule.fromJson(npcId, current.dailySchedule());
        return stored == null ? DailySchedule.defaultFor(npcId) : stored;
    }

    private static JSONObject transitionPayload(
            LifeState current,
            ScheduleSlot next,
            long observedAt,
            String transitionReason
    ) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("previous_activity", current.currentActivity());
            payload.put("previous_location", current.location());
            payload.put("previous_schedule_entry_id", current.currentScheduleEntryId());
            payload.put("previous_activity_started_at", current.activityStartedAtMs());
            payload.put("next_schedule_entry", next.toJson());
            payload.put("observed_at", observedAt);
            payload.put("transition_reason", transitionReason);
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
