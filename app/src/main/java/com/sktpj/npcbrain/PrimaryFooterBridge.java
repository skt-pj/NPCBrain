package com.sktpj.npcbrain;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Extends the legacy primary footer without rewriting PrimaryUiCoordinator.
 * v0.4.45 owns Settings navigation and keeps the footer visible on the 8-way dungeon screen.
 */
final class PrimaryFooterBridge {
    private static final String NAV_TAG = "npcbrain_primary_nav_v0420";
    private static final String SPACER_TAG = "npcbrain_primary_nav_spacer_v0420";

    private PrimaryFooterBridge() {}

    static void install(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;

        View existing = content.findViewWithTag(NAV_TAG);
        if (existing == null && ownsFooter(activity)) {
            View rootView = content.getChildAt(0);
            if (!(rootView instanceof LinearLayout)) return;
            LinearLayout root = (LinearLayout) rootView;
            ensureSpacer(activity, root);
            existing = createFooter(activity, content);
        }
        if (existing != null) bindNavigation(activity, existing);
    }

    private static boolean ownsFooter(Activity activity) {
        return activity instanceof SettingsActivity
                || activity instanceof IndividualDungeonActivity;
    }

    private static View createFooter(Activity activity, FrameLayout content) {
        LinearLayout shell = new LinearLayout(activity);
        shell.setTag(NAV_TAG);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(AppUiTheme.NAV_BACKGROUND);
        shell.setElevation(dp(activity, 8));

        View divider = new View(activity);
        divider.setBackgroundColor(AppUiTheme.NAV_DIVIDER);
        shell.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 1)));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        shell.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, AppUiTheme.NAV_HEIGHT_DP)));

        String selected = destinationFor(activity);
        for (String id : PrimaryNavigationPolicy.destinationIds()) {
            Button button = navigationButton(activity, id, id.equals(selected));
            row.addView(button, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        content.addView(shell, params);
        return shell;
    }

    private static void ensureSpacer(Activity activity, LinearLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            if (SPACER_TAG.equals(root.getChildAt(i).getTag())) return;
        }
        View spacer = new View(activity);
        spacer.setTag(SPACER_TAG);
        root.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, AppUiTheme.NAV_HEIGHT_DP + 1)));
    }

    private static Button navigationButton(Activity activity, String id, boolean selected) {
        Button button = new Button(activity);
        button.setText(PrimaryNavigationPolicy.labelFor(id));
        button.setAllCaps(false);
        button.setTextSize(10.0f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? AppUiTheme.NAV_TEXT : AppUiTheme.NAV_MUTED);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 1), 0, dp(activity, 1), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(navBackground(selected));
        button.setContentDescription(PrimaryNavigationPolicy.labelFor(id)
                + (selected ? "、選択中" : ""));
        return button;
    }

    private static void bindNavigation(Activity activity, View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String label = button.getText() == null ? "" : button.getText().toString().trim();
            String id = idForLabel(label);
            if (!id.isEmpty()) {
                boolean selected = id.equals(destinationFor(activity));
                button.setOnClickListener(v -> navigate(activity, id));
                button.setEnabled(!selected);
                button.setTextColor(selected ? AppUiTheme.NAV_TEXT : AppUiTheme.NAV_MUTED);
                button.setBackground(navBackground(selected));
                button.setContentDescription(label + (selected ? "、選択中" : ""));
            }
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            bindNavigation(activity, group.getChildAt(i));
        }
    }

    private static String idForLabel(String label) {
        for (String id : PrimaryNavigationPolicy.destinationIds()) {
            if (PrimaryNavigationPolicy.labelFor(id).equals(label)) return id;
        }
        return "";
    }

    private static void navigate(Activity current, String destination) {
        if (!PrimaryNavigationPolicy.isDestination(destination)) return;
        if (destination.equals(destinationFor(current))) return;
        Class<? extends Activity> target = activityFor(destination);
        if (target == null) return;
        Intent intent = new Intent(current, target);
        intent.addFlags(PrimaryNavigationPolicy.intentFlags());
        current.startActivity(intent);
        current.overridePendingTransition(0, 0);
    }

    private static Class<? extends Activity> activityFor(String destination) {
        if (PrimaryNavigationPolicy.CONVERSATION.equals(destination)) return DemoActivityV032.class;
        if (PrimaryNavigationPolicy.STATUS.equals(destination)) return NpcStatusActivity.class;
        if (PrimaryNavigationPolicy.DUNGEON.equals(destination)) return DungeonActivity.class;
        if (PrimaryNavigationPolicy.CODEX.equals(destination)) return CodexActivity.class;
        if (PrimaryNavigationPolicy.SETTINGS.equals(destination)) return SettingsActivity.class;
        if (PrimaryNavigationPolicy.MANAGER.equals(destination)) return NpcManagerActivity.class;
        return null;
    }

    private static String destinationFor(Activity activity) {
        if (activity instanceof DemoActivityV032) return PrimaryNavigationPolicy.CONVERSATION;
        if (activity instanceof NpcStatusActivity) return PrimaryNavigationPolicy.STATUS;
        if (activity instanceof DungeonActivity || activity instanceof IndividualDungeonActivity) {
            return PrimaryNavigationPolicy.DUNGEON;
        }
        if (activity instanceof CodexActivity) return PrimaryNavigationPolicy.CODEX;
        if (activity instanceof SettingsActivity) return PrimaryNavigationPolicy.SETTINGS;
        if (activity instanceof NpcManagerActivity) return PrimaryNavigationPolicy.MANAGER;
        return "";
    }

    private static GradientDrawable navBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? AppUiTheme.NAV_SELECTED : AppUiTheme.NAV_BACKGROUND);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
