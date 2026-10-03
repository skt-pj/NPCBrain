package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class RoutingSettingsStore {
    private static final String PREFS = "npcbrain_routing_v1";
    private static final String GLOBAL_MODEL = "global_model";
    private static final String SPECIALIST_MODEL = "specialist_model";
    private static final String GLOBAL_LAST_LOCAL = "global_last_local";
    private static final String SPECIALIST_LAST_LOCAL = "specialist_last_local";

    private final SharedPreferences preferences;

    RoutingSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String globalModel() {
        return NpcInferenceModel.normalize(
                preferences.getString(GLOBAL_MODEL, NpcInferenceModel.LOCAL_LIGHT));
    }

    synchronized String specialistModel() {
        return NpcInferenceModel.normalize(
                preferences.getString(SPECIALIST_MODEL, NpcInferenceModel.LOCAL_LIGHT));
    }

    synchronized String globalLastLocalModel() {
        String model = NpcInferenceModel.normalize(
                preferences.getString(GLOBAL_LAST_LOCAL, NpcInferenceModel.LOCAL_LIGHT));
        return NpcInferenceModel.isLocal(model) ? model : NpcInferenceModel.LOCAL_LIGHT;
    }

    synchronized String specialistLastLocalModel() {
        String model = NpcInferenceModel.normalize(
                preferences.getString(SPECIALIST_LAST_LOCAL, NpcInferenceModel.LOCAL_LIGHT));
        return NpcInferenceModel.isLocal(model) ? model : NpcInferenceModel.LOCAL_LIGHT;
    }

    synchronized void setGlobalModel(String model) {
        String normalized = NpcInferenceModel.normalize(model);
        SharedPreferences.Editor editor = preferences.edit().putString(GLOBAL_MODEL, normalized);
        if (NpcInferenceModel.isLocal(normalized)) {
            editor.putString(GLOBAL_LAST_LOCAL, normalized);
        }
        editor.apply();
    }

    synchronized void setSpecialistModel(String model) {
        String normalized = NpcInferenceModel.normalize(model);
        SharedPreferences.Editor editor = preferences.edit().putString(SPECIALIST_MODEL, normalized);
        if (NpcInferenceModel.isLocal(normalized)) {
            editor.putString(SPECIALIST_LAST_LOCAL, normalized);
        }
        editor.apply();
    }

    synchronized void setGlobalCloud(boolean cloud) {
        setGlobalModel(cloud ? NpcInferenceModel.OPENAI_LUNA : globalLastLocalModel());
    }

    synchronized void setSpecialistCloud(boolean cloud) {
        setSpecialistModel(cloud ? NpcInferenceModel.OPENAI_LUNA : specialistLastLocalModel());
    }
}
