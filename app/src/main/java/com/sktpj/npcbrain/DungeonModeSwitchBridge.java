package com.sktpj.npcbrain;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

final class DungeonModeSwitchBridge {
    private static final String TAG = "npcbrain_dungeon_mode_switch_v043";
    private static final long REFRESH_MS = 300L;
    private static final WeakHashMap<DungeonActivity, State> STATES = new WeakHashMap<>();

    private DungeonModeSwitchBridge() {
    }

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (STATES.containsKey(activity)) return;
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View first = content.getChildAt(0);
        if (!(first instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) first;

        for (int i = 0; i < root.getChildCount(); i++) {
            if (TAG.equals(root.getChildAt(i).getTag())) return;
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
        LinearLayout.LayoutParams individualParams = new LinearLayout.LayoutParams(
                0, dp(activity, 42), 1f);
        individualParams.leftMargin = dp(activity, 7);
        row.addView(individual, individualParams);
        individual.setOnClickListener(v -> {
            Intent intent = new Intent(activity, IndividualDungeonActivity.class);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        });

        root.addView(row, Math.min(2, root.getChildCount()));
        State state = new State(new DungeonRosterStore(activity));
        STATES.put(activity, state);
        state.handler = new Handler(Looper.getMainLooper());
        state.task = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed() || row.getParent() == null) return;
                syncEmptyPartyPause(activity, state);
                state.handler.postDelayed(this, REFRESH_MS);
            }
        };
        state.handler.post(state.task);
    }

    private static void syncEmptyPartyPause(DungeonActivity activity, State state) {
        boolean empty = state.roster.activeNpcIds().isEmpty();
        boolean paused = booleanField(activity, "paused");
        if (empty && !paused) {
            if (setBooleanField(activity, "paused", true)) {
                state.autoPausedForEmptyParty = true;
                invoke(activity, "render");
            }
            return;
        }
        if (!empty && state.autoPausedForEmptyParty) {
            state.autoPausedForEmptyParty = false;
            if (setBooleanField(activity, "paused", false)) {
                invoke(activity, "render");
                invoke(activity, "scheduleNextTurn");
            }
        }
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

    private static boolean booleanField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean setBooleanField(Object target, String name, boolean value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(target, value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void invoke(Object target, String name) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (Exception ignored) {
        }
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class State {
        final DungeonRosterStore roster;
        Handler handler;
        Runnable task;
        boolean autoPausedForEmptyParty;

        State(DungeonRosterStore roster) {
            this.roster = roster;
        }
    }
}
