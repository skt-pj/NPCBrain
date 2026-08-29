package com.sktpj.npcbrain;

/** Hard preflight budget policy for every NPC-attributed OpenAI request. */
final class NpcAiBudgetPolicy {
    static final long REQUEST_INPUT_OVERHEAD_TOKENS = 2048L;
    static final int NPC_SPECIALIST_MAX_OUTPUT_TOKENS = 512;
    static final int NPC_GLOBAL_MAX_OUTPUT_TOKENS = 1536;
    private static final double EPSILON_JPY = 0.000000001;

    private NpcAiBudgetPolicy() {
    }

    static double reservationJpy(int requestUtf8Bytes, int maxOutputTokens) {
        long conservativeInputTokens = conservativeInputTokenUpperBound(requestUtf8Bytes);
        int outputTokens = OpenAiClient.normalizeMaxOutputTokens(maxOutputTokens);
        return DungeonTokenCostPolicy.costJpy(
                conservativeInputTokens,
                0L,
                outputTokens);
    }

    static long conservativeInputTokenUpperBound(int requestUtf8Bytes) {
        long requestBytes = Math.max(0L, (long) requestUtf8Bytes);
        if (Long.MAX_VALUE - requestBytes < REQUEST_INPUT_OVERHEAD_TOKENS) {
            return Long.MAX_VALUE;
        }
        return requestBytes + REQUEST_INPUT_OVERHEAD_TOKENS;
    }

    static boolean canReserve(double spentJpy, double alreadyReservedJpy, double requestJpy) {
        double spent = Math.max(0.0, spentJpy);
        double reserved = Math.max(0.0, alreadyReservedJpy);
        double request = Math.max(0.0, requestJpy);
        return spent + reserved + request <= DungeonTokenCostPolicy.MAX_BUDGET_JPY + EPSILON_JPY;
    }

    static int npcDefaultMaxOutputTokens(String prompt) {
        return OpenAiClient.isGlobalWorkspacePrompt(prompt)
                ? NPC_GLOBAL_MAX_OUTPUT_TOKENS
                : NPC_SPECIALIST_MAX_OUTPUT_TOKENS;
    }
}
