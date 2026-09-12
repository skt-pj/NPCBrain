package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Autonomous dungeon-entry opportunity backed by the canonical world.
 *
 * The Brain decides; this class only offers grounded candidate actions. A successful ENTER_DUNGEON
 * becomes a SET_DUNGEON_PRESENCE command. It never writes legacy dungeon persistence directly.
 */
final class CanonicalDungeonAutonomyV211 {
    private static final String PREFS = "npcbrain_canonical_dungeon_autonomy_v211";
    private static final String ENTER_DUNGEON = "ENTER_DUNGEON";
    private static final String KEEP_CURRENT_ACTIVITY = "KEEP_CURRENT_ACTIVITY";

    private final Context appContext;
    private final SharedPreferences checkpoints;
    private final NpcRegistryStore registry;
    private final NpcBrainCoordinator brain;
    private final WorldKernelV210 kernel;
    private final WorldQueryServiceV210 query;

    CanonicalDungeonAutonomyV211(Context context) {
        appContext = context.getApplicationContext();
        checkpoints = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registry = new NpcRegistryStore(appContext);
        brain = new NpcBrainCoordinator(appContext);
        kernel = WorldKernelV210.get(appContext);
        new LegacyWorldImporterV210(appContext, kernel.database()).importIfNeeded();
        query = new WorldQueryServiceV210(kernel.database());
    }

    int evaluateDue(String apiKey, String reasoningEffort, long nowMs) {
        int entered = 0;
        long day = DungeonAutonomyPolicy.dayBucket(nowMs);
        for (String npcId : registry.activeNpcIds()) {
            JSONObject snapshot = query.snapshot(npcId);
            JSONObject npc = snapshot.optJSONObject("npc");
            if (npc == null
                    || !npc.optBoolean("active", true)
                    || npc.optBoolean("dead", false)
                    || npc.optBoolean("dungeon_present", false)) {
                continue;
            }
            String checkpointKey = "day_" + NpcId.of(npcId).value();
            if (checkpoints.getLong(checkpointKey, -1L) == day) continue;
            // Do not consume the daily opportunity when inference cannot run at all. Once an actual
            // inference attempt starts, checkpoint it before the request to avoid a tight retry loop.
            if (!NpcInferenceAccess.canRun(appContext, npcId, apiKey)) continue;
            checkpoints.edit().putLong(checkpointKey, day).commit();

            NpcBrainCoordinator.BrainDecisionEnvelope envelope;
            try {
                envelope = brain.request(new NpcBrainCoordinator.BrainRequest(
                        npcId,
                        "world_action_opportunity",
                        prompt(npcId, snapshot, nowMs),
                        apiKey,
                        reasoningEffort,
                        null,
                        null,
                        false));
            } catch (Exception ignored) {
                continue;
            }
            if (!wantsDungeon(envelope.decision)) continue;

            JSONObject payload = new JSONObject();
            try {
                payload.put("present", true);
                payload.put("decision_id", envelope.decisionId);
                payload.put("basis_revision", envelope.basisRevision);
                payload.put("basis_state_version", envelope.basisStateVersion);
                payload.put("observed_wall_time_ms", Math.max(0L, nowMs));
            } catch (Exception ignored) {
            }
            WorldCommitResultV210 result = kernel.commit(WorldCommandV210.of(
                    WorldCommandV210.SET_DUNGEON_PRESENCE,
                    nowMs,
                    npcId,
                    "autonomous-dungeon-entry:" + npcId + ":" + day,
                    payload));
            if (result.committed || result.duplicate) entered++;
        }
        return entered;
    }

    private static String prompt(String npcId, JSONObject snapshot, long nowMs) {
        JSONObject runtime = new JSONObject();
        try {
            runtime.put("mode", "world_action_opportunity");
            runtime.put("character_id", NpcId.of(npcId).value());
            runtime.put("now_ms", Math.max(0L, nowMs));
            runtime.put("canonical_world_snapshot", snapshot == null
                    ? new JSONObject() : new JSONObject(snapshot.toString()));
            runtime.put("candidate_actions", new JSONArray()
                    .put(ENTER_DUNGEON)
                    .put(KEEP_CURRENT_ACTIVITY));
        } catch (Exception ignored) {
        }
        return "Autonomous in-world action opportunity. Runtime JSON:\n" + runtime + "\n\n"
                + "This is not a user request. Decide from the NPC's own personality, goals, memory, "
                + "relationships and current grounded world state. Do not assume the NPC wants danger. "
                + "There is no application-side consent/personality threshold. Choose exactly one "
                + "candidate action and return it in npc_action. Do not invent an off-screen event.";
    }

    static boolean wantsDungeon(BrainEngine.Decision decision) {
        return decision != null && ENTER_DUNGEON.equalsIgnoreCase(decision.action().trim());
    }
}
