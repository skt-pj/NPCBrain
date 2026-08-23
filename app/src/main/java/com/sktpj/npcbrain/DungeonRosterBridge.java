package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class DungeonRosterBridge {
    private static final String TAG = "npcbrain_dungeon_roster_v0419";
    private static final long REFRESH_MS = 350L;
    private static final WeakHashMap<DungeonActivity, BridgeState> STATES = new WeakHashMap<>();

    private DungeonRosterBridge() {
    }

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        BridgeState existing = STATES.get(activity);
        if (existing != null) {
            reconcileSelection(activity, existing);
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
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button change = compactButton(activity, "変更");
        header.addView(change, new LinearLayout.LayoutParams(dp(activity, 76), dp(activity, 38)));
        container.addView(header);

        LinearLayout slots = new LinearLayout(activity);
        slots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slotsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        slotsParams.topMargin = dp(activity, 5);
        container.addView(slots, slotsParams);

        root.removeView(oldSelector);
        root.addView(container, insertAt);

        BridgeState state = new BridgeState(
                new DungeonRosterStore(activity),
                new NpcAiStaminaStore(activity),
                title,
                slots,
                change);
        STATES.put(activity, state);
        change.setOnClickListener(v -> showRosterDialog(activity, state));
        reconcileSelection(activity, state);
        render(activity, state);

        Handler handler = new Handler(Looper.getMainLooper());
        state.handler = handler;
        state.task = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed() || container.getParent() == null) return;
                reconcileSelection(activity, state);
                advanceBackgroundIfDue(activity, state);
                render(activity, state);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        handler.post(state.task);
    }

    private static void render(DungeonActivity activity, BridgeState state) {
        List<String> active = state.store.activeNpcIds();
        state.title.setText("探索メンバー  " + active.size() + "/" + DungeonRosterPolicy.MAX_ACTIVE);
        state.slots.removeAllViews();
        String selected = selectedNpcId(activity);
        for (int i = 0; i < DungeonRosterPolicy.MAX_ACTIVE; i++) {
            Button slot;
            if (i < active.size()) {
                String npcId = active.get(i);
                slot = slotButton(activity, slotLabel(activity, state, npcId), npcId.equals(selected));
                slot.setContentDescription(slotDescription(activity, state, npcId));
                slot.setOnClickListener(v -> selectNpc(activity, npcId));
            } else {
                slot = slotButton(activity, "+ メンバー", false);
                slot.setOnClickListener(v -> showRosterDialog(activity, state));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 60), 1f);
            if (i > 0) params.leftMargin = dp(activity, 6);
            state.slots.addView(slot, params);
        }
    }

    private static String slotLabel(DungeonActivity activity, BridgeState bridge, String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(activity, npcId));
        DungeonState dungeon = new DungeonStore(activity).load(npcId);
        DungeonParticipationState participation = DungeonParticipationStore.forNpc(activity, npcId).load();
        NpcAiStaminaStore.Snapshot stamina = bridge.stamina.snapshot(npcId);
        String floor = dungeon == null ? "未開始" : dungeon.floor + "F";
        return character.displayName() + "\n" + floor + " · " + shortParticipation(participation)
                + " · " + stamina.remainingPercent + "%";
    }

    private static String slotDescription(DungeonActivity activity, BridgeState bridge, String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(activity, npcId));
        DungeonState dungeon = new DungeonStore(activity).load(npcId);
        DungeonParticipationState participation = DungeonParticipationStore.forNpc(activity, npcId).load();
        NpcAiStaminaStore.Snapshot stamina = bridge.stamina.snapshot(npcId);
        return character.displayName() + "、"
                + (dungeon == null ? "ダンジョン未開始" : dungeon.floor + "階")
                + "、参加意思 " + participation.label()
                + "、AI STAMINA " + stamina.remainingPercent + "%";
    }

    private static String shortParticipation(DungeonParticipationState state) {
        if (state == null) return "未相談";
        if (state.isAccepted()) return "参加中";
        if (DungeonParticipationState.HESITATE.equals(state.stance)) return "迷い";
        if (DungeonParticipationState.REFUSE.equals(state.stance)) return "拒否";
        if (DungeonParticipationState.WITHDRAW.equals(state.stance)) return "撤回";
        return "未相談";
    }

    private static void showRosterDialog(DungeonActivity activity, BridgeState bridge) {
        List<String> candidates = bridge.store.candidates();
        List<String> working = new ArrayList<>(bridge.store.activeNpcIds());

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 16), dp(activity, 6));
        TextView count = new TextView(activity);
        count.setTextSize(13);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        count.setTextColor(Color.rgb(42, 50, 62));
        body.addView(count);

        TextView note = new TextView(activity);
        note.setText("最大3人。同じ画面でそれぞれ独立したダンジョンを進みます。参加未了承のNPCは会話で相談するまで自動進行しません。");
        note.setTextSize(11);
        note.setTextColor(Color.rgb(98, 105, 116));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(activity, 5);
        noteParams.bottomMargin = dp(activity, 8);
        body.addView(note, noteParams);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 390)));

        populatePicker(activity, bridge, candidates, working, count, list);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ダンジョンに行くNPCを選択")
                .setView(body)
                .setPositiveButton("このメンバーで探索", null)
                .setNegativeButton("キャンセル", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (working.isEmpty()) {
                        Toast.makeText(activity, "1人以上選んでください", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bridge.store.save(working);
                    reconcileSelection(activity, bridge);
                    render(activity, bridge);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static void populatePicker(
            DungeonActivity activity,
            BridgeState bridge,
            List<String> candidates,
            List<String> working,
            TextView count,
            LinearLayout list
    ) {
        count.setText("選択中  " + working.size() + "/" + DungeonRosterPolicy.MAX_ACTIVE);
        list.removeAllViews();
        if (candidates.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("参加できる登録NPCがいません。");
            empty.setTextSize(13);
            list.addView(empty);
            return;
        }
        for (String npcId : candidates) {
            boolean selected = working.contains(npcId);
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(activity, npcId));
            DungeonState dungeon = new DungeonStore(activity).load(npcId);
            DungeonParticipationState participation = DungeonParticipationStore.forNpc(activity, npcId).load();
            NpcAiStaminaStore.Snapshot stamina = bridge.stamina.snapshot(npcId);
            String floor = dungeon == null ? "未開始" : dungeon.floor + "F";
            Button row = new Button(activity);
            row.setAllCaps(false);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setTextSize(12);
            row.setTextColor(Color.rgb(37, 45, 56));
            row.setText((selected ? "✓  " : "○  ") + character.displayName() + "  (" + npcId + ")\n"
                    + "     " + participation.label() + " · " + floor
                    + " · AI STAMINA " + stamina.remainingPercent + "%");
            row.setBackground(pickerBackground(activity, selected));
            row.setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 10), dp(activity, 7));
            row.setOnClickListener(v -> {
                if (working.contains(npcId)) {
                    working.remove(npcId);
                } else if (working.size() >= DungeonRosterPolicy.MAX_ACTIVE) {
                    Toast.makeText(activity, "探索メンバーは最大3人です", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    working.add(npcId);
                }
                populatePicker(activity, bridge, candidates, working, count, list);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 64));
            params.bottomMargin = dp(activity, 6);
            list.addView(row, params);
        }
    }

    private static void reconcileSelection(DungeonActivity activity, BridgeState state) {
        List<String> active = state.store.activeNpcIds();
        if (active.isEmpty()) return;
        String selected = selectedNpcId(activity);
        if (!active.contains(selected)) selectNpc(activity, active.get(0));
    }

    private static void advanceBackgroundIfDue(DungeonActivity activity, BridgeState bridge) {
        List<String> active = bridge.store.activeNpcIds();
        if (active.size() <= 1) return;
        String selected = selectedNpcId(activity);
        long now = System.currentTimeMillis();
        long interval = turnInterval(activity);

        boolean paused = booleanField(activity, "paused");
        if (paused && canAdvance(activity, selected)) return;

        for (String npcId : active) {
            if (npcId.equals(selected) || !canAdvance(activity, npcId)) continue;
            long last = bridge.lastBackgroundStepMs.containsKey(npcId)
                    ? bridge.lastBackgroundStepMs.get(npcId) : 0L;
            if (now - last < interval) continue;
            advanceBackgroundNpc(activity, npcId);
            bridge.lastBackgroundStepMs.put(npcId, now);
        }
    }

    private static boolean canAdvance(DungeonActivity activity, String npcId) {
        if (npcId == null || npcId.trim().isEmpty()) return false;
        DungeonParticipationState participation = DungeonParticipationStore.forNpc(activity, npcId).load();
        if (!participation.isAccepted()) return false;
        DungeonObjective objective = new DungeonObjectiveStore(activity).load(npcId);
        if (objective == null || !objective.isActive()) return false;
        DungeonState state = new DungeonStore(activity).load(npcId);
        return state == null || !objective.isComplete(state.floor);
    }

    private static void advanceBackgroundNpc(DungeonActivity activity, String npcId) {
        DungeonStore dungeonStore = new DungeonStore(activity);
        DungeonState state = dungeonStore.load(npcId);
        if (state == null) {
            long seed = System.nanoTime() ^ System.currentTimeMillis() ^ ((long) npcId.hashCode() << 17);
            state = DungeonGenerator.generate(seed, 1);
        }
        DungeonPerception.refreshExploration(state);
        DungeonObjective objective = new DungeonObjectiveStore(activity).load(npcId);
        if (objective == null || !objective.isActive() || objective.isComplete(state.floor)) {
            dungeonStore.save(npcId, state);
            return;
        }

        DungeonPersonalityPolicy.Traits traits = traitsForNpc(activity, npcId);
        DungeonMindStore.Snapshot mind = new DungeonMindStore(activity).load(npcId);
        DungeonPlan plan = mind == null ? null : mind.plan;
        if (plan == null || !plan.matches(objective)) {
            plan = DungeonPlan.local(objective, traits, state, "非表示探索のローカル計画");
        }
        DungeonIntent intent = DungeonIntent.localFallback(state, traits, "非表示探索をローカル実行");
        DungeonStepResult step = DungeonEngine.stepDetailed(state, traits, intent, plan);
        DungeonState next = step == null || step.state == null ? state : step.state;
        DungeonPerception.refreshExploration(next);
        dungeonStore.save(npcId, next);
    }

    private static DungeonPersonalityPolicy.Traits traitsForNpc(DungeonActivity activity, String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(activity, npcId));
        return new DungeonPersonalityPolicy.Traits(
                character.traitPercent(CharacterStateStore.extraversionKey()),
                character.traitPercent(CharacterStateStore.neuroticismKey()),
                character.traitPercent(CharacterStateStore.agreeablenessKey()),
                character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                character.traitPercent(CharacterStateStore.opennessKey()));
    }

    private static long turnInterval(DungeonActivity activity) {
        try {
            Field indexField = DungeonActivity.class.getDeclaredField("speedIndex");
            indexField.setAccessible(true);
            int index = indexField.getInt(activity);
            Field intervalsField = DungeonActivity.class.getDeclaredField("TURN_INTERVALS");
            intervalsField.setAccessible(true);
            long[] intervals = (long[]) intervalsField.get(null);
            if (intervals != null && index >= 0 && index < intervals.length) return intervals[index];
        } catch (Exception ignored) {
        }
        return 650L;
    }

    private static void selectNpc(DungeonActivity activity, String npcId) {
        try {
            Method method = DungeonActivity.class.getDeclaredMethod("selectNpc", String.class);
            method.setAccessible(true);
            method.invoke(activity, npcId);
        } catch (Exception ignored) {
        }
    }

    private static String selectedNpcId(DungeonActivity activity) {
        try {
            Field field = DungeonActivity.class.getDeclaredField("selectedNpcId");
            field.setAccessible(true);
            Object value = field.get(activity);
            if (value != null) return NpcId.of(value.toString()).value();
        } catch (Exception ignored) {
        }
        return "npc1";
    }

    private static boolean booleanField(DungeonActivity activity, String name) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Button buttonField(DungeonActivity activity, String name) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof Button ? (Button) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Button compactButton(DungeonActivity activity, String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.rgb(220, 234, 250));
        button.setBackground(slotBackground(activity, false));
        button.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
        return button;
    }

    private static Button slotButton(DungeonActivity activity, String label, boolean selected) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 3), 0, dp(activity, 3), 0);
        button.setBackground(slotBackground(activity, selected));
        return button;
    }

    private static GradientDrawable slotBackground(DungeonActivity activity, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? Color.rgb(42, 91, 156) : Color.rgb(20, 32, 47));
        drawable.setStroke(dp(activity, 1), selected ? Color.rgb(80, 139, 207) : Color.rgb(42, 59, 77));
        drawable.setCornerRadius(dp(activity, 11));
        return drawable;
    }

    private static GradientDrawable pickerBackground(DungeonActivity activity, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? Color.rgb(226, 238, 253) : Color.rgb(246, 248, 251));
        drawable.setStroke(dp(activity, 1), selected ? Color.rgb(91, 143, 207) : Color.rgb(218, 224, 232));
        drawable.setCornerRadius(dp(activity, 10));
        return drawable;
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class BridgeState {
        final DungeonRosterStore store;
        final NpcAiStaminaStore stamina;
        final TextView title;
        final LinearLayout slots;
        final Button change;
        final Map<String, Long> lastBackgroundStepMs = new HashMap<>();
        Handler handler;
        Runnable task;

        BridgeState(
                DungeonRosterStore store,
                NpcAiStaminaStore stamina,
                TextView title,
                LinearLayout slots,
                Button change
        ) {
            this.store = store;
            this.stamina = stamina;
            this.title = title;
            this.slots = slots;
            this.change = change;
        }
    }
}
