package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class ClefSettingsStore {
    static final String MODEL_CLEF_FLASH = "clef-flash";
    static final String MODEL_CLEF = "clef";

    private static final String PREFS = "npcbrain_clef_settings_v1";
    private static final String ENABLED = "enabled";
    private static final String MODEL = "model";
    private static final String ACCOUNT_ID = "account_id";
    private static final List<String> MODELS = Arrays.asList(MODEL_CLEF_FLASH, MODEL_CLEF);

    private final SharedPreferences preferences;

    ClefSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean enabled() {
        return preferences.getBoolean(ENABLED, false);
    }

    synchronized void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(ENABLED, enabled).apply();
    }

    synchronized String model() {
        return normalizeModel(preferences.getString(MODEL, MODEL_CLEF_FLASH));
    }

    synchronized void setModel(String model) {
        preferences.edit().putString(MODEL, normalizeModel(model)).apply();
    }

    synchronized String accountId() {
        return normalizeAccountId(preferences.getString(ACCOUNT_ID, ""));
    }

    synchronized void setAccountId(String accountId) {
        String normalized = normalizeAccountId(accountId);
        if (!normalized.isEmpty() && !normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Cloudflare Account IDの形式が不正です");
        }
        preferences.edit().putString(ACCOUNT_ID, normalized).apply();
    }

    synchronized void clearAccountId() {
        preferences.edit().remove(ACCOUNT_ID).putBoolean(ENABLED, false).apply();
    }

    static String normalizeModel(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return MODELS.contains(normalized) ? normalized : MODEL_CLEF_FLASH;
    }

    static String normalizeAccountId(String value) {
        return value == null ? "" : value.trim();
    }

    static String[] supportedModels() {
        return MODELS.toArray(new String[0]);
    }

    static String displayLabel(String model) {
        return MODEL_CLEF.equals(normalizeModel(model)) ? "CLEF 27B" : "CLEF-Flash 9B";
    }
}
