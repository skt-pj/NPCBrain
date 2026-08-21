package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class ModelSettingsStore {
    private static final String PREFS = "npcbrain_ai_settings_v1";
    private static final String REASONING_EFFORT = "reasoning_effort";
    private static final String DEFAULT_EFFORT = "max";
    private static final List<String> SUPPORTED = Arrays.asList(
            "none", "low", "medium", "high", "xhigh", "max");

    private final SharedPreferences preferences;

    ModelSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String reasoningEffort() {
        return normalizeReasoningEffort(preferences.getString(REASONING_EFFORT, DEFAULT_EFFORT));
    }

    synchronized void setReasoningEffort(String effort) {
        preferences.edit()
                .putString(REASONING_EFFORT, normalizeReasoningEffort(effort))
                .apply();
    }

    static String normalizeReasoningEffort(String effort) {
        String normalized = effort == null
                ? DEFAULT_EFFORT
                : effort.trim().toLowerCase(Locale.US);
        return SUPPORTED.contains(normalized) ? normalized : DEFAULT_EFFORT;
    }

    static String[] supportedEfforts() {
        return SUPPORTED.toArray(new String[0]);
    }

    static String displayLabel(String effort) {
        return normalizeReasoningEffort(effort).toUpperCase(Locale.US);
    }

    static String description(String effort) {
        switch (normalizeReasoningEffort(effort)) {
            case "none":
                return "最小の遅延を優先";
            case "low":
                return "軽い推論";
            case "medium":
                return "標準的なバランス";
            case "high":
                return "深めの推論";
            case "xhigh":
                return "かなり深い推論";
            case "max":
            default:
                return "最大の推論量を優先";
        }
    }
}
