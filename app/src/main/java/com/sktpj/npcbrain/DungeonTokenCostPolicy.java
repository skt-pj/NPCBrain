package com.sktpj.npcbrain;

final class DungeonTokenCostPolicy {
    static final double MAX_BUDGET_JPY = 10.0;
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
        return Math.max(0.0, Math.min(MAX_BUDGET_JPY, MAX_BUDGET_JPY - Math.max(0.0, spentJpy)));
    }

    static int remainingPercent(double spentJpy) {
        return (int) Math.round(remainingJpy(spentJpy) / MAX_BUDGET_JPY * 100.0);
    }
}
