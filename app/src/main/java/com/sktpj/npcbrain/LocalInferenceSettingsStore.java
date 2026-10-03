package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared runtime details for every on-device LiteRT-LM model. */
final class LocalInferenceSettingsStore {
    private static final String PREFS = "npcbrain_local_inference_settings_v1";
    private static final String PREFER_GPU = "prefer_gpu";
    private static final String AUTO_COMPACT = "auto_compact";
    private static final String RETRY_INVALID_JSON = "retry_invalid_json";

    private final SharedPreferences preferences;

    LocalInferenceSettingsStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean preferGpu() {
        return preferences.getBoolean(PREFER_GPU, true);
    }

    synchronized void setPreferGpu(boolean value) {
        preferences.edit().putBoolean(PREFER_GPU, value).apply();
    }

    synchronized boolean autoCompact() {
        return preferences.getBoolean(AUTO_COMPACT, true);
    }

    synchronized void setAutoCompact(boolean value) {
        preferences.edit().putBoolean(AUTO_COMPACT, value).apply();
    }

    synchronized boolean retryInvalidJson() {
        return preferences.getBoolean(RETRY_INVALID_JSON, true);
    }

    synchronized void setRetryInvalidJson(boolean value) {
        preferences.edit().putBoolean(RETRY_INVALID_JSON, value).apply();
    }

    synchronized String summary() {
        StringBuilder value = new StringBuilder();
        value.append(preferGpu() ? "GPU優先" : "CPU固定");
        value.append(autoCompact() ? " · 自動圧縮" : " · 圧縮OFF");
        value.append(retryInvalidJson() ? " · JSON再試行" : " · 再試行OFF");
        return value.toString();
    }
}
