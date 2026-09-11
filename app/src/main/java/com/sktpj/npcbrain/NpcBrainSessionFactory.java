package com.sktpj.npcbrain;

import android.content.Context;

/** Builds every autonomous BrainEngine from the same NPC-scoped stores and canonical world view. */
final class NpcBrainSessionFactory {
    private final Context appContext;
    private final WorldQueryServiceV210 worldQuery;

    NpcBrainSessionFactory(Context context) {
        appContext = context.getApplicationContext();
        WorldKernelV210 kernel = WorldKernelV210.get(appContext);
        new LegacyWorldImporterV210(appContext, kernel.database()).importIfNeeded();
        worldQuery = new WorldQueryServiceV210(kernel.database());
    }

    BrainEngine create(String npcId, String apiKey, String reasoningEffort) {
        String id = NpcId.of(npcId).value();
        Context storage = NpcContexts.storage(appContext, id);
        return new BrainEngine(
                new OpenAiClient(appContext, apiKey, reasoningEffort),
                new MemoryStore(storage),
                new CharacterStateStore(storage));
    }

    String promptWithWorldState(String npcId, String mode, long nowMs, String instruction) {
        String id = NpcId.of(npcId).value();
        org.json.JSONObject runtime = new org.json.JSONObject();
        try {
            runtime.put("mode", mode == null ? "" : mode);
            runtime.put("character_id", id);
            runtime.put("observed_wall_time_ms", nowMs);
            runtime.put("world_snapshot", worldQuery.snapshot(id));
            runtime.put("response_contract", new org.json.JSONObject()
                    .put("format", "JSON")
                    .put("npc_action", "A concrete in-world action owned by this NPC."));
        } catch (Exception ignored) {
        }
        return "Runtime context is JSON.\nRuntime JSON:\n" + runtime + "\n\n"
                + (instruction == null ? "" : instruction.trim());
    }
}
