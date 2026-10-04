package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** Runtime settings for local CLEF / llama.cpp execution. */
final class ClefRuntimeSettingsStore {
    private static final String PREFS = "npcbrain_clef_runtime_settings_v1";
    private static final String PREFER_GPU = "prefer_gpu";

    private final SharedPreferences preferences;

    ClefRuntimeSettingsStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean preferGpu() {
        return preferences.getBoolean(PREFER_GPU, true);
    }

    synchronized void setPreferGpu(boolean value) {
        preferences.edit().putBoolean(PREFER_GPU, value).apply();
    }

    synchronized String summary() {
        return preferGpu()
                ? "Vulkan GPU優先 · 失敗時CPU"
                : "CPU固定";
    }
}
