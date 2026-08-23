package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class DungeonMindStore {
    static final String STATE_BRAIN = "BRAIN";
    static final String STATE_THINKING = "THINKING";
    static final String STATE_LOCAL = "LOCAL FALLBACK";

    static final class Snapshot {
        final DungeonIntent intent;
        final DungeonPlan plan;
        final JSONArray trace;
        final JSONObject cognitiveGraph;
        final String brainState;
        final String error;
        final long updatedTimeMs;

        Snapshot(
                DungeonIntent intent,
                JSONArray trace,
                JSONObject cognitiveGraph,
                String brainState,
                String error,
                long updatedTimeMs
        ) {
            this(intent, null, trace, cognitiveGraph, brainState, error, updatedTimeMs);
        }

        Snapshot(
                DungeonIntent intent,
                DungeonPlan plan,
                JSONArray trace,
                JSONObject cognitiveGraph,
                String brainState,
                String error,
                long updatedTimeMs
        ) {
            this.intent = intent;
            this.plan = plan;
            this.trace = copyArray(trace);
            this.cognitiveGraph = copyObject(cognitiveGraph);
            this.brainState = safe(brainState, STATE_LOCAL);
            this.error = error == null ? "" : error.trim();
            this.updatedTimeMs = Math.max(0L, updatedTimeMs);
        }

        String summary() {
            if (plan != null && !plan.summary.isEmpty()) return plan.summary;
            return intent == null ? "" : intent.summary;
        }
    }

    private static final String PREFS = "npcbrain_dungeon_mind_v1";
    private final Context appContext;
    private final SharedPreferences preferences;

    DungeonMindStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void save(String npcId, Snapshot snapshot) {
        if (snapshot == null || isDead(npcId)) return;
        JSONObject root = new JSONObject();
        try {
            root.put("intent", snapshot.intent == null
                    ? new JSONObject() : snapshot.intent.toJson());
            if (snapshot.plan != null) root.put("plan", snapshot.plan.toJson());
            root.put("trace", snapshot.trace);
            root.put("cognitive_graph", snapshot.cognitiveGraph);
            root.put("brain_state", snapshot.brainState);
            root.put("error", snapshot.error);
            root.put("updated_time_ms", snapshot.updatedTimeMs);
            preferences.edit().putString(key(npcId), root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized Snapshot load(String npcId) {
        String raw = preferences.getString(key(npcId), "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(raw);
            return new Snapshot(
                    DungeonIntent.fromJson(root.optJSONObject("intent")),
                    DungeonPlan.fromJson(root.optJSONObject("plan")),
                    root.optJSONArray("trace"),
                    root.optJSONObject("cognitive_graph"),
                    root.optString("brain_state", STATE_LOCAL),
                    root.optString("error", ""),
                    root.optLong("updated_time_ms", 0L));
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized void clear(String npcId) {
        preferences.edit().remove(key(npcId)).commit();
    }

    static String key(String npcId) {
        String id = NpcId.of(npcId).value();
        if ("npc1".equals(id)) return "npc1_mind";
        if ("npc2".equals(id)) return "npc2_mind";
        return id + "_mind";
    }

    private boolean isDead(String npcId) {
        return new CharacterStateStore(NpcContexts.storage(appContext, npcId)).isDead();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static JSONArray copyArray(JSONArray source) {
        try {
            return source == null ? new JSONArray() : new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject copyObject(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
