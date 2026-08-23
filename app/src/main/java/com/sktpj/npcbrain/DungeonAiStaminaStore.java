package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class DungeonAiStaminaStore {
    private static final String PREFS = "npcbrain_dungeon_ai_stamina_v1";

    static final class Snapshot {
        final double spentJpy;
        final double remainingJpy;
        final int remainingPercent;
        final long inputTokens;
        final long cachedInputTokens;
        final long outputTokens;
        final long totalTokens;

        Snapshot(
                double spentJpy,
                long inputTokens,
                long cachedInputTokens,
                long outputTokens,
                long totalTokens
        ) {
            this.spentJpy = Math.max(0.0, spentJpy);
            this.remainingJpy = DungeonTokenCostPolicy.remainingJpy(this.spentJpy);
            this.remainingPercent = DungeonTokenCostPolicy.remainingPercent(this.spentJpy);
            this.inputTokens = Math.max(0L, inputTokens);
            this.cachedInputTokens = Math.max(0L, cachedInputTokens);
            this.outputTokens = Math.max(0L, outputTokens);
            this.totalTokens = Math.max(0L, totalTokens);
        }

        boolean exhausted() {
            return remainingJpy <= 0.000001;
        }
    }

    private final SharedPreferences preferences;

    DungeonAiStaminaStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized Snapshot snapshot(String npcId) {
        String prefix = prefix(npcId);
        return new Snapshot(
                Double.longBitsToDouble(preferences.getLong(prefix + "spent_bits", Double.doubleToLongBits(0.0))),
                preferences.getLong(prefix + "input_tokens", 0L),
                preferences.getLong(prefix + "cached_input_tokens", 0L),
                preferences.getLong(prefix + "output_tokens", 0L),
                preferences.getLong(prefix + "total_tokens", 0L));
    }

    synchronized Snapshot recordUsage(String npcId, OpenAiClient.Usage usage) {
        Snapshot before = snapshot(npcId);
        if (usage == null) return before;
        double added = DungeonTokenCostPolicy.costJpy(
                usage.inputTokens,
                usage.cachedInputTokens,
                usage.outputTokens);
        double spent = before.spentJpy + added;
        long input = safeAdd(before.inputTokens, usage.inputTokens);
        long cached = safeAdd(before.cachedInputTokens, usage.cachedInputTokens);
        long output = safeAdd(before.outputTokens, usage.outputTokens);
        long total = safeAdd(before.totalTokens, usage.totalTokens);
        String prefix = prefix(npcId);
        preferences.edit()
                .putLong(prefix + "spent_bits", Double.doubleToLongBits(spent))
                .putLong(prefix + "input_tokens", input)
                .putLong(prefix + "cached_input_tokens", cached)
                .putLong(prefix + "output_tokens", output)
                .putLong(prefix + "total_tokens", total)
                .commit();
        return new Snapshot(spent, input, cached, output, total);
    }

    private static long safeAdd(long a, long b) {
        long safeB = Math.max(0L, b);
        if (Long.MAX_VALUE - Math.max(0L, a) < safeB) return Long.MAX_VALUE;
        return Math.max(0L, a) + safeB;
    }

    private static String prefix(String npcId) {
        return NpcId.of(npcId).value() + "_";
    }
}
