package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class SpecialistInferenceSettingsStore {
    static final String MODE_LLM = "llm";
    static final String MODE_DECISION_MODEL = "decision_model";

    private static final String PREFS = "npcbrain_specialist_inference_v1";
    private static final String MODE = "mode";

    private final SharedPreferences preferences;

    SpecialistInferenceSettingsStore(Context context) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateLegacyClefIfNeeded(app);
    }

    synchronized String mode() {
        String value = preferences.getString(MODE, MODE_LLM);
        return MODE_DECISION_MODEL.equals(value) ? MODE_DECISION_MODEL : MODE_LLM;
    }

    synchronized boolean usesDecisionModel() {
        return MODE_DECISION_MODEL.equals(mode());
    }

    synchronized void setMode(String value) {
        preferences.edit()
                .putString(MODE, MODE_DECISION_MODEL.equals(value)
                        ? MODE_DECISION_MODEL
                        : MODE_LLM)
                .apply();
    }

    private void migrateLegacyClefIfNeeded(Context context) {
        if (preferences.contains(MODE)) return;
        ClefSettingsStore legacy = new ClefSettingsStore(context);
        preferences.edit()
                .putString(MODE, legacy.enabled() ? MODE_DECISION_MODEL : MODE_LLM)
                .apply();
    }
}
