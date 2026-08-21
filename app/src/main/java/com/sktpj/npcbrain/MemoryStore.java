package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class MemoryStore {
    private static final String PREFS = "npcbrain_memory";
    private static final String HISTORY = "history";
    private static final int MAX_ITEMS = 8;

    private final SharedPreferences preferences;

    MemoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String recentContext() {
        return preferences.getString(HISTORY, "[]");
    }

    synchronized void remember(String input, String output) {
        try {
            JSONArray oldHistory = new JSONArray(preferences.getString(HISTORY, "[]"));
            JSONArray newHistory = new JSONArray();
            int start = Math.max(0, oldHistory.length() - (MAX_ITEMS - 1));
            for (int i = start; i < oldHistory.length(); i++) {
                newHistory.put(oldHistory.get(i));
            }
            JSONObject item = new JSONObject();
            item.put("time_ms", System.currentTimeMillis());
            item.put("input", limit(input, 1800));
            item.put("output", limit(output, 2200));
            newHistory.put(item);
            preferences.edit().putString(HISTORY, newHistory.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized void clear() {
        preferences.edit().remove(HISTORY).apply();
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max);
    }
}
