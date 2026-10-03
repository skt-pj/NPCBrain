package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** NPC-scoped explicit overrides layered on top of app-wide routing defaults. */
final class NpcModelStore {
    private static final String PREFS = "npcbrain_inference_model_v1";
    private static final String LEGACY_SELECTED_MODEL = "selected_model";
    private static final String GLOBAL_OVERRIDE = "global_model_override";
    private static final String SPECIALIST_OVERRIDE = "specialist_model_override";

    private final SharedPreferences preferences;
    private final RoutingSettingsStore defaults;

    NpcModelStore(Context context, String npcId) {
        Context storage = NpcContexts.storage(context, npcId);
        preferences = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defaults = new RoutingSettingsStore(context);
        if (preferences.contains(LEGACY_SELECTED_MODEL)) {
            preferences.edit().remove(LEGACY_SELECTED_MODEL).apply();
        }
    }

    synchronized String globalOverride() {
        return preferences.contains(GLOBAL_OVERRIDE)
                ? NpcInferenceModel.normalize(preferences.getString(GLOBAL_OVERRIDE, ""))
                : "";
    }

    synchronized String specialistOverride() {
        return preferences.contains(SPECIALIST_OVERRIDE)
                ? NpcInferenceModel.normalize(preferences.getString(SPECIALIST_OVERRIDE, ""))
                : "";
    }

    synchronized boolean hasGlobalOverride() {
        return preferences.contains(GLOBAL_OVERRIDE);
    }

    synchronized boolean hasSpecialistOverride() {
        return preferences.contains(SPECIALIST_OVERRIDE);
    }

    synchronized String effectiveGlobalModel() {
        return hasGlobalOverride() ? globalOverride() : defaults.globalModel();
    }

    synchronized String effectiveSpecialistModel() {
        return hasSpecialistOverride() ? specialistOverride() : defaults.specialistModel();
    }

    synchronized String effectiveModelForStage(String stageId) {
        return "global_workspace".equals(stageId)
                ? effectiveGlobalModel()
                : effectiveSpecialistModel();
    }

    synchronized void setGlobalOverride(String model) {
        preferences.edit()
                .putString(GLOBAL_OVERRIDE, NpcInferenceModel.normalize(model))
                .remove(LEGACY_SELECTED_MODEL)
                .apply();
    }

    synchronized void clearGlobalOverride() {
        preferences.edit().remove(GLOBAL_OVERRIDE).remove(LEGACY_SELECTED_MODEL).apply();
    }

    synchronized void setSpecialistOverride(String model) {
        preferences.edit()
                .putString(SPECIALIST_OVERRIDE, NpcInferenceModel.normalize(model))
                .remove(LEGACY_SELECTED_MODEL)
                .apply();
    }

    synchronized void clearSpecialistOverride() {
        preferences.edit().remove(SPECIALIST_OVERRIDE).remove(LEGACY_SELECTED_MODEL).apply();
    }

    /** Compatibility for older callers: specialist route is the general non-global route. */
    synchronized String selectedModel() {
        return effectiveSpecialistModel();
    }

    synchronized void setSelectedModel(String model) {
        String normalized = NpcInferenceModel.normalize(model);
        preferences.edit()
                .putString(GLOBAL_OVERRIDE, normalized)
                .putString(SPECIALIST_OVERRIDE, normalized)
                .remove(LEGACY_SELECTED_MODEL)
                .apply();
    }
}
