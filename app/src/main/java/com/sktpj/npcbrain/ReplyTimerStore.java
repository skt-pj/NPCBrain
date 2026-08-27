package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class ReplyTimerStore {
    private static final String PREFS = "npcbrain_reply_timer_v1";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_NEXT_JOB_ID = "next_job_id";
    private static final int FIRST_JOB_ID = 426_500;

    private final Context appContext;
    private final SharedPreferences preferences;

    ReplyTimerStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized ReplyTimerTask schedule(
            ReplyTimerBinding binding,
            long wakeAtMs,
            String reason
    ) {
        if (binding == null || !binding.isValid()) return null;
        long now = Math.max(System.currentTimeMillis(), binding.decisionNowMs);
        if (!ReplyTimerPolicy.isValidWake(now, wakeAtMs)) return null;
        JSONObject tasks = loadTasks();
        String key = binding.sourceKey();
        ReplyTimerTask existing = ReplyTimerTask.fromJson(tasks.optJSONObject(key));
        int jobId = existing == null ? allocateJobId(tasks) : existing.jobId;
        long createdAt = existing == null ? now : existing.createdAtMs;
        ReplyTimerTask updated = ReplyTimerTask.fromBinding(
                binding, jobId, wakeAtMs, safeReason(reason), createdAt);
        if (!updated.isValid()) return null;
        try {
            tasks.put(key, updated.toJson());
            if (!preferences.edit().putString(KEY_TASKS, tasks.toString()).commit()) return null;
        } catch (Exception ignored) {
            return null;
        }
        if (!key.equals(ReplyTimerExecutionScope.currentSourceKey())) {
            if (!ReplyTimerScheduler.schedule(appContext, updated)) {
                tasks.remove(key);
                preferences.edit().putString(KEY_TASKS, tasks.toString()).commit();
                return null;
            }
        }
        return updated;
    }

    synchronized ReplyTimerTask get(String sourceKey) {
        return ReplyTimerTask.fromJson(loadTasks().optJSONObject(safe(sourceKey)));
    }

    synchronized void complete(String sourceKey) {
        String key = safe(sourceKey);
        if (key.isEmpty()) return;
        JSONObject tasks = loadTasks();
        ReplyTimerTask task = ReplyTimerTask.fromJson(tasks.optJSONObject(key));
        tasks.remove(key);
        preferences.edit().putString(KEY_TASKS, tasks.toString()).commit();
        if (task != null && !key.equals(ReplyTimerExecutionScope.currentSourceKey())) {
            ReplyTimerScheduler.cancel(appContext, task.jobId);
        }
    }

    synchronized int clearForNpc(String npcId) {
        String id = safe(npcId);
        if (id.isEmpty()) return 0;
        JSONObject tasks = loadTasks();
        List<ReplyTimerTask> removed = new ArrayList<>();
        Iterator<String> keys = tasks.keys();
        List<String> taskKeys = new ArrayList<>();
        while (keys.hasNext()) taskKeys.add(keys.next());
        for (String key : taskKeys) {
            ReplyTimerTask task = ReplyTimerTask.fromJson(tasks.optJSONObject(key));
            if (!sameNpc(task == null ? "" : task.npcId, id)) continue;
            removed.add(task);
            tasks.remove(key);
        }
        if (removed.isEmpty()) return 0;
        if (!preferences.edit().putString(KEY_TASKS, tasks.toString()).commit()) return 0;
        String activeSourceKey = ReplyTimerExecutionScope.currentSourceKey();
        for (ReplyTimerTask task : removed) {
            if (task == null || task.sourceKey.equals(activeSourceKey)) continue;
            ReplyTimerScheduler.cancel(appContext, task.jobId);
        }
        return removed.size();
    }

    synchronized void rearmAll() {
        JSONObject tasks = loadTasks();
        Iterator<String> keys = tasks.keys();
        while (keys.hasNext()) {
            ReplyTimerTask task = ReplyTimerTask.fromJson(tasks.optJSONObject(keys.next()));
            if (task != null) ReplyTimerScheduler.schedule(appContext, task);
        }
    }

    static boolean sameNpc(String taskNpcId, String targetNpcId) {
        String task = safe(taskNpcId);
        String target = safe(targetNpcId);
        return !task.isEmpty() && task.equals(target);
    }

    private int allocateJobId(JSONObject tasks) {
        Set<Integer> used = new HashSet<>();
        Iterator<String> keys = tasks.keys();
        while (keys.hasNext()) {
            ReplyTimerTask task = ReplyTimerTask.fromJson(tasks.optJSONObject(keys.next()));
            if (task != null && task.jobId > 0) used.add(task.jobId);
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

    private JSONObject loadTasks() {
        try {
            return new JSONObject(preferences.getString(KEY_TASKS, "{}"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeReason(String value) {
        String text = safe(value);
        return text.length() <= 240 ? text : text.substring(0, 240);
    }
}
