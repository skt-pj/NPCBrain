package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridges legacy WorldRuntimeV040 events into the same MemoryStore sink used by v2 world events.
 * The event itself is not re-appended; only previously unseen relevant events are remembered.
 */
final class WorldEventMemoryIngestor {
    private static final String PREFS = "npcbrain_world_event_memory_ingestor_v201";
    private static final String KEY_PROCESSED = "processed_event_ids";
    private static final int MAX_TRACKED_IDS = 240;

    private final Context appContext;
    private final SharedPreferences preferences;
    private final WorldStateStore worldStore;
    private final NpcRegistryStore registry;
    private final WorldEventMemoryBridge memoryBridge;

    WorldEventMemoryIngestor(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        worldStore = new WorldStateStore(appContext);
        registry = new NpcRegistryStore(appContext);
        memoryBridge = new WorldEventMemoryBridge(appContext);
    }

    synchronized int ingestPending() {
        JSONArray events = worldStore.events();
        Set<String> processed = readProcessed();
        Set<String> nextProcessed = new LinkedHashSet<>();
        int ingested = 0;

        for (int i = 0; i < events.length(); i++) {
            WorldEvent event = WorldEvent.fromJson(events.optJSONObject(i));
            if (event == null) continue;
            String eventId = event.eventId();
            if (eventId.isEmpty()) continue;
            nextProcessed.add(eventId);
            if (processed.contains(eventId) || !shouldIngest(event.eventType())) continue;
            List<String> participants = participantsFor(event, registry.activeNpcIds());
            if (participants.isEmpty()) continue;
            memoryBridge.rememberExisting(event, participants, importanceFor(event.eventType()));
            ingested++;
        }
        persistProcessed(tail(nextProcessed, MAX_TRACKED_IDS));
        return ingested;
    }

    static boolean shouldIngest(String eventType) {
        String type = eventType == null ? "" : eventType.trim();
        return "activity_started".equals(type)
                || "activity_ended".equals(type)
                || "activity_interrupted".equals(type)
                || "location_changed".equals(type)
                || "message_received".equals(type);
    }

    static List<String> participantsFor(WorldEvent event, List<String> activeNpcIds) {
        Set<String> active = new LinkedHashSet<>();
        if (activeNpcIds != null) {
            for (String raw : activeNpcIds) {
                try {
                    active.add(NpcId.of(raw).value());
                } catch (Exception ignored) {
                }
            }
        }
        LinkedHashSet<String> participants = new LinkedHashSet<>();
        addIfActive(participants, active, event == null ? "" : event.actorId());
        addIfActive(participants, active, event == null ? "" : event.targetId());
        if (event != null && "message_received".equals(event.eventType())) {
            JSONObject room = event.payload().optJSONObject("room");
            JSONArray ids = room == null ? null : room.optJSONArray("participant_ids");
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    addIfActive(participants, active, ids.optString(i, ""));
                }
            }
        }
        return new ArrayList<>(participants);
    }

    static double importanceFor(String eventType) {
        String type = eventType == null ? "" : eventType.trim();
        if ("message_received".equals(type)) return 0.75;
        if ("activity_interrupted".equals(type)) return 0.65;
        if ("activity_started".equals(type)) return 0.55;
        if ("location_changed".equals(type)) return 0.45;
        return 0.35;
    }

    private Set<String> readProcessed() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_PROCESSED, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "").trim();
                if (!id.isEmpty()) result.add(id);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void persistProcessed(Set<String> ids) {
        JSONArray array = new JSONArray();
        if (ids != null) for (String id : ids) array.put(id);
        preferences.edit().putString(KEY_PROCESSED, array.toString()).apply();
    }

    private static Set<String> tail(Set<String> source, int max) {
        List<String> values = new ArrayList<>(source);
        int start = Math.max(0, values.size() - Math.max(1, max));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int i = start; i < values.size(); i++) result.add(values.get(i));
        return result;
    }

    private static void addIfActive(Set<String> output, Set<String> active, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            String id = NpcId.of(raw).value();
            if (active.contains(id)) output.add(id);
        } catch (Exception ignored) {
        }
    }
}
