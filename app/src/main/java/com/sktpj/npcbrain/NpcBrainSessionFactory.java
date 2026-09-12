package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONObject;

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
        return create(
                npcId,
                new OpenAiClient(appContext, apiKey, reasoningEffort));
    }

    BrainEngine create(String npcId, OpenAiClient client) {
        String id = NpcId.of(npcId).value();
        Context storage = NpcContexts.storage(appContext, id);
        return new BrainEngine(
                client,
                new MemoryStore(storage),
                new CharacterStateStore(storage));
    }

    JSONObject worldSnapshot(String npcId) {
        String id = NpcId.of(npcId).value();
        return copy(worldQuery.snapshot(id));
    }

    String promptWithWorldState(String npcId, String mode, long nowMs, String instruction) {
        return promptWithFrozenWorldState(
                npcId,
                mode,
                worldSnapshot(npcId),
                instruction);
    }

    String promptWithFrozenWorldState(
            String npcId,
            String mode,
            JSONObject frozenWorldSnapshot,
            String instruction
    ) {
        String id = NpcId.of(npcId).value();
        JSONObject runtime = new JSONObject();
        try {
            runtime.put("mode", mode == null ? "" : mode);
            runtime.put("character_id", id);
            runtime.put("world_snapshot", copy(frozenWorldSnapshot));
            runtime.put("response_contract", new JSONObject()
                    .put("format", "JSON")
                    .put("npc_action", "A concrete in-world action owned by this NPC."));
        } catch (Exception ignored) {
        }
        return "Runtime context is JSON.\nRuntime JSON:\n" + runtime + "\n\n"
                + (instruction == null ? "" : instruction.trim());
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
