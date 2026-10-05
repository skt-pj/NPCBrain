package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class ClefSettingsStore {
    static final String MODEL_CLEF_FLASH = "clef-flash-local-q4_k_m";
    static final String EXECUTION_GPU = "gpu";
    static final String EXECUTION_CPU = "cpu";

    private static final String PREFS = "npcbrain_clef_local_settings_v2";
    private static final String ENABLED = "enabled";
    private static final String EXECUTION_BACKEND = "execution_backend";

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

    synchronized String executionBackend() {
        return normalizeExecutionBackend(preferences.getString(EXECUTION_BACKEND, ""));
    }

    synchronized void setExecutionBackend(String backend) {
        String normalized = normalizeExecutionBackend(backend);
        if (normalized.isEmpty()) {
            preferences.edit().remove(EXECUTION_BACKEND).apply();
            return;
        }
        preferences.edit().putString(EXECUTION_BACKEND, normalized).apply();
    }

    static String normalizeModel(String ignored) {
        return MODEL_CLEF_FLASH;
    }

    static String normalizeExecutionBackend(String value) {
        if (EXECUTION_GPU.equals(value)) return EXECUTION_GPU;
        if (EXECUTION_CPU.equals(value)) return EXECUTION_CPU;
        return "";
    }

    static String executionBackendLabel(String backend) {
        String normalized = normalizeExecutionBackend(backend);
        if (EXECUTION_GPU.equals(normalized)) return "GPU";
        if (EXECUTION_CPU.equals(normalized)) return "CPU";
        return "未選択";
    }

    static String displayLabel() {
        return "CLEF-Flash 9B · Q4_K_M · ローカル";
    }
}
