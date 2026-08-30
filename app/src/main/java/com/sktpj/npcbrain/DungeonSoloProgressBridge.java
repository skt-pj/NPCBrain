package com.sktpj.npcbrain;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps solo explorers moving while the party screen exists, including behind the 8-way monitor. */
final class DungeonSoloProgressBridge {
    private static final long REFRESH_MS = 300L;
    private static final long[] TURN_INTERVALS = {1100L, 650L, 350L};
    private static final WeakHashMap<DungeonActivity, State> STATES = new WeakHashMap<>();

    private DungeonSoloProgressBridge() {
    }

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (STATES.containsKey(activity)) return;
        State state = new State(activity);
        STATES.put(activity, state);
        state.handler.post(state.task);
    }

    private static final class State {
        final DungeonActivity activity;
        final Handler handler = new Handler(Looper.getMainLooper());
        final DungeonPresenceStore presence;
        final DungeonRosterStore roster;
        final DungeonStore dungeon;
        final DungeonEconomyRuntime economy;
        final Map<String, Long> lastStepMs = new HashMap<>();

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                tick();
                handler.postDelayed(this, REFRESH_MS);
            }
        };

        State(DungeonActivity activity) {
            this.activity = activity;
            presence = new DungeonPresenceStore(activity);
            roster = new DungeonRosterStore(activity);
            dungeon = new DungeonStore(activity);
            economy = new DungeonEconomyRuntime(activity);
        }

        void tick() {
            List<String> present = presence.activePresentNpcIds();
            List<String> party = roster.activeNpcIds();
            String selected = selectedNpcId(activity);
            boolean activityRunning = booleanField(activity, "running");
            boolean partyPaused = booleanField(activity, "paused");
            long now = System.currentTimeMillis();
            long interval = TURN_INTERVALS[Math.max(0, Math.min(2, intField(activity, "speedIndex", 1)))];

            for (String npcId : present) {
                boolean inParty = party.contains(npcId);
                if (inParty) {
                    // Foreground selected actor is owned by DungeonActivity. Other party members are
                    // owned by DungeonRosterBridge. When the Activity is behind the 8-way monitor,
                    // only its selected member needs this bridge to keep the whole party moving.
                    if (activityRunning || !npcId.equals(selected) || partyPaused) continue;
                }
                long last = lastStepMs.containsKey(npcId) ? lastStepMs.get(npcId) : 0L;
                if (now - last < interval) continue;
                step(npcId, now);
                lastStepMs.put(npcId, now);
            }
        }

        void step(String npcId, long now) {
            CharacterStateStore character =
                    new CharacterStateStore(NpcContexts.storage(activity, npcId));
            if (character.isDead()) {
                presence.setPresent(npcId, false);
                return;
            }

            DungeonState state = dungeon.load(npcId);
            if (state == null) {
                long seed = System.nanoTime()
                        ^ now
                        ^ ((long) npcId.hashCode() << 17);
                state = DungeonGenerator.generate(seed, 1);
                state.lastAction = "単独探索を開始";
                dungeon.save(npcId, state);
            }
            if (state.hp <= 0) {
                presence.setPresent(npcId, false);
                return;
            }

            DungeonObjective objective = new DungeonObjectiveStore(activity).load(npcId);
            if (objective == null) objective = DungeonObjective.none();
            if (objective.isActive() && objective.isComplete(state.floor)) return;

            DungeonPersonalityPolicy.Traits traits = new DungeonPersonalityPolicy.Traits(
                    character.traitPercent(CharacterStateStore.extraversionKey()),
                    character.traitPercent(CharacterStateStore.neuroticismKey()),
                    character.traitPercent(CharacterStateStore.agreeablenessKey()),
                    character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                    character.traitPercent(CharacterStateStore.opennessKey()));
            DungeonMindStore.Snapshot mind = new DungeonMindStore(activity).load(npcId);
            DungeonPlan plan = mind == null ? null : mind.plan;
            if (plan == null || !plan.matches(objective)) {
                plan = DungeonPlan.local(objective, traits, state, "単独探索のローカル計画");
            }
            DungeonIntent intent = DungeonRosterBridge.backgroundTurnIntent(state, traits, mind);
            DungeonStepResult result = DungeonEngine.stepDetailed(state, traits, intent, plan);
            DungeonState next = result == null || result.state == null ? state : result.state;
            DungeonPerception.refreshExploration(next);
            dungeon.save(npcId, next);
            economy.process(npcId, next, now);
            if (next.hp <= 0) presence.setPresent(npcId, false);
        }
    }

    private static String selectedNpcId(DungeonActivity activity) {
        try {
            Field field = DungeonActivity.class.getDeclaredField("selectedNpcId");
            field.setAccessible(true);
            Object value = field.get(activity);
            if (value instanceof String) return NpcId.of((String) value).value();
        } catch (Exception ignored) {
        }
        return "npc1";
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

    private static int intField(Object target, String name, int fallback) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
