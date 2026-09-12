package com.sktpj.npcbrain;

import org.json.JSONObject;

import java.util.UUID;

/** Single orchestration entry for autonomous NPC cognition against one frozen canonical snapshot. */
final class NpcBrainCoordinator {
    static final class BrainRequest {
        final String npcId;
        final String mode;
        final String prompt;
        final String apiKey;
        final String reasoningEffort;
        final OpenAiClient clientOverride;
        final BrainEngine.ProgressListener listener;
        final boolean recordMemory;

        BrainRequest(
                String npcId,
                String mode,
                String prompt,
                String apiKey,
                String reasoningEffort,
                OpenAiClient clientOverride,
                BrainEngine.ProgressListener listener,
                boolean recordMemory
        ) {
            this.npcId = NpcId.of(npcId).value();
            this.mode = mode == null ? "" : mode.trim();
            this.prompt = prompt == null ? "" : prompt.trim();
            this.apiKey = apiKey == null ? "" : apiKey;
            this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort;
            this.clientOverride = clientOverride;
            this.listener = listener;
            this.recordMemory = recordMemory;
        }
    }

    static final class BrainDecisionEnvelope {
        final String npcId;
        final long basisRevision;
        final long basisStateVersion;
        final String decisionId;
        final BrainEngine.Decision decision;
        final JSONObject worldSnapshot;

        BrainDecisionEnvelope(
                String npcId,
                long basisRevision,
                long basisStateVersion,
                String decisionId,
                BrainEngine.Decision decision,
                JSONObject worldSnapshot
        ) {
            this.npcId = npcId;
            this.basisRevision = basisRevision;
            this.basisStateVersion = basisStateVersion;
            this.decisionId = decisionId;
            this.decision = decision;
            this.worldSnapshot = copy(worldSnapshot);
        }
    }

    private final NpcBrainSessionFactory sessionFactory;

    NpcBrainCoordinator(android.content.Context context) {
        sessionFactory = new NpcBrainSessionFactory(context);
    }

    BrainDecisionEnvelope request(BrainRequest request) throws Exception {
        if (request == null) throw new IllegalArgumentException("BrainRequest is required");
        JSONObject frozen = sessionFactory.worldSnapshot(request.npcId);
        long basisRevision = frozen.optLong("revision", 0L);
        JSONObject npc = frozen.optJSONObject("npc");
        long basisStateVersion = npc == null ? 0L : npc.optLong("state_version", 0L);
        String prompt = sessionFactory.promptWithFrozenWorldState(
                request.npcId,
                request.mode,
                frozen,
                request.prompt);
        BrainEngine engine = request.clientOverride == null
                ? sessionFactory.create(request.npcId, request.apiKey, request.reasoningEffort)
                : sessionFactory.create(request.npcId, request.clientOverride);
        BrainEngine.Decision decision = engine.thinkDecision(
                prompt,
                request.listener,
                request.recordMemory);
        return new BrainDecisionEnvelope(
                request.npcId,
                basisRevision,
                basisStateVersion,
                UUID.randomUUID().toString(),
                decision,
                frozen);
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
