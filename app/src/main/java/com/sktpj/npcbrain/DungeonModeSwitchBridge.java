package com.sktpj.npcbrain;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.WeakHashMap;

/** Presentation-only switch between party and individual observation surfaces. */
final class DungeonModeSwitchBridge {
    private static final String TAG = "npcbrain_dungeon_mode_switch_v201";
    private static final WeakHashMap<DungeonActivity, Boolean> INSTALLED = new WeakHashMap<>();

    private DungeonModeSwitchBridge() {}

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (Boolean.TRUE.equals(INSTALLED.get(activity))) return;
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View first = content.getChildAt(0);
        if (!(first instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) first;

        for (int i = 0; i < root.getChildCount(); i++) {
            if (TAG.equals(root.getChildAt(i).getTag())) {
                INSTALLED.put(activity, true);
                return;
            }
        }

        LinearLayout row = new LinearLayout(activity);
        row.setTag(TAG);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

        Button party = modeButton(activity, "パーティ", true);
        party.setEnabled(false);
        row.addView(party, new LinearLayout.LayoutParams(0, dp(activity, 42), 1f));

        Button individual = modeButton(activity, "各自 8画面", false);
        LinearLayout.LayoutParams individualParams = new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
        individualParams.leftMargin = dp(activity, 7);
        row.addView(individual, individualParams);
        individual.setOnClickListener(v -> {
            Intent intent = new Intent(activity, IndividualDungeonActivity.class);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        });

        root.addView(row, Math.min(2, root.getChildCount()));
        INSTALLED.put(activity, true);
    }

    private static Button modeButton(DungeonActivity activity, String label, boolean selected) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(activity, 10));
        bg.setColor(selected ? Color.rgb(45, 94, 137) : Color.rgb(19, 34, 50));
        bg.setStroke(dp(activity, 1), selected ? Color.rgb(90, 157, 214) : Color.rgb(47, 69, 91));
        button.setBackground(bg);
        return button;
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
