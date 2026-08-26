package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class NpcProfileSynthesisStore {
    private static final String PREFS = "npcbrain_profile_synthesis_v1";
    private static final String PROFILE_SYNTHESIS = "profile_synthesis";

    private final SharedPreferences preferences;

    NpcProfileSynthesisStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean save(JSONObject synthesis) {
        if (synthesis == null || synthesis.length() == 0) return false;
        try {
            JSONObject copy = new JSONObject(synthesis.toString());
            String summary = copy.optString("summary", "").trim();
            if (summary.isEmpty()) return false;
            return preferences.edit().putString(PROFILE_SYNTHESIS, copy.toString()).commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    synchronized JSONObject load() {
        String raw = preferences.getString(PROFILE_SYNTHESIS, "");
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        try {
            JSONObject result = new JSONObject(raw);
            return result.optString("summary", "").trim().isEmpty()
                    ? new JSONObject()
                    : result;
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    synchronized void clear() {
        preferences.edit().remove(PROFILE_SYNTHESIS).commit();
    }
}
