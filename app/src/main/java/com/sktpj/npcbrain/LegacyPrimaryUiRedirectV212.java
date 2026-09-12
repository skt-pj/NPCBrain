package com.sktpj.npcbrain;

import android.app.Activity;
import android.content.Intent;

import java.lang.reflect.Method;

/** Ensures restored v0/v2.1.1 primary Activities cannot strand an upgraded app in the old UI. */
final class LegacyPrimaryUiRedirectV212 {
    private static volatile String pendingTab = "";

    private LegacyPrimaryUiRedirectV212() {}

    static boolean redirectIfLegacyPrimary(Activity activity) {
        String tab = tabFor(activity);
        if (tab.isEmpty()) return false;
        pendingTab = tab;
        Intent intent = new Intent(activity, WorldShellActivityV212.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
        activity.finish();
        activity.overridePendingTransition(0, 0);
        return true;
    }

    static void applyPendingTab(WorldShellActivityV212 shell) {
        String requested = pendingTab;
        if (requested.isEmpty()) return;
        pendingTab = "";
        try {
            Class<?> tabType = Class.forName(
                    "com.sktpj.npcbrain.WorldShellActivityV212$Tab");
            Object target = null;
            Object[] constants = tabType.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    if (requested.equals(constant.toString())) {
                        target = constant;
                        break;
                    }
                }
            }
            if (target == null) return;
            Method select = WorldShellActivityV212.class.getDeclaredMethod(
                    "selectTab", tabType, boolean.class);
            select.setAccessible(true);
            select.invoke(shell, target, true);
        } catch (Exception ignored) {
        }
    }

    private static String tabFor(Activity activity) {
        if (activity instanceof DemoActivityV032) return "CONVERSATION";
        if (activity instanceof NpcStatusActivity) return "STATUS";
        if (activity instanceof DungeonActivity || activity instanceof IndividualDungeonActivity) {
            return "DUNGEON";
        }
        if (activity instanceof CodexActivity) return "CODEX";
        if (activity instanceof SettingsActivity && isRestoredLegacyTask(activity)) return "SETTINGS";
        return "";
    }

    /** Settings opened from the new shell carries REORDER_TO_FRONT; restored old tasks do not. */
    private static boolean isRestoredLegacyTask(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) return true;
        return (intent.getFlags() & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) == 0;
    }
}
