package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class RoutingSettingsStore {
    private static final String PREFS = "npcbrain_routing_v1";
    private static final String GLOBAL_MODEL = "global_model";
    private static final String SPECIALIST_MODEL = "specialist_model";

    private final SharedPreferences preferences;

    RoutingSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String globalModel() {
        return NpcInferenceModel.normalize(preferences.getString(GLOBAL_MODEL, NpcInferenceModel.LOCAL_LIGHT));
    }

    synchronized String specialistModel() {
        return NpcInferenceModel.normalize(preferences.getString(SPECIALIST_MODEL, NpcInferenceModel.LOCAL_LIGHT));
    }

    synchronized void setGlobalModel(String model) {
        preferences.edit().putString(GLOBAL_MODEL, NpcInferenceModel.normalize(model)).apply();
    }

    synchronized void setSpecialistModel(String model) {
        preferences.edit().putString(SPECIALIST_MODEL, NpcInferenceModel.normalize(model)).apply();
    }
}
