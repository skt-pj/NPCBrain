package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class SpontaneousMessageStore {
    private static final String PREFS = "npcbrain_spontaneous_v043";
    private static final String KEY_INITIALIZED = "initialized";
    private static final String KEY_STATUS = "status_by_event";

    private static final String HISTORICAL = "historical";
    private static final String DONE = "done";
    private static final String DEFERRED = "deferred";

    private final SharedPreferences preferences;

    SpontaneousMessageStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void initializeBaseline(JSONArray events) {
        if (preferences.getBoolean(KEY_INITIALIZED, false)) return;
        JSONObject status = loadStatus();
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (!isTrigger(event)) continue;
                String eventId = event.optString("event_id", "").trim();
                if (eventId.isEmpty()) continue;
                putState(status, eventId, HISTORICAL, 0L, "baseline");
            }
        }
        preferences.edit()
                .putString(KEY_STATUS, status.toString())
                .putBoolean(KEY_INITIALIZED, true)
                .apply();
    }

    synchronized JSONArray dueEvents(JSONArray events, long nowMs) {
        JSONArray due = new JSONArray();
        JSONObject status = loadStatus();
        if (events == null) return due;

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (!isTrigger(event)) continue;
            String eventId = event.optString("event_id", "").trim();
            if (eventId.isEmpty()) continue;

            JSONObject state = status.optJSONObject(eventId);
            if (state == null) {
                due.put(copy(event));
                continue;
            }
            if (!DEFERRED.equals(state.optString("state", ""))) continue;
            long nextEligible = state.optLong("next_eligible_time_ms", 0L);
            if (SpontaneousMessagePolicy.isDeferredDue(nextEligible, nowMs)) {
                due.put(copy(event));
            }
        }
        return due;
    }

    synchronized void markDone(String eventId, String outcome) {
        updateState(eventId, DONE, 0L, outcome);
    }

    synchronized void markDeferred(String eventId, long nextEligibleTimeMs) {
        updateState(eventId, DEFERRED, nextEligibleTimeMs, "defer");
    }

    synchronized String state(String eventId) {
        JSONObject item = loadStatus().optJSONObject(safeId(eventId));
        return item == null ? "" : item.optString("state", "");
    }

    private void updateState(String eventId, String state, long nextEligibleTimeMs, String outcome) {
        String id = safeId(eventId);
        if (id.isEmpty()) return;
        JSONObject status = loadStatus();
        putState(status, id, state, nextEligibleTimeMs, outcome);
        preferences.edit().putString(KEY_STATUS, status.toString()).apply();
    }

    private static void putState(
            JSONObject status,
            String eventId,
            String state,
            long nextEligibleTimeMs,
            String outcome
    ) {
        try {
            JSONObject item = new JSONObject();
            item.put("state", state);
            item.put("next_eligible_time_ms", nextEligibleTimeMs);
            item.put("outcome", outcome == null ? "" : outcome.trim());
            status.put(eventId, item);
        } catch (Exception ignored) {
        }
    }

    private JSONObject loadStatus() {
        try {
            return new JSONObject(preferences.getString(KEY_STATUS, "{}"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static boolean isTrigger(JSONObject event) {
        return event != null && SpontaneousMessagePolicy.isTriggerEvent(
                event.optString("event_type", ""),
                event.optString("actor_id", "")
        );
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String safeId(String value) {
        return value == null ? "" : value.trim();
    }
}
