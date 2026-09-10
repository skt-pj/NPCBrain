package com.sktpj.npcbrain;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.WeakHashMap;

/** Roster presentation only. It never resolves or advances a dungeon turn. */
final class DungeonRosterUiBridge {
    private static final String TAG = "npcbrain_dungeon_roster_ui_v201";
    private static final long REFRESH_MS = 500L;
    private static final WeakHashMap<DungeonActivity, State> STATES = new WeakHashMap<>();

    private DungeonRosterUiBridge() {}

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        State existing = STATES.get(activity);
        if (existing != null) {
            reconcileSelection(activity, existing.store);
            render(activity, existing);
            return;
        }

        Button npc1Button = buttonField(activity, "npc1Button");
        if (npc1Button == null) return;
        ViewParent selectorParent = npc1Button.getParent();
        if (!(selectorParent instanceof LinearLayout)) return;
        LinearLayout oldSelector = (LinearLayout) selectorParent;
        ViewParent rootParent = oldSelector.getParent();
        if (!(rootParent instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) rootParent;
        int insertAt = root.indexOfChild(oldSelector);
        if (insertAt < 0) return;

        LinearLayout container = new LinearLayout(activity);
        container.setTag(TAG);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(activity, 7), 0, dp(activity, 7));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setTextColor(Color.rgb(200, 219, 241));
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button change = compactButton(activity, "メンバー選択");
        header.addView(change, new LinearLayout.LayoutParams(dp(activity, 112), dp(activity, 38)));
        container.addView(header);

        LinearLayout slots = new LinearLayout(activity);
        slots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slotsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slotsParams.topMargin = dp(activity, 5);
        container.addView(slots, slotsParams);

        root.removeView(oldSelector);
        root.addView(container, insertAt);

        State state = new State(
                new DungeonRosterStore(activity),
                new NpcAiStaminaStore(activity),
                title,
                slots);
        STATES.put(activity, state);
        change.setOnClickListener(v -> activity.startActivity(new Intent(activity, DungeonRosterActivity.class)));
        reconcileSelection(activity, state.store);
        render(activity, state);

        state.handler.post(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed() || container.getParent() == null) return;
                reconcileSelection(activity, state.store);
                render(activity, state);
                state.handler.postDelayed(this, REFRESH_MS);
            }
        });
    }

    private static void render(DungeonActivity activity, State state) {
        List<String> active = state.store.activeNpcIds();
        state.title.setText("探索中  " + active.size() + "/" + DungeonRosterPolicy.MAX_ACTIVE);
        state.slots.removeAllViews();
        String selected = stringField(activity, "selectedNpcId");
        for (int i = 0; i < DungeonRosterPolicy.MAX_ACTIVE; i++) {
            Button slot;
            if (i < active.size()) {
                String npcId = active.get(i);
                slot = slotButton(activity, slotLabel(activity, state.stamina, npcId), npcId.equals(selected));
                slot.setOnClickListener(v -> selectNpc(activity, npcId));
            } else {
                slot = slotButton(activity, "+ 追加", false);
                slot.setOnClickListener(v -> activity.startActivity(new Intent(activity, DungeonRosterActivity.class)));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 60), 1f);
            if (i > 0) params.leftMargin = dp(activity, 6);
            state.slots.addView(slot, params);
        }
    }

    private static String slotLabel(DungeonActivity activity, NpcAiStaminaStore staminaStore, String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(activity, npcId));
        DungeonState dungeon = new DungeonStore(activity).load(npcId);
        NpcAiStaminaStore.Snapshot stamina = staminaStore.snapshot(npcId);
        String floor = dungeon == null ? "未開始" : dungeon.floor + "F";
        return character.displayName() + "\n" + floor + " · " + stamina.remainingPercent + "%";
    }

    private static void reconcileSelection(DungeonActivity activity, DungeonRosterStore store) {
        List<String> active = store.activeNpcIds();
        if (active.isEmpty()) return;
        String selected = stringField(activity, "selectedNpcId");
        if (!active.contains(selected)) selectNpc(activity, active.get(0));
    }

    private static void selectNpc(DungeonActivity activity, String npcId) {
        if (npcId == null || npcId.trim().isEmpty()) return;
        // Do not allow the legacy Activity to persist its observation mirror while switching.
        setField(activity, "state", null);
        try {
            Method method = DungeonActivity.class.getDeclaredMethod("selectNpc", String.class);
            method.setAccessible(true);
            method.invoke(activity, npcId);
        } catch (Exception ignored) {
        }
        DungeonWorldObserverBridge.install(activity);
    }

    private static Button compactButton(DungeonActivity activity, String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.rgb(224, 238, 252));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(24, 42, 61));
        background.setStroke(dp(activity, 1), Color.rgb(52, 80, 106));
        background.setCornerRadius(dp(activity, 9));
        button.setBackground(background);
        return button;
    }

    private static Button slotButton(DungeonActivity activity, String label, boolean selected) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(9);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 2), 0, dp(activity, 2), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? Color.rgb(42, 91, 156) : Color.rgb(20, 32, 47));
        background.setStroke(dp(activity, 1), selected ? Color.rgb(80, 139, 207) : Color.rgb(42, 59, 77));
        background.setCornerRadius(dp(activity, 9));
        button.setBackground(background);
        return button;
    }

    private static Button buttonField(Object target, String name) {
        Object value = fieldValue(target, name);
        return value instanceof Button ? (Button) value : null;
    }

    private static String stringField(Object target, String name) {
        Object value = fieldValue(target, name);
        return value == null ? "" : value.toString().trim();
    }

    private static Object fieldValue(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {
        }
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class State {
        final DungeonRosterStore store;
        final NpcAiStaminaStore stamina;
        final TextView title;
        final LinearLayout slots;
        final Handler handler = new Handler(Looper.getMainLooper());

        State(DungeonRosterStore store, NpcAiStaminaStore stamina, TextView title, LinearLayout slots) {
            this.store = store;
            this.stamina = stamina;
            this.title = title;
            this.slots = slots;
        }
    }
}
