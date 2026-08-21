package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class WorldStateStore {
    private static final String PREFS = "npcbrain_world_state_v040";
    private static final String EVENTS_KEY = "world_events";
    private static final int MAX_EVENTS = 240;

    private final SharedPreferences preferences;

    WorldStateStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized LifeState lifeState(NpcId npcId, long worldTimeMs) {
        String raw = preferences.getString(lifeKey(npcId), "");
        LifeState state;
        try {
            state = raw == null || raw.trim().isEmpty()
                    ? LifeState.initial(npcId, worldTimeMs)
                    : LifeState.fromJson(new JSONObject(raw), npcId, worldTimeMs);
        } catch (Exception ignored) {
            state = LifeState.initial(npcId, worldTimeMs);
        }
        if (state.worldTimeMs() < worldTimeMs) {
            state = state.atWorldTime(worldTimeMs);
        }
        saveLifeState(state);
        return state;
    }

    synchronized void saveLifeState(LifeState state) {
        if (state == null) return;
        preferences.edit()
                .putString(lifeKey(state.npcId()), state.toJson().toString())
                .apply();
    }

    synchronized void appendEvent(WorldEvent event) {
        if (event == null) return;
        JSONArray source = loadEvents();
        JSONArray updated = new JSONArray();
        int start = Math.max(0, source.length() - (MAX_EVENTS - 1));
        for (int i = start; i < source.length(); i++) {
            Object item = source.opt(i);
            if (item != null) updated.put(item);
        }
        updated.put(event.toJson());
        preferences.edit().putString(EVENTS_KEY, updated.toString()).apply();
    }

    synchronized WorldEvent eventById(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) return null;
        JSONArray events = loadEvents();
        for (int i = events.length() - 1; i >= 0; i--) {
            JSONObject item = events.optJSONObject(i);
            if (item == null) continue;
            if (eventId.equals(item.optString("event_id", ""))) {
                return WorldEvent.fromJson(item);
            }
        }
        return null;
    }

    synchronized WorldEvent eventByMessageId(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) return null;
        JSONArray events = loadEvents();
        for (int i = events.length() - 1; i >= 0; i--) {
            JSONObject item = events.optJSONObject(i);
            if (item == null || !"message_received".equals(item.optString("event_type", ""))) continue;
            JSONObject payload = item.optJSONObject("payload");
            if (payload == null) continue;
            if (messageId.equals(payload.optString("message_id", ""))) {
                return WorldEvent.fromJson(item);
            }
        }
        return null;
    }

    synchronized JSONArray events() {
        try {
            return new JSONArray(loadEvents().toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private JSONArray loadEvents() {
        try {
            return new JSONArray(preferences.getString(EVENTS_KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String lifeKey(NpcId npcId) {
        return "life_" + npcId.value().replaceAll("[^a-z0-9_-]", "_");
    }
}
