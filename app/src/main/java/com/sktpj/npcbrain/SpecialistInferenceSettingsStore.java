package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** Selects one inference family for all nine specialist brains. */
final class SpecialistInferenceSettingsStore {
    static final String MODE_NORMAL_LLM = "normal_llm";
    static final String MODE_DECISION_MODEL = "decision_model";

    private static final String PREFS = "npcbrain_specialist_inference_v1";
    private static final String MODE = "mode";

    private final SharedPreferences preferences;

    SpecialistInferenceSettingsStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String mode() {
        String value = preferences.getString(MODE, MODE_NORMAL_LLM);
        return MODE_DECISION_MODEL.equals(value) ? MODE_DECISION_MODEL : MODE_NORMAL_LLM;
    }

    synchronized boolean usesDecisionModel() {
        return MODE_DECISION_MODEL.equals(mode());
    }

    synchronized void setMode(String mode) {
        preferences.edit()
                .putString(MODE, MODE_DECISION_MODEL.equals(mode)
                        ? MODE_DECISION_MODEL
                        : MODE_NORMAL_LLM)
                .apply();
    }
}
