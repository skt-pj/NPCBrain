package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NpcInnerLifeStore {
    private static final String PREFS = "npcbrain_inner_life_v1";
    private static final String STATE = "state";
    private static final String STREAM = "stream";
    private static final int MAX_STREAM = 120;

    private final SharedPreferences preferences;

    NpcInnerLifeStore(Context storageContext) {
        preferences = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized NpcInnerLifeState loadExisting() {
        String raw = preferences.getString(STATE, "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(raw);
            long initialized = json.optLong("initialized_at_ms", 0L);
            return NpcInnerLifeState.fromJson(json, initialized, 0.5, 0.5, 0.5);
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized NpcInnerLifeState loadOrCreate(
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness
    ) {
        NpcInnerLifeState existing = loadExisting();
        if (existing != null) return existing;
        NpcInnerLifeState state = NpcInnerLifeState.initial(
                nowMs, extraversion, neuroticism, openness);
        save(state);
        return state;
    }

    synchronized void save(NpcInnerLifeState state) {
        if (state == null) return;
        preferences.edit().putString(STATE, state.toJson().toString()).commit();
    }

    synchronized void appendThought(NpcThoughtEntry entry) {
        if (entry == null || entry.text.isEmpty()) return;
        JSONArray source = loadStream();
        JSONArray updated = new JSONArray();
        int start = Math.max(0, source.length() - (MAX_STREAM - 1));
        for (int i = start; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null) updated.put(item);
        }
        updated.put(entry.toJson());
        preferences.edit().putString(STREAM, updated.toString()).commit();
    }

    synchronized List<NpcThoughtEntry> latestThoughts(int limit) {
        JSONArray source = loadStream();
        int max = Math.max(0, Math.min(MAX_STREAM, limit));
        List<NpcThoughtEntry> result = new ArrayList<>();
        for (int i = source.length() - 1; i >= 0 && result.size() < max; i--) {
            NpcThoughtEntry entry = NpcThoughtEntry.fromJson(source.optJSONObject(i));
            if (entry != null) result.add(entry);
        }
        return result;
    }

    synchronized int thoughtCount() {
        return loadStream().length();
    }

    synchronized JSONObject snapshotForBrain(
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness
    ) {
        return loadOrCreate(nowMs, extraversion, neuroticism, openness).snapshotForBrain();
    }

    synchronized String compactSummary(
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness
    ) {
        NpcInnerLifeState state = loadOrCreate(nowMs, extraversion, neuroticism, openness);
        return compactSummary(state);
    }

    static String compactSummary(NpcInnerLifeState state) {
        if (state == null) return "まだ内面状態は記録されていません。";
        return state.mood + " · " + state.focus + "\n"
                + "次: " + state.intention + "\n"
                + String.format(
                        Locale.JAPAN,
                        "E %d%%  H %d%%  S %d%%  B %d%%  C %d%%  SAFE %d%%",
                        pct(state.energy),
                        pct(state.hunger),
                        pct(state.socialNeed),
                        pct(state.boredom),
                        pct(state.curiosity),
                        pct(state.safetyConcern)
                );
    }

    static int maxStreamEntries() {
        return MAX_STREAM;
    }

    private JSONArray loadStream() {
        String raw = preferences.getString(STREAM, "[]");
        try {
            return raw == null ? new JSONArray() : new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static int pct(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0);
    }
}
