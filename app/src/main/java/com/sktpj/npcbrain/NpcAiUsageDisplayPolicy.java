package com.sktpj.npcbrain;

import java.util.List;
import java.util.Locale;

/** Pure display/aggregation policy over the canonical NpcAiStaminaStore snapshots. */
final class NpcAiUsageDisplayPolicy {
    static final class Aggregate {
        final int npcCount;
        final double spentJpy;
        final double remainingJpy;
        final double budgetJpy;
        final long inputTokens;
        final long cachedInputTokens;
        final long outputTokens;
        final long totalTokens;

        Aggregate(
                int npcCount,
                double spentJpy,
                double remainingJpy,
                double budgetJpy,
                long inputTokens,
                long cachedInputTokens,
                long outputTokens,
                long totalTokens
        ) {
            this.npcCount = Math.max(0, npcCount);
            this.spentJpy = Math.max(0.0, spentJpy);
            this.remainingJpy = Math.max(0.0, remainingJpy);
            this.budgetJpy = Math.max(0.0, budgetJpy);
            this.inputTokens = Math.max(0L, inputTokens);
            this.cachedInputTokens = Math.max(0L, cachedInputTokens);
            this.outputTokens = Math.max(0L, outputTokens);
            this.totalTokens = Math.max(0L, totalTokens);
        }
    }

    private NpcAiUsageDisplayPolicy() {
    }

    static String formatSpentJpy(double value) {
        double safe = Math.max(0.0, value);
        if (safe == 0.0) return "¥0.00";
        if (safe < 0.000001) return String.format(Locale.JAPAN, "¥%.8f", safe);
        if (safe < 0.01) return String.format(Locale.JAPAN, "¥%.6f", safe);
        if (safe < 1.0) return String.format(Locale.JAPAN, "¥%.4f", safe);
        return String.format(Locale.JAPAN, "¥%.2f", safe);
    }

    static String formatRemainingJpy(double value) {
        double safe = Math.max(0.0, value);
        if (safe < 1.0 && safe > 0.0) return String.format(Locale.JAPAN, "¥%.4f", safe);
        return String.format(Locale.JAPAN, "¥%.2f", safe);
    }

    static Aggregate aggregate(List<NpcAiStaminaStore.Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new Aggregate(0, 0.0, 0.0, 0.0, 0L, 0L, 0L, 0L);
        }
        double spent = 0.0;
        double remaining = 0.0;
        double budget = 0.0;
        long input = 0L;
        long cached = 0L;
        long output = 0L;
        long total = 0L;
        int count = 0;
        for (NpcAiStaminaStore.Snapshot snapshot : snapshots) {
            if (snapshot == null) continue;
            count++;
            spent += snapshot.spentJpy;
            remaining += snapshot.remainingJpy;
            budget += snapshot.budgetLimitJpy;
            input = safeAdd(input, snapshot.inputTokens);
            cached = safeAdd(cached, snapshot.cachedInputTokens);
            output = safeAdd(output, snapshot.outputTokens);
            total = safeAdd(total, snapshot.totalTokens);
        }
        return new Aggregate(
                count,
                spent,
                remaining,
                budget,
                input,
                cached,
                output,
                total);
    }

    private static long safeAdd(long a, long b) {
        long left = Math.max(0L, a);
        long right = Math.max(0L, b);
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }
}
