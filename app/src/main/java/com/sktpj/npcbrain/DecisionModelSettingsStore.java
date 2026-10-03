package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class DecisionModelSettingsStore {
    private static final String PREFS = "npcbrain_decision_model_settings_v1";
    private static final String MODEL = "model";
    private static final String LAST_LOCAL = "last_local";
    private static final String LAST_CLOUD = "last_cloud";
    private static final String CLOUDFLARE_ACCOUNT_ID = "cloudflare_account_id";

    private final SharedPreferences preferences;

    DecisionModelSettingsStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String model() {
        return DecisionModelCatalog.normalize(
                preferences.getString(MODEL, DecisionModelCatalog.LOCAL_CLEF_FLASH_Q4_K_M));
    }

    synchronized void setModel(String value) {
        String normalized = DecisionModelCatalog.normalize(value);
        SharedPreferences.Editor editor = preferences.edit().putString(MODEL, normalized);
        if (DecisionModelCatalog.isLocal(normalized)) {
            editor.putString(LAST_LOCAL, normalized);
        } else {
            editor.putString(LAST_CLOUD, normalized);
        }
        editor.apply();
    }

    synchronized void setCloud(boolean cloud) {
        if (cloud) {
            String last = DecisionModelCatalog.normalize(preferences.getString(
                    LAST_CLOUD, DecisionModelCatalog.CLOUD_CLEF_FLASH));
            if (!DecisionModelCatalog.isCloud(last)) last = DecisionModelCatalog.CLOUD_CLEF_FLASH;
            setModel(last);
        } else {
            String last = DecisionModelCatalog.normalize(preferences.getString(
                    LAST_LOCAL, DecisionModelCatalog.LOCAL_CLEF_FLASH_Q4_K_M));
            if (!DecisionModelCatalog.isLocal(last)) {
                last = DecisionModelCatalog.LOCAL_CLEF_FLASH_Q4_K_M;
            }
            setModel(last);
        }
    }

    synchronized String cloudflareAccountId() {
        String value = preferences.getString(CLOUDFLARE_ACCOUNT_ID, "");
        return value == null ? "" : value.trim();
    }

    synchronized void setCloudflareAccountId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.isEmpty() && !normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Cloudflare Account IDの形式が不正です");
        }
        preferences.edit().putString(CLOUDFLARE_ACCOUNT_ID, normalized).apply();
    }

    synchronized void clearCloudflareAccountId() {
        preferences.edit().remove(CLOUDFLARE_ACCOUNT_ID).apply();
    }
}
