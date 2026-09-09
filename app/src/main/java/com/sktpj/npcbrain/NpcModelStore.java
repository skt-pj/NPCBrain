package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** NPC-scoped persisted inference model selection. */
final class NpcModelStore {
    private static final String PREFS = "npcbrain_inference_model_v1";
    private static final String SELECTED_MODEL = "selected_model";

    private final SharedPreferences preferences;

    NpcModelStore(Context context, String npcId) {
        Context storage = NpcContexts.storage(context, npcId);
        preferences = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String selectedModel() {
        return NpcInferenceModel.normalize(
                preferences.getString(SELECTED_MODEL, NpcInferenceModel.LOCAL_LIGHT));
    }

    synchronized void setSelectedModel(String model) {
        preferences.edit()
                .putString(SELECTED_MODEL, NpcInferenceModel.normalize(model))
                .apply();
    }
}
