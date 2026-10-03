package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class ClefSettingsStore {
    static final String MODEL_CLEF_FLASH = "clef-flash-local-q4_k_m";

    private static final String PREFS = "npcbrain_clef_local_settings_v2";
    private static final String ENABLED = "enabled";

    private final SharedPreferences preferences;

    ClefSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean enabled() {
        return preferences.getBoolean(ENABLED, false);
    }

    synchronized void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(ENABLED, enabled).apply();
    }

    synchronized String model() {
        return MODEL_CLEF_FLASH;
    }

    static String normalizeModel(String ignored) {
        return MODEL_CLEF_FLASH;
    }

    static String displayLabel() {
        return "CLEF-Flash 9B · Q4_K_M · ローカル";
    }
}
