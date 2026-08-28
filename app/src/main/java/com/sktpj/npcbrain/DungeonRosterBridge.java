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

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        Button change = compactButton(activity, "メンバー選択");
        header.addView(change, new LinearLayout.LayoutParams(dp(activity, 112), dp(activity, 38)));
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
                new SecureApiKeyStore(activity),
                new ModelSettingsStore(activity),
                new DungeonBrainRuntime(activity),
                new DungeonNpcStateCoordinator(activity),
                title,
                slots,
                change);
        STATES.put(activity, state);
        change.setOnClickListener(v -> openRosterScreen(activity));
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
        state.title.setText("探索中  " + active.size() + "/" + DungeonRosterPolicy.MAX_ACTIVE);
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
                slot = slotButton(activity, "+ 追加", false);
                slot.setContentDescription("探索メンバーを追加");
                slot.setOnClickListener(v -> openRosterScreen(activity));
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
        String cognition = Boolean.TRUE.equals(bridge.backgroundBrainThinking.get(npcId)) ? " · BRAIN" : "";
        return character.displayName() + "\n" + floor + " · " + shortParticipation(participation)
                + " · " + stamina.remainingPercent + "%" + cognition;
    }

    private static String slotDescription(DungeonActivity activity, BridgeState bridge, String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(activity, npcId));
        DungeonState dungeon = new DungeonStore(activity).load(npcId);
        DungeonParticipationState participation = DungeonParticipationStore.forNpc(activity, npcId).load();
        NpcAiStaminaStore.Snapshot stamina = bridge.stamina.snapshot(npcId);
        return character.displayName() + "、"
                + (dungeon == null ? "ダンジョン未開始" : dungeon.floor + "階")
                + "、参加意思 " + participation.label()
                + "、AI STAMINA " + stamina.remainingPercent + "%"
                + (Boolean.TRUE.equals(bridge.backgroundBrainThinking.get(npcId))
                ? "、Brain再評価中" : "");
    }

    private static String shortParticipation(DungeonParticipationState state) {
        if (state == null) return "未相談";
        if (state.isAccepted()) return "参加";
        if (DungeonParticipationState.HESITATE.equals(state.stance)) return "迷い";
        if (DungeonParticipationState.REFUSE.equals(state.stance)) return "拒否";
        if (DungeonParticipationState.WITHDRAW.equals(state.stance)) return "撤回";
        return "未相談";
    }

    private static void openRosterScreen(DungeonActivity activity) {
        Intent intent = new Intent(activity, DungeonRosterActivity.class);
        activity.startActivity(intent);
    }

    private static void reconcileSelection(DungeonActivity activity, BridgeState state) {
        List<String> active = state.store.activeNpcIds();
        if (active.isEmpty()) return;
        String selected = selectedNpcId(activity);
        if (!active.contains(selected)) selectNpc(activity, active.get(0));
    }

    private static void advanceBackgroundIfDue(DungeonActivity activity, BridgeState bridge) {
        List<String> active = bridge.store.activeNpcIds();
        if (active.size() <= 1 || booleanField(activity, "paused")) return;
        String selected = selectedNpcId(activity);
        long now = System.currentTimeMillis();
        long interval = turnInterval(activity);

        for (String npcId : active) {
            if (npcId.equals(selected)
                    || Boolean.TRUE.equals(bridge.backgroundBrainThinking.get(npcId))
                    || !canAdvance(bridge, npcId)) continue;
            long last = bridge.lastBackgroundStepMs.containsKey(npcId)
                    ? bridge.lastBackgroundStepMs.get(npcId) : 0L;
            if (now - last < interval) continue;
            BackgroundStep outcome = advanceBackgroundNpc(activity, bridge, npcId);
            bridge.lastBackgroundStepMs.put(npcId, now);
            if (outcome != null
                    && !outcome.trigger.isEmpty()
                    && DungeonCognitionGate.isCognitionTrigger(outcome.trigger)) {
                requestBackgroundBrain(activity, bridge, npcId, outcome);
            }
        }
    }

    private static boolean canAdvance(BridgeState bridge, String npcId) {
        if (bridge == null || npcId == null || npcId.trim().isEmpty()) return false;
        return bridge.npcStateCoordinator.canAdvance(npcId);
    }

    private static BackgroundStep advanceBackgroundNpc(
            DungeonActivity activity,
            BridgeState bridge,
            String npcId
    ) {
        DungeonStore dungeonStore = new DungeonStore(activity);
        DungeonState state = dungeonStore.load(npcId);
        if (state == null) {
            long seed = System.nanoTime() ^ System.currentTimeMillis() ^ ((long) npcId.hashCode() << 17);
            state = DungeonGenerator.generate(seed, 1);
        }
        DungeonPerception.refreshExploration(state);
        DungeonObjective objective = new DungeonObjectiveStore(activity).load(npcId);
        if (objective == null) objective = DungeonObjective.none();
        if (state.hp <= 0 || (objective.isActive() && objective.isComplete(state.floor))) {
            dungeonStore.save(npcId, state);
            return null;
        }

        DungeonPersonalityPolicy.Traits traits = traitsForNpc(activity, npcId);
        DungeonMindStore mindStore = new DungeonMindStore(activity);
        DungeonMindStore.Snapshot mind = mindStore.load(npcId);
        DungeonPlan plan = mind == null ? null : mind.plan;
        if (plan == null || !plan.matches(objective)) {
            plan = DungeonPlan.local(objective, traits, state, "非表示探索の中立ローカル計画");
        }

        DungeonCognitionGate.Signal beforeSignal = DungeonCognitionGate.snapshot(state);
        DungeonProgressMonitor.Snapshot beforeProgress = bridge.backgroundProgress.get(npcId);
        if (beforeProgress == null || beforeProgress.floor != state.floor) {
            beforeProgress = DungeonProgressMonitor.initial(state);
        }
        int lastBrainTurn = plan != null && DungeonPlan.SOURCE_BRAIN.equals(plan.source)
                ? plan.createdTurn : -1;

        DungeonIntent intent = backgroundTurnIntent(state, traits, mind);
        DungeonStepResult step = DungeonEngine.stepDetailed(state, traits, intent, plan);
        DungeonState next = step == null || step.state == null ? state : step.state;
        DungeonPerception.refreshExploration(next);
        dungeonStore.save(npcId, next);

        DungeonCognitionGate.Signal afterSignal = DungeonCognitionGate.snapshot(next);
        String trigger = DungeonCognitionGate.reason(beforeSignal, afterSignal, lastBrainTurn);
        DungeonProgressMonitor.Result progress = DungeonProgressMonitor.observe(
                beforeProgress,
                next,
                lastBrainTurn);
        bridge.backgroundProgress.put(npcId, progress.snapshot);
        if (progress.shouldReplan) {
            trigger = DungeonCognitionGate.mergePending(
                    trigger,
                    DungeonCognitionGate.PROGRESS_STALLED);
        }
        if ((objective.isActive() && objective.isComplete(next.floor)) || next.hp <= 0) trigger = "";
        return new BackgroundStep(next, objective, plan, trigger);
    }

    static DungeonIntent backgroundTurnIntent(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            DungeonMindStore.Snapshot mind
    ) {
        if (state != null && mind != null && mind.intent != null
                && mind.intent.isBrain()
                && mind.intent.floor == state.floor
                && mind.intent.turn == state.turn) {
            return mind.intent;
        }
        return DungeonIntent.localFallback(state, traits, "非表示探索でBrain persistent planを合法実行");
    }

    private static void requestBackgroundBrain(
            DungeonActivity activity,
            BridgeState bridge,
            String npcId,
            BackgroundStep outcome
    ) {
        if (outcome == null || outcome.state == null || outcome.objective == null) return;
        if (Boolean.TRUE.equals(bridge.backgroundBrainThinking.get(npcId))) return;
        final String apiKey;
        try {
            apiKey = bridge.apiKeyStore.load().trim();
        } catch (Exception ignored) {
            return;
        }
        if (apiKey.isEmpty()) return;

        final DungeonState captured;
        try {
            captured = DungeonState.fromJson(new JSONObject(outcome.state.toJson().toString()));
        } catch (Exception ignored) {
            return;
        }
        if (captured == null) return;
        final DungeonObjective requestedObjective = outcome.objective;
        final DungeonPlan existingPlan = outcome.plan;
        final DungeonPersonalityPolicy.Traits traits = traitsForNpc(activity, npcId);
        final String effort = bridge.modelSettings.reasoningEffort();
        final String trigger = outcome.trigger;
        bridge.backgroundBrainThinking.put(npcId, true);

        new Thread(() -> {
            try {
                DungeonBrainRuntime.Result result = bridge.brainRuntime.run(
                        npcId,
                        captured,
                        trigger,
                        apiKey,
                        effort,
                        requestedObjective,
                        existingPlan,
                        null);
                DungeonPlan brainPlan = DungeonPlan.fromBrain(
                        requestedObjective,
                        traits,
                        captured,
                        result.intent,
                        result.publicSummary);
                activity.runOnUiThread(() -> finishBackgroundBrainSuccess(
                        activity,
                        bridge,
                        npcId,
                        captured,
                        requestedObjective,
                        brainPlan,
                        result));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.toString() : error.getMessage();
                activity.runOnUiThread(() -> finishBackgroundBrainFailure(
                        activity,
                        bridge,
                        npcId,
                        captured,
                        requestedObjective,
                        existingPlan,
                        message));
            }
        }, "npcbrain-v0427-background-dungeon-" + npcId).start();
    }

    private static void finishBackgroundBrainSuccess(
            DungeonActivity activity,
            BridgeState bridge,
            String npcId,
            DungeonState captured,
            DungeonObjective requestedObjective,
            DungeonPlan plan,
            DungeonBrainRuntime.Result result
    ) {
        bridge.backgroundBrainThinking.put(npcId, false);
        DungeonObjective currentObjective = new DungeonObjectiveStore(activity).load(npcId);
        DungeonState current = new DungeonStore(activity).load(npcId);
        if (!sameObjective(currentObjective, requestedObjective)
                || current == null
                || current.hp <= 0
                || currentObjective.isComplete(current.floor)
                || current.floor != captured.floor
                || current.turn != captured.turn) {
            return;
        }
        DungeonMindStore mindStore = new DungeonMindStore(activity);
        mindStore.save(npcId, new DungeonMindStore.Snapshot(
                result.intent,
                plan,
                result.trace,
                result.cognitiveGraph,
                DungeonMindStore.STATE_BRAIN,
                "",
                System.currentTimeMillis()));
        bridge.backgroundProgress.put(npcId, DungeonProgressMonitor.initial(current));
    }

    private static void finishBackgroundBrainFailure(
            DungeonActivity activity,
            BridgeState bridge,
            String npcId,
            DungeonState captured,
            DungeonObjective requestedObjective,
            DungeonPlan existingPlan,
            String error
    ) {
        bridge.backgroundBrainThinking.put(npcId, false);
        DungeonObjective currentObjective = new DungeonObjectiveStore(activity).load(npcId);
        DungeonState current = new DungeonStore(activity).load(npcId);
        if (!sameObjective(currentObjective, requestedObjective)
                || current == null
                || current.floor != captured.floor
                || current.turn != captured.turn) {
            return;
        }
        DungeonMindStore mindStore = new DungeonMindStore(activity);
        DungeonMindStore.Snapshot previous = mindStore.load(npcId);
        DungeonPlan plan = existingPlan != null && existingPlan.matches(currentObjective)
                ? existingPlan : DungeonPlan.local(
                currentObjective,
                traitsForNpc(activity, npcId),
                current,
                "Brain再評価失敗");
        mindStore.save(npcId, new DungeonMindStore.Snapshot(
                DungeonIntent.localFallback(
                        current,
                        traitsForNpc(activity, npcId),
                        "Brain再評価失敗"),
                plan,
                previous == null ? new JSONArray() : previous.trace,
                previous == null ? new JSONObject() : previous.cognitiveGraph,
                DungeonMindStore.STATE_LOCAL,
                compactError(error),
                System.currentTimeMillis()));
    }

    private static boolean sameObjective(DungeonObjective a, DungeonObjective b) {
        DungeonObjective left = a == null ? DungeonObjective.none() : a;
        DungeonObjective right = b == null ? DungeonObjective.none() : b;
        return left.type.equals(right.type) && left.targetFloor == right.targetFloor;
    }

    private static String compactError(String value) {
        String text = value == null ? "Brain失敗" : value.replace('\n', ' ').trim();
        return text.length() <= 96 ? text : text.substring(0, 96) + "…";
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

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class BackgroundStep {
        final DungeonState state;
        final DungeonObjective objective;
        final DungeonPlan plan;
        final String trigger;

        BackgroundStep(
                DungeonState state,
                DungeonObjective objective,
                DungeonPlan plan,
                String trigger
        ) {
            this.state = state;
            this.objective = objective;
            this.plan = plan;
            this.trigger = trigger == null ? "" : trigger;
        }
    }

    private static final class BridgeState {
        final DungeonRosterStore store;
        final NpcAiStaminaStore stamina;
        final SecureApiKeyStore apiKeyStore;
        final ModelSettingsStore modelSettings;
        final DungeonBrainRuntime brainRuntime;
        final DungeonNpcStateCoordinator npcStateCoordinator;
        final TextView title;
        final LinearLayout slots;
        final Button change;
        final Map<String, Long> lastBackgroundStepMs = new HashMap<>();
        final Map<String, Boolean> backgroundBrainThinking = new HashMap<>();
        final Map<String, DungeonProgressMonitor.Snapshot> backgroundProgress = new HashMap<>();
        Handler handler;
        Runnable task;

        BridgeState(
                DungeonRosterStore store,
                NpcAiStaminaStore stamina,
                SecureApiKeyStore apiKeyStore,
                ModelSettingsStore modelSettings,
                DungeonBrainRuntime brainRuntime,
                DungeonNpcStateCoordinator npcStateCoordinator,
                TextView title,
                LinearLayout slots,
                Button change
        ) {
            this.store = store;
            this.stamina = stamina;
            this.apiKeyStore = apiKeyStore;
            this.modelSettings = modelSettings;
            this.brainRuntime = brainRuntime;
            this.npcStateCoordinator = npcStateCoordinator;
            this.title = title;
            this.slots = slots;
            this.change = change;
        }
    }
}
