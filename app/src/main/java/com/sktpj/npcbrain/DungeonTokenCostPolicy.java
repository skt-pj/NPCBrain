package com.sktpj.npcbrain;

final class DungeonTokenCostPolicy {
    static final double DEFAULT_BUDGET_JPY = 10.0;
    // Compatibility alias for older tests/UI. New budget checks use the NPC-specific limit.
    static final double MAX_BUDGET_JPY = DEFAULT_BUDGET_JPY;
    static final double USD_TO_JPY = ApiPricingPolicy.USD_TO_JPY;

    private DungeonTokenCostPolicy() {
    }

    /**
     * Legacy GPT-5.6 Luna compatibility entry. Production billing resolves the actual model
     * through ApiPricingPolicy before recording usage.
     */
    static double costJpy(long inputTokens, long cachedInputTokens, long outputTokens) {
        return ApiPricingPolicy.costJpy(
                ApiPricingPolicy.MODEL_GPT56_LUNA,
                inputTokens,
                cachedInputTokens,
                0L,
                outputTokens);
    }

    static double remainingJpy(double spentJpy) {
        return remainingJpy(spentJpy, DEFAULT_BUDGET_JPY);
    }

    static double remainingJpy(double spentJpy, double budgetLimitJpy) {
        double limit = NpcAiBudgetPolicy.normalizeBudgetLimitJpy(budgetLimitJpy);
        return Math.max(0.0, Math.min(limit, limit - Math.max(0.0, spentJpy)));
    }

    static int remainingPercent(double spentJpy) {
        return remainingPercent(spentJpy, DEFAULT_BUDGET_JPY);
    }

    static int remainingPercent(double spentJpy, double budgetLimitJpy) {
        double limit = NpcAiBudgetPolicy.normalizeBudgetLimitJpy(budgetLimitJpy);
        if (limit <= 0.0) return 0;
        return (int) Math.round(remainingJpy(spentJpy, limit) / limit * 100.0);
    }
}
