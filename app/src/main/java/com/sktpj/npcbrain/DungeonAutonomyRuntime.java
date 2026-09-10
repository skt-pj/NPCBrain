package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class DungeonAutonomyRuntime {
    private static final String PREFS = "npcbrain_dungeon_autonomy_v043";
    private static final String ENTER_DUNGEON = "ENTER_DUNGEON";
    private static final String KEEP_CURRENT_ACTIVITY = "KEEP_CURRENT_ACTIVITY";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final NpcRegistryStore registryStore;
    private final DungeonPresenceStore presenceStore;
    private final DungeonStore dungeonStore;
    private final NpcBrainSessionFactory brainFactory;
    private final NpcWorldStateCoordinator worldState;

    DungeonAutonomyRuntime(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registryStore = new NpcRegistryStore(appContext);
        presenceStore = new DungeonPresenceStore(appContext);
        dungeonStore = new DungeonStore(appContext);
        brainFactory = new NpcBrainSessionFactory(appContext);
        worldState = new NpcWorldStateCoordinator(appContext);
    }

    synchronized int evaluateAndEnter(long nowMs) {
        String apiKey = new SecureApiKeyStore(appContext).load();
        String reasoning = new ModelSettingsStore(appContext).reasoningEffort();
        return evaluateAndEnter(nowMs, apiKey == null ? "" : apiKey.trim(), reasoning);
    }

    synchronized int evaluateAndEnter(long nowMs, String apiKey, String reasoningEffort) {
        int entered = 0;
        long bucket = DungeonAutonomyPolicy.dayBucket(nowMs);
        for (String npcId : registryStore.activeNpcIds()) {
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
            if (character.isDead() || presenceStore.isPresent(npcId)) continue;
            if (preferences.getLong(dayKey(npcId), -1L) == bucket) continue;
            preferences.edit().putLong(dayKey(npcId), bucket).commit();
            if (!NpcInferenceAccess.canRun(appContext, npcId, apiKey)) continue;

            BrainEngine.Decision decision;
            try {
                decision = brainFactory.create(npcId, apiKey, reasoningEffort)
                        .thinkDecision(buildPrompt(npcId, nowMs), null, true);
            } catch (Exception inferenceFailure) {
                continue;
            }
            if (!wantsDungeon(decision)) continue;

            DungeonState state = dungeonStore.loadRaw(npcId);
            if (state == null) {
                long seed = System.nanoTime() ^ nowMs ^ ((long) npcId.hashCode() << 17);
                state = DungeonGenerator.generate(seed, 1);
                state.lastAction = "自分の意思で単独ダンジョン探索を開始";
                dungeonStore.save(npcId, state);
            }
            presenceStore.setPresent(npcId, true);
            entered++;
        }
        return entered;
    }

    /** Compatibility name retained for existing foreground/job call sites. */
    synchronized int evaluateAndJoin(long nowMs) {
        return evaluateAndEnter(nowMs);
    }

    synchronized int evaluateAndJoin(long nowMs, String apiKey, String reasoningEffort) {
        return evaluateAndEnter(nowMs, apiKey, reasoningEffort);
    }

    private String buildPrompt(String npcId, long nowMs) {
        JSONObject runtime = new JSONObject();
        try {
            runtime.put("mode", "world_action_opportunity");
            runtime.put("character_id", NpcId.of(npcId).value());
            runtime.put("now_ms", nowMs);
            runtime.put("world_snapshot", worldState.snapshot(npcId, nowMs).toJson());
            runtime.put("candidate_actions", new JSONArray()
                    .put(ENTER_DUNGEON)
                    .put(KEEP_CURRENT_ACTIVITY));
            runtime.put("response_contract", new JSONObject()
                    .put("format", "JSON")
                    .put("npc_action", ENTER_DUNGEON + " or " + KEEP_CURRENT_ACTIVITY));
        } catch (Exception ignored) {
        }
        return "character_id=" + NpcId.of(npcId).value() + "\n"
                + "This is an autonomous in-world action opportunity, not a user request. Runtime context is JSON.\n"
                + "Runtime JSON:\n" + runtime + "\n\n"
                + "Use the same personality, memories, needs, relationships, and grounded shared-world state used for every other decision. "
                + "There is no special consent gate and no Big Five threshold. Decide whether this NPC, on their own, goes to the dungeon now. "
                + "Choose exactly one candidate action. Return the chosen candidate token exactly in npc_action. "
                + "Do not fabricate a reason or an off-screen event. The final response must remain the BrainEngine JSON contract.";
    }

    static boolean wantsDungeon(BrainEngine.Decision decision) {
        if (decision == null) return false;
        return ENTER_DUNGEON.equalsIgnoreCase(decision.action().trim());
    }

    private static String dayKey(String npcId) {
        return "last_day_" + NpcId.of(npcId).value();
    }
}
