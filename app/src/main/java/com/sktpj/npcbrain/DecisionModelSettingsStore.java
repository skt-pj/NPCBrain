package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class DecisionModelSettingsStore {
    static final String ROUTE_LOCAL_CLEF = "local_clef";
    static final String ROUTE_CLOUD_JEV = "cloud_jev";

    private static final String PREFS = "npcbrain_decision_model_settings_v1";
    private static final String ROUTE = "route";

    private final SharedPreferences preferences;

    DecisionModelSettingsStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String route() {
        return normalizeRoute(preferences.getString(ROUTE, ROUTE_LOCAL_CLEF));
    }

    synchronized void setRoute(String route) {
        preferences.edit().putString(ROUTE, normalizeRoute(route)).apply();
    }

    synchronized boolean usesLocalClef() {
        return ROUTE_LOCAL_CLEF.equals(route());
    }

    synchronized boolean usesCloudJev() {
        return ROUTE_CLOUD_JEV.equals(route());
    }

    static String normalizeRoute(String value) {
        return ROUTE_CLOUD_JEV.equals(value) ? ROUTE_CLOUD_JEV : ROUTE_LOCAL_CLEF;
    }

    static String displayLabel(String route) {
        return ROUTE_CLOUD_JEV.equals(normalizeRoute(route))
                ? "クラウド / Jev"
                : "ローカル / CLEF-Flash";
    }
}
