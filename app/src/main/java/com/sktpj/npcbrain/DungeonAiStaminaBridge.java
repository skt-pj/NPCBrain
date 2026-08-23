package com.sktpj.npcbrain;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.Locale;

final class DungeonAiStaminaBridge {
    private static final String TAG = "npcbrain_dungeon_ai_stamina_v0417";
    private static final long REFRESH_MS = 400L;

    private DungeonAiStaminaBridge() {
    }

    static void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            ProgressBar hpBar = (ProgressBar) field(activity, "hpBar");
            if (hpBar == null) return;
            ViewParent parent = hpBar.getParent();
            if (!(parent instanceof LinearLayout)) return;
            LinearLayout hud = (LinearLayout) parent;
            for (int i = 0; i < hud.getChildCount(); i++) {
                if (TAG.equals(hud.getChildAt(i).getTag())) return;
            }

            LinearLayout stamina = new LinearLayout(activity);
            stamina.setTag(TAG);
            stamina.setOrientation(LinearLayout.VERTICAL);

            TextView label = new TextView(activity);
            label.setTextColor(Color.rgb(204, 222, 244));
            label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            stamina.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            ProgressBar bar = new ProgressBar(
                    activity,
                    null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(31, 43, 57)));
            bar.setProgressTintList(ColorStateList.valueOf(Color.rgb(94, 153, 224)));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(activity, 6));
            barParams.topMargin = dp(activity, 3);
            stamina.addView(bar, barParams);

            int hpIndex = hud.indexOfChild(hpBar);
            LinearLayout.LayoutParams staminaParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            staminaParams.topMargin = dp(activity, 6);
            hud.addView(stamina, Math.min(hpIndex + 1, hud.getChildCount()), staminaParams);

            DungeonAiStaminaStore store = new DungeonAiStaminaStore(activity);
            Runnable refresh = new Runnable() {
                @Override
                public void run() {
                    if (stamina.getParent() == null || activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    String npcId = selectedNpcId(activity);
                    DungeonAiStaminaStore.Snapshot snapshot = store.snapshot(npcId);
                    label.setText(String.format(
                            Locale.JAPAN,
                            "AI STAMINA %d%% · 概算 ¥%.2f / ¥%.2f",
                            snapshot.remainingPercent,
                            snapshot.remainingJpy,
                            DungeonTokenCostPolicy.MAX_BUDGET_JPY));
                    bar.setProgress(snapshot.remainingPercent);
                    stamina.setContentDescription(label.getText());
                    stamina.postDelayed(this, REFRESH_MS);
                }
            };
            stamina.post(refresh);
        } catch (Exception ignored) {
        }
    }

    private static String selectedNpcId(DungeonActivity activity) {
        try {
            Object value = field(activity, "selectedNpcId");
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return NpcId.of((String) value).value();
            }
        } catch (Exception ignored) {
        }
        return "npc1";
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
