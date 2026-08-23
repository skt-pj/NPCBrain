package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** NPC-wide AI usage budget shared by conversation, spontaneous messaging and dungeon cognition. */
final class NpcAiStaminaStore {
    // Keep the v0.4.17 preference name so existing dungeon/conversation usage survives upgrades.
    private static final String PREFS = "npcbrain_dungeon_ai_stamina_v1";
    private static final String MONTH_SUFFIX = "usage_month_index";

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

    NpcAiStaminaStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized Snapshot snapshot(String npcId) {
        String prefix = prefix(npcId);
        ensureCurrentPeriod(prefix, currentMonthIndex());
        return readSnapshot(prefix);
    }

    synchronized Snapshot recordUsage(String npcId, OpenAiClient.Usage usage) {
        String prefix = prefix(npcId);
        ensureCurrentPeriod(prefix, currentMonthIndex());
        Snapshot before = readSnapshot(prefix);
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
        preferences.edit()
                .putLong(prefix + "spent_bits", Double.doubleToLongBits(spent))
                .putLong(prefix + "input_tokens", input)
                .putLong(prefix + "cached_input_tokens", cached)
                .putLong(prefix + "output_tokens", output)
                .putLong(prefix + "total_tokens", total)
                .commit();
        return new Snapshot(spent, input, cached, output, total);
    }

    private Snapshot readSnapshot(String prefix) {
        return new Snapshot(
                Double.longBitsToDouble(preferences.getLong(
                        prefix + "spent_bits", Double.doubleToLongBits(0.0))),
                preferences.getLong(prefix + "input_tokens", 0L),
                preferences.getLong(prefix + "cached_input_tokens", 0L),
                preferences.getLong(prefix + "output_tokens", 0L),
                preferences.getLong(prefix + "total_tokens", 0L));
    }

    /**
     * Migration keeps all existing v0.4.17/v0.4.18 counters and only stamps the current month.
     * A reset happens exactly once when the local calendar month moves forward. Moving the device
     * clock backwards never resets or restores usage.
     */
    private void ensureCurrentPeriod(String prefix, int currentMonthIndex) {
        String monthKey = prefix + MONTH_SUFFIX;
        boolean hasStoredMonth = preferences.contains(monthKey);
        int storedMonthIndex = preferences.getInt(monthKey, currentMonthIndex);
        AiUsagePeriodPolicy.Action action = AiUsagePeriodPolicy.action(
                hasStoredMonth,
                storedMonthIndex,
                currentMonthIndex);
        if (action == AiUsagePeriodPolicy.Action.KEEP) return;

        SharedPreferences.Editor editor = preferences.edit();
        if (action == AiUsagePeriodPolicy.Action.RESET) {
            editor.putLong(prefix + "spent_bits", Double.doubleToLongBits(0.0));
            editor.putLong(prefix + "input_tokens", 0L);
            editor.putLong(prefix + "cached_input_tokens", 0L);
            editor.putLong(prefix + "output_tokens", 0L);
            editor.putLong(prefix + "total_tokens", 0L);
        }
        editor.putInt(monthKey, currentMonthIndex).commit();
    }

    static int currentMonthIndex() {
        Calendar calendar = Calendar.getInstance();
        return AiUsagePeriodPolicy.monthIndex(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH));
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