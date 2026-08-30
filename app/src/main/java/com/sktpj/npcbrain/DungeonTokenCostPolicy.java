package com.sktpj.npcbrain;

final class DungeonTokenCostPolicy {
    static final double DEFAULT_BUDGET_JPY = 10.0;
    // Compatibility alias for older tests/UI. New budget checks use the NPC-specific limit.
    static final double MAX_BUDGET_JPY = DEFAULT_BUDGET_JPY;
    static final double INPUT_USD_PER_MILLION = 0.20;
    static final double CACHED_INPUT_USD_PER_MILLION = 0.02;
    static final double OUTPUT_USD_PER_MILLION = 1.20;
    static final double USD_TO_JPY = 158.975;

    private DungeonTokenCostPolicy() {
    }

    static double costJpy(long inputTokens, long cachedInputTokens, long outputTokens) {
        long input = Math.max(0L, inputTokens);
        long cached = Math.max(0L, Math.min(input, cachedInputTokens));
        long uncached = input - cached;
        long output = Math.max(0L, outputTokens);
        double usd = (uncached * INPUT_USD_PER_MILLION
                + cached * CACHED_INPUT_USD_PER_MILLION
                + output * OUTPUT_USD_PER_MILLION) / 1_000_000.0;
        return Math.max(0.0, usd * USD_TO_JPY);
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
