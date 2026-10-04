package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

/** Remembers a fatal/unresponsive Vulkan attempt for the current installed build. */
final class ClefGpuHealthStore {
    private static final String PREFS = "npcbrain_clef_gpu_health_v1";
    private static final String QUARANTINED_VERSION = "quarantined_version_code";

    private final Context appContext;
    private final SharedPreferences preferences;

    ClefGpuHealthStore(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean isQuarantined() {
        long versionCode = versionCode(appContext);
        return versionCode >= 0L
                && preferences.getLong(QUARANTINED_VERSION, Long.MIN_VALUE) == versionCode;
    }

    synchronized void quarantine() {
        long versionCode = versionCode(appContext);
        if (versionCode < 0L) return;
        preferences.edit().putLong(QUARANTINED_VERSION, versionCode).apply();
    }

    static long versionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Exception ignored) {
            return -1L;
        }
    }
}
