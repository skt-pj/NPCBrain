package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class CharacterStateStore {
    private static final String PREFS = "npcbrain_character_v1";
    private static final String NAME = "name";
    private static final String SPEECH_STYLE = "speech_style";
    private static final String EXTRAVERSION = "extraversion";
    private static final String NEUROTICISM = "neuroticism";
    private static final String AGREEABLENESS = "agreeableness";
    private static final String CONSCIENTIOUSNESS = "conscientiousness";
    private static final String OPENNESS = "openness";
    private static final String VALENCE = "valence";
    private static final String AROUSAL = "arousal";
    private static final String STRESS = "stress";
    private static final String DEAD = "dead";

    private final Context storageContext;
    private final SharedPreferences preferences;

    CharacterStateStore(Context context) {
        storageContext = context;
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean isDead() {
        return preferences.getBoolean(DEAD, false);
    }

    synchronized String displayName() {
        if (isDead()) return "死亡";
        String value = preferences.getString(NAME, "NPC");
        return value == null || value.trim().isEmpty() ? "NPC" : value.trim();
    }

    synchronized String speechStyle() {
        if (isDead()) return "";
        String value = preferences.getString(SPEECH_STYLE, "");
        return value == null ? "" : value;
    }

    synchronized int traitPercent(String key) {
        if (isDead()) return 0;
        return preferences.getInt(key, 50);
    }

    synchronized JSONObject snapshotJson() {
        if (isDead()) throw new IllegalStateException("NPC is dead");
        JSONObject root = new JSONObject();
        JSONObject traits = new JSONObject();
        JSONObject dynamic = new JSONObject();
        try {
            root.put("name", displayName());
            root.put("speech_style", speechStyle());

            traits.put(EXTRAVERSION, traitPercent(EXTRAVERSION) / 100.0);
            traits.put(NEUROTICISM, traitPercent(NEUROTICISM) / 100.0);
            traits.put(AGREEABLENESS, traitPercent(AGREEABLENESS) / 100.0);
            traits.put(CONSCIENTIOUSNESS, traitPercent(CONSCIENTIOUSNESS) / 100.0);
            traits.put(OPENNESS, traitPercent(OPENNESS) / 100.0);
            root.put("traits", traits);

            dynamic.put("valence", clampSigned(preferences.getFloat(VALENCE, 0.0f)));
            dynamic.put("arousal", clamp01(preferences.getFloat(AROUSAL, 0.25f)));
            dynamic.put("stress", clamp01(preferences.getFloat(STRESS, 0.15f)));
            root.put("current_state", dynamic);

            root.put("human_baseline", HumanBaseline.toJson());
            root.put("dungeon_participation",
                    new DungeonParticipationStore(storageContext).load().toJson());
        } catch (Exception ignored) {
        }
        return root;
    }

    synchronized void saveProfile(
            String name,
            int extraversion,
            int neuroticism,
            int agreeableness,
            int conscientiousness,
            int openness,
            String speechStyle
    ) {
        if (isDead()) return;
        preferences.edit()
                .putString(NAME, safe(name, "NPC"))
                .putInt(EXTRAVERSION, clampPercent(extraversion))
                .putInt(NEUROTICISM, clampPercent(neuroticism))
                .putInt(AGREEABLENESS, clampPercent(agreeableness))
                .putInt(CONSCIENTIOUSNESS, clampPercent(conscientiousness))
                .putInt(OPENNESS, clampPercent(openness))
                .putString(SPEECH_STYLE, speechStyle == null ? "" : speechStyle.trim())
                .apply();
    }

    synchronized void updateDynamicState(JSONObject state) {
        if (state == null || isDead()) return;
        float valence = (float) clampSigned(state.optDouble("valence", preferences.getFloat(VALENCE, 0.0f)));
        float arousal = (float) clamp01(state.optDouble("arousal", preferences.getFloat(AROUSAL, 0.25f)));
        float stress = (float) clamp01(state.optDouble("stress", preferences.getFloat(STRESS, 0.15f)));
        preferences.edit()
                .putFloat(VALENCE, valence)
                .putFloat(AROUSAL, arousal)
                .putFloat(STRESS, stress)
                .apply();
    }

    synchronized String dynamicStateSummary() {
        if (isDead()) return "—";
        int valence = Math.round(preferences.getFloat(VALENCE, 0.0f) * 100f);
        int arousal = Math.round(preferences.getFloat(AROUSAL, 0.25f) * 100f);
        int stress = Math.round(preferences.getFloat(STRESS, 0.15f) * 100f);
        return "感情価 " + signed(valence) + " · 覚醒 " + arousal + " · ストレス " + stress;
    }

    synchronized void markDead() {
        preferences.edit().clear().putBoolean(DEAD, true).commit();
    }

    synchronized void reset() {
        if (isDead()) return;
        preferences.edit().clear().apply();
    }

    static String extraversionKey() {
        return EXTRAVERSION;
    }

    static String neuroticismKey() {
        return NEUROTICISM;
    }

    static String agreeablenessKey() {
        return AGREEABLENESS;
    }

    static String conscientiousnessKey() {
        return CONSCIENTIOUSNESS;
    }

    static String opennessKey() {
        return OPENNESS;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
