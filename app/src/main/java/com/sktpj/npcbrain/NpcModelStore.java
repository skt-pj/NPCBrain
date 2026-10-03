package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** NPC-scoped overrides layered on top of app-wide routing defaults. */
final class NpcModelStore {
    private static final String PREFS = "npcbrain_inference_model_v1";
    private static final String SELECTED_MODEL = "selected_model";
    private static final String GLOBAL_OVERRIDE = "global_model_override";
    private static final String SPECIALIST_OVERRIDE = "specialist_model_override";

    private final SharedPreferences preferences;
    private final RoutingSettingsStore defaults;

    NpcModelStore(Context context, String npcId) {
        Context storage = NpcContexts.storage(context, npcId);
        preferences = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defaults = new RoutingSettingsStore(context);
    }

    synchronized String globalOverride() {
        if (preferences.contains(GLOBAL_OVERRIDE)) {
            return NpcInferenceModel.normalize(preferences.getString(GLOBAL_OVERRIDE, ""));
        }
        return legacyOverride();
    }

    synchronized String specialistOverride() {
        if (preferences.contains(SPECIALIST_OVERRIDE)) {
            return NpcInferenceModel.normalize(preferences.getString(SPECIALIST_OVERRIDE, ""));
        }
        return legacyOverride();
    }

    synchronized boolean hasGlobalOverride() {
        return preferences.contains(GLOBAL_OVERRIDE) || hasLegacyOverride();
    }

    synchronized boolean hasSpecialistOverride() {
        return preferences.contains(SPECIALIST_OVERRIDE) || hasLegacyOverride();
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
                .remove(SELECTED_MODEL)
                .apply();
    }

    synchronized void clearGlobalOverride() {
        preferences.edit().remove(GLOBAL_OVERRIDE).remove(SELECTED_MODEL).apply();
    }

    synchronized void setSpecialistOverride(String model) {
        preferences.edit()
                .putString(SPECIALIST_OVERRIDE, NpcInferenceModel.normalize(model))
                .remove(SELECTED_MODEL)
                .apply();
    }

    synchronized void clearSpecialistOverride() {
        preferences.edit().remove(SPECIALIST_OVERRIDE).remove(SELECTED_MODEL).apply();
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
                .remove(SELECTED_MODEL)
                .apply();
    }

    private boolean hasLegacyOverride() {
        return preferences.contains(SELECTED_MODEL);
    }

    private String legacyOverride() {
        if (!hasLegacyOverride()) return "";
        return NpcInferenceModel.normalize(
                preferences.getString(SELECTED_MODEL, NpcInferenceModel.LOCAL_LIGHT));
    }
}
