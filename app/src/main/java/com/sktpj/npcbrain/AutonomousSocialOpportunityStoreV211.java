package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared foreground/background checkpoint for autonomous NPC social opportunities. */
final class AutonomousSocialOpportunityStoreV211 {
    private static final String PREFS = "npcbrain_periodic_social_v1";
    private static final String LAST_WINDOW = "last_window";

    private final SharedPreferences preferences;

    AutonomousSocialOpportunityStoreV211(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean isDue(long nowMs) {
        return PeriodicSocialPolicy.window(nowMs)
                > preferences.getLong(LAST_WINDOW, -1L);
    }

    synchronized void markAttempted(long nowMs) {
        preferences.edit()
                .putLong(LAST_WINDOW, PeriodicSocialPolicy.window(nowMs))
                .commit();
    }
}
