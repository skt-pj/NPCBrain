package com.sktpj.npcbrain;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Keeps the legacy DungeonActivity as an observation/control surface.
 * Its private turn loop is permanently paused; NpcWorldRuntimeV200 owns state advancement.
 */
final class DungeonWorldObserverBridge {
    private static final long REFRESH_MS = 150L;
    private static final WeakHashMap<DungeonActivity, State> STATES = new WeakHashMap<>();

    private DungeonWorldObserverBridge() {}

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        State existing = STATES.get(activity);
        if (existing != null) {
            existing.blockLegacyTurnLoop();
            existing.syncNow();
            return;
        }

        State state = new State(activity);
        STATES.put(activity, state);
        state.blockLegacyTurnLoop();
        state.guardAutomaticResumeBrain();
        state.bindControls();
        state.syncNow();
        state.handler.post(state.refreshTask);
    }

    /** API 29+ lifecycle pre-callback uses this to prevent Activity.onPause from persisting a stale mirror. */
    static synchronized void beforeActivityPause(DungeonActivity activity) {
        State state = STATES.get(activity);
        if (state == null) return;
        state.blockLegacyTurnLoop();
        setField(activity, "state", null);
    }

    static synchronized void afterActivityPause(DungeonActivity activity) {
        State state = STATES.get(activity);
        if (state == null) return;
        state.syncNow();
    }

    private static final class State {
        final DungeonActivity activity;
        final DungeonStore dungeonStore;
        final DungeonObjectiveStore objectiveStore;
        final DungeonMindStore mindStore;
        final DungeonSimulationControlStore controls;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable refreshTask;
        boolean resumeBrainGuard;

        State(DungeonActivity activity) {
            this.activity = activity;
            dungeonStore = new DungeonStore(activity);
            objectiveStore = new DungeonObjectiveStore(activity);
            mindStore = new DungeonMindStore(activity);
            controls = new DungeonSimulationControlStore(activity);
            refreshTask = new Runnable() {
                @Override public void run() {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    blockLegacyTurnLoop();
                    syncNow();
                    handler.postDelayed(this, REFRESH_MS);
                }
            };
        }

        void blockLegacyTurnLoop() {
            setBoolean(activity, "paused", true);
        }

        void guardAutomaticResumeBrain() {
            if (booleanField(activity, "brainThinking")) return;
            resumeBrainGuard = true;
            setBoolean(activity, "brainThinking", true);
            handler.post(() -> {
                if (!resumeBrainGuard || activity.isFinishing() || activity.isDestroyed()) return;
                resumeBrainGuard = false;
                setBoolean(activity, "brainThinking", false);
                syncNow();
            });
        }

        void bindControls() {
            Button pause = buttonField(activity, "pauseButton");
            if (pause != null) {
                pause.setOnClickListener(v -> {
                    controls.setPaused(!controls.paused());
                    renderObserved();
                });
            }
            Button speed = buttonField(activity, "speedButton");
            if (speed != null) {
                speed.setOnClickListener(v -> {
                    int next = (controls.speedIndex() + 1) % 3;
                    controls.setSpeedIndex(next);
                    setInt(activity, "speedIndex", next);
                    renderObserved();
                });
            }
        }

        void syncNow() {
            if (resumeBrainGuard) return;
            String npcId = stringField(activity, "selectedNpcId");
            if (npcId.isEmpty()) return;
            if (!booleanField(activity, "brainThinking")) {
                DungeonState persisted = dungeonStore.load(npcId);
                if (persisted != null) {
                    DungeonObjective objective = objectiveStore.load(npcId);
                    if (objective == null) objective = DungeonObjective.none();
                    DungeonMindStore.Snapshot mind = mindStore.load(npcId);
                    DungeonPlan plan = mind == null ? null : mind.plan;
                    if (plan != null && !plan.matches(objective)) plan = null;
                    DungeonPersonalityPolicy.Traits traits = traitsFor(activity, npcId);
                    DungeonIntent intent = DungeonWorldProgressRuntime.worldTurnIntent(persisted, traits, mind);
                    setField(activity, "state", persisted);
                    setField(activity, "objective", objective);
                    setField(activity, "mindSnapshot", mind);
                    setField(activity, "currentPlan", plan);
                    setField(activity, "currentIntent", intent);
                    if (mind != null) {
                        setField(activity, "brainState", mind.brainState);
                        setField(activity, "brainError", mind.error == null ? "" : mind.error);
                    }
                }
            }
            renderObserved();
        }

        void renderObserved() {
            int speedIndex = controls.speedIndex();
            setInt(activity, "speedIndex", speedIndex);
            // render() reads the legacy fields. Temporarily mirror the world-owned pause state only
            // while rendering, then restore true so the Activity's own turnTask can never advance.
            setBoolean(activity, "paused", controls.paused());
            invoke(activity, "render");
            setBoolean(activity, "paused", true);
            Button pause = buttonField(activity, "pauseButton");
            if (pause != null) pause.setText(controls.paused() ? "再開" : "一時停止");
            Button speed = buttonField(activity, "speedButton");
            if (speed != null) {
                String[] labels = {"0.5×", "1×", "2×"};
                speed.setText("速度 " + labels[speedIndex]);
            }
        }
    }

    private static DungeonPersonalityPolicy.Traits traitsFor(DungeonActivity activity, String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(activity, npcId));
        return new DungeonPersonalityPolicy.Traits(
                character.traitPercent(CharacterStateStore.extraversionKey()),
                character.traitPercent(CharacterStateStore.neuroticismKey()),
                character.traitPercent(CharacterStateStore.agreeablenessKey()),
                character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                character.traitPercent(CharacterStateStore.opennessKey()));
    }

    private static Button buttonField(Object target, String name) {
        Object value = fieldValue(target, name);
        return value instanceof Button ? (Button) value : null;
    }

    private static boolean booleanField(Object target, String name) {
        Object value = fieldValue(target, name);
        return value instanceof Boolean && (Boolean) value;
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

    private static void setBoolean(Object target, String name, boolean value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (Exception ignored) {
        }
    }

    private static void setInt(Object target, String name, int value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (Exception ignored) {
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

    private static void invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (Exception ignored) {
        }
    }
}
