package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class SpontaneousMessageStore {
    private static final String PREFS = "npcbrain_spontaneous_v043";
    private static final String KEY_INITIALIZED = "initialized";
    private static final String KEY_STATUS = "status_by_event";
    private static final String KEY_NEXT_JOB_ID = "next_job_id";
    private static final int FIRST_JOB_ID = 426_000;

    private static final String HISTORICAL = "historical";
    private static final String DONE = "done";
    private static final String DEFERRED = "deferred";

    static final class DeferredStatus {
        final boolean deferred;
        final long nextEligibleTimeMs;
        final int jobId;

        DeferredStatus(boolean deferred, long nextEligibleTimeMs, int jobId) {
            this.deferred = deferred;
            this.nextEligibleTimeMs = nextEligibleTimeMs;
            this.jobId = jobId;
        }
    }

    private final Context appContext;
    private final SharedPreferences preferences;

    SpontaneousMessageStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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
                putState(status, eventId, HISTORICAL, 0L, "baseline", 0);
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
        String scopedEventId = DeferredSpontaneousEventScope.currentEventId();

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (!isTrigger(event)) continue;
            String eventId = event.optString("event_id", "").trim();
            if (eventId.isEmpty()) continue;
            if (!scopedEventId.isEmpty() && !scopedEventId.equals(eventId)) continue;

            JSONObject state = status.optJSONObject(eventId);
            if (state == null) {
                if (scopedEventId.isEmpty()) due.put(copy(event));
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
        String id = safeId(eventId);
        if (id.isEmpty()) return;
        JSONObject status = loadStatus();
        JSONObject current = status.optJSONObject(id);
        int jobId = current == null ? 0 : current.optInt("job_id", 0);
        if (jobId > 0) DeferredSpontaneousScheduler.cancel(appContext, jobId);
        putState(status, id, DONE, 0L, outcome, jobId);
        preferences.edit().putString(KEY_STATUS, status.toString()).apply();
    }

    synchronized void markDeferred(String eventId, long nextEligibleTimeMs) {
        String id = safeId(eventId);
        if (id.isEmpty()) return;
        JSONObject status = loadStatus();
        JSONObject current = status.optJSONObject(id);
        int jobId = current == null ? 0 : current.optInt("job_id", 0);
        if (jobId <= 0) jobId = allocateJobId(status);
        putState(status, id, DEFERRED, nextEligibleTimeMs, "defer", jobId);
        preferences.edit().putString(KEY_STATUS, status.toString()).commit();
        DeferredSpontaneousScheduler.schedule(
                appContext, id, jobId, nextEligibleTimeMs);
    }

    synchronized String state(String eventId) {
        JSONObject item = loadStatus().optJSONObject(safeId(eventId));
        return item == null ? "" : item.optString("state", "");
    }

    synchronized DeferredStatus deferredStatus(String eventId) {
        JSONObject item = loadStatus().optJSONObject(safeId(eventId));
        if (item == null || !DEFERRED.equals(item.optString("state", ""))) {
            return new DeferredStatus(false, 0L, 0);
        }
        return new DeferredStatus(
                true,
                item.optLong("next_eligible_time_ms", 0L),
                item.optInt("job_id", 0));
    }

    synchronized void rearmDeferredJobs() {
        JSONObject status = loadStatus();
        boolean changed = false;
        Iterator<String> keys = status.keys();
        while (keys.hasNext()) {
            String eventId = keys.next();
            JSONObject item = status.optJSONObject(eventId);
            if (item == null || !DEFERRED.equals(item.optString("state", ""))) continue;
            int jobId = item.optInt("job_id", 0);
            if (jobId <= 0) {
                jobId = allocateJobId(status);
                try {
                    item.put("job_id", jobId);
                    changed = true;
                } catch (Exception ignored) {
                }
            }
        }
        if (changed) preferences.edit().putString(KEY_STATUS, status.toString()).commit();

        keys = status.keys();
        while (keys.hasNext()) {
            String eventId = keys.next();
            JSONObject item = status.optJSONObject(eventId);
            if (item == null || !DEFERRED.equals(item.optString("state", ""))) continue;
            int jobId = item.optInt("job_id", 0);
            long dueMs = item.optLong("next_eligible_time_ms", 0L);
            if (jobId > 0 && dueMs > 0L) {
                DeferredSpontaneousScheduler.schedule(appContext, eventId, jobId, dueMs);
            }
        }
    }

    private int allocateJobId(JSONObject status) {
        Set<Integer> used = new HashSet<>();
        Iterator<String> keys = status.keys();
        while (keys.hasNext()) {
            JSONObject item = status.optJSONObject(keys.next());
            if (item == null) continue;
            int value = item.optInt("job_id", 0);
            if (value > 0) used.add(value);
        }
        int candidate = preferences.getInt(KEY_NEXT_JOB_ID, FIRST_JOB_ID);
        if (candidate < FIRST_JOB_ID) candidate = FIRST_JOB_ID;
        while (used.contains(candidate)) {
            candidate++;
            if (candidate <= 0) candidate = FIRST_JOB_ID;
        }
        int next = candidate == Integer.MAX_VALUE ? FIRST_JOB_ID : candidate + 1;
        preferences.edit().putInt(KEY_NEXT_JOB_ID, next).commit();
        return candidate;
    }

    private static void putState(
            JSONObject status,
            String eventId,
            String state,
            long nextEligibleTimeMs,
            String outcome,
            int jobId
    ) {
        try {
            JSONObject item = new JSONObject();
            item.put("state", state);
            item.put("next_eligible_time_ms", nextEligibleTimeMs);
            item.put("outcome", outcome == null ? "" : outcome.trim());
            if (jobId > 0) item.put("job_id", jobId);
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
