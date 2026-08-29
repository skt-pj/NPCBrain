package com.sktpj.npcbrain;

import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.WeakHashMap;

/** Adds precise spent-cost visibility and an all-NPC usage entry point to NPC Status. */
final class NpcAiUsageUiBridge {
    private static final String TAG = "npcbrain_all_ai_usage_button_v0439hf2";
    private static final long REFRESH_MS = 240L;
    private static final WeakHashMap<NpcStatusActivity, State> STATES = new WeakHashMap<>();

    private NpcAiUsageUiBridge() {
    }

    static synchronized void install(NpcStatusActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (STATES.containsKey(activity)) return;
        TextView staminaValue = field(activity, "staminaValue", TextView.class);
        if (staminaValue == null) return;

        Button all = new Button(activity);
        all.setTag(TAG);
        all.setText("全員のAI使用量");
        all.setAllCaps(false);
        all.setTextSize(12);
        all.setTextColor(Color.rgb(220, 234, 252));
        all.setBackgroundColor(Color.rgb(25, 50, 78));
        all.setOnClickListener(v -> activity.startActivity(new Intent(activity, AiUsageActivity.class)));

        ViewParent rowParent = staminaValue.getParent();
        if (rowParent instanceof ViewGroup) {
            ViewParent cardParent = ((ViewGroup) rowParent).getParent();
            if (cardParent instanceof LinearLayout) {
                LinearLayout card = (LinearLayout) cardParent;
                boolean exists = false;
                for (int i = 0; i < card.getChildCount(); i++) {
                    if (TAG.equals(card.getChildAt(i).getTag())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    int index = card.indexOfChild((android.view.View) rowParent);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(activity, 46));
                    params.topMargin = dp(activity, 8);
                    card.addView(all, Math.min(index + 1, card.getChildCount()), params);
                }
            }
        }

        State state = new State(new NpcAiStaminaStore(activity), staminaValue);
        STATES.put(activity, state);
        Handler handler = new Handler(Looper.getMainLooper());
        state.handler = handler;
        state.task = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                refresh(activity, state);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        handler.post(state.task);
    }

    static synchronized void uninstall(NpcStatusActivity activity) {
        State state = STATES.remove(activity);
        if (state != null && state.handler != null && state.task != null) {
            state.handler.removeCallbacks(state.task);
        }
    }

    private static void refresh(NpcStatusActivity activity, State state) {
        String npcId = selectedNpcId(activity);
        NpcAiStaminaStore.Snapshot snapshot = state.store.snapshot(npcId);
        state.value.setText(
                snapshot.remainingPercent + "%"
                        + " · 消費 " + NpcAiUsageDisplayPolicy.formatSpentJpy(snapshot.spentJpy)
                        + " / ¥10.00"
                        + "\n残額 " + NpcAiUsageDisplayPolicy.formatRemainingJpy(snapshot.remainingJpy)
                        + " · 累積 " + String.format(Locale.JAPAN, "%,d", snapshot.totalTokens)
                        + " tokens");
    }

    private static String selectedNpcId(NpcStatusActivity activity) {
        try {
            Field field = NpcStatusActivity.class.getDeclaredField("selectedNpcId");
            field.setAccessible(true);
            Object value = field.get(activity);
            if (value instanceof String) return NpcId.of((String) value).value();
        } catch (Exception ignored) {
        }
        return "npc1";
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int dp(NpcStatusActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class State {
        final NpcAiStaminaStore store;
        final TextView value;
        Handler handler;
        Runnable task;

        State(NpcAiStaminaStore store, TextView value) {
            this.store = store;
            this.value = value;
        }
    }
}
