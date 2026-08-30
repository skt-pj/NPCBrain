package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** NPC-wide AI usage budget shared by conversation, spontaneous messaging and dungeon cognition. */
final class NpcAiStaminaStore {
    // Keep the v0.4.17 preference name so existing dungeon/conversation usage survives upgrades.
    private static final String PREFS = "npcbrain_dungeon_ai_stamina_v1";
    private static final String MONTH_SUFFIX = "usage_month_index";
    private static final String BUDGET_LIMIT_BITS = "budget_limit_bits";
    private static final String LIFETIME_INITIALIZED = "lifetime_initialized";
    private static final String LIFETIME_SPENT_BITS = "lifetime_spent_bits";
    private static final String LIFETIME_INPUT_TOKENS = "lifetime_input_tokens";
    private static final String LIFETIME_CACHED_INPUT_TOKENS = "lifetime_cached_input_tokens";
    private static final String LIFETIME_OUTPUT_TOKENS = "lifetime_output_tokens";
    private static final String LIFETIME_TOTAL_TOKENS = "lifetime_total_tokens";
    private static final Object GLOBAL_BUDGET_LOCK = new Object();
    private static final Map<String, Double> RESERVED_JPY = new HashMap<>();

    static final class Snapshot {
        final double spentJpy;
        final double budgetLimitJpy;
        final double remainingJpy;
        final int remainingPercent;
        final long inputTokens;
        final long cachedInputTokens;
        final long outputTokens;
        final long totalTokens;
        final double lifetimeSpentJpy;
        final long lifetimeInputTokens;
        final long lifetimeCachedInputTokens;
        final long lifetimeOutputTokens;
        final long lifetimeTotalTokens;

        Snapshot(
                double spentJpy,
                long inputTokens,
                long cachedInputTokens,
                long outputTokens,
                long totalTokens
        ) {
            this(
                    spentJpy,
                    DungeonTokenCostPolicy.DEFAULT_BUDGET_JPY,
                    inputTokens,
                    cachedInputTokens,
                    outputTokens,
                    totalTokens,
                    spentJpy,
                    inputTokens,
                    cachedInputTokens,
                    outputTokens,
                    totalTokens);
        }

        Snapshot(
                double spentJpy,
                double budgetLimitJpy,
                long inputTokens,
                long cachedInputTokens,
                long outputTokens,
                long totalTokens,
                double lifetimeSpentJpy,
                long lifetimeInputTokens,
                long lifetimeCachedInputTokens,
                long lifetimeOutputTokens,
                long lifetimeTotalTokens
        ) {
            this.spentJpy = sanitizeMoney(spentJpy);
            this.budgetLimitJpy = NpcAiBudgetPolicy.normalizeBudgetLimitJpy(budgetLimitJpy);
            this.remainingJpy = DungeonTokenCostPolicy.remainingJpy(
                    this.spentJpy, this.budgetLimitJpy);
            this.remainingPercent = DungeonTokenCostPolicy.remainingPercent(
                    this.spentJpy, this.budgetLimitJpy);
            this.inputTokens = Math.max(0L, inputTokens);
            this.cachedInputTokens = Math.max(0L, cachedInputTokens);
            this.outputTokens = Math.max(0L, outputTokens);
            this.totalTokens = Math.max(0L, totalTokens);
            this.lifetimeSpentJpy = sanitizeMoney(lifetimeSpentJpy);
            this.lifetimeInputTokens = Math.max(0L, lifetimeInputTokens);
            this.lifetimeCachedInputTokens = Math.max(0L, lifetimeCachedInputTokens);
            this.lifetimeOutputTokens = Math.max(0L, lifetimeOutputTokens);
            this.lifetimeTotalTokens = Math.max(0L, lifetimeTotalTokens);
        }

        boolean exhausted() {
            return remainingJpy <= 0.000001;
        }
    }

    static final class Reservation {
        final String npcId;
        final double reservedJpy;
        private boolean released;

        Reservation(String npcId, double reservedJpy) {
            this.npcId = npcId;
            this.reservedJpy = Math.max(0.0, reservedJpy);
        }
    }

    private final SharedPreferences preferences;

    NpcAiStaminaStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Snapshot snapshot(String npcId) {
        synchronized (GLOBAL_BUDGET_LOCK) {
            String prefix = prefix(npcId);
            ensureLifetimeInitialized(prefix);
            ensureCurrentPeriod(prefix, currentMonthIndex());
            return readSnapshot(prefix);
        }
    }

    Snapshot recordUsage(String npcId, OpenAiClient.Usage usage) {
        synchronized (GLOBAL_BUDGET_LOCK) {
            String prefix = prefix(npcId);
            ensureLifetimeInitialized(prefix);
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

            double lifetimeSpent = before.lifetimeSpentJpy + added;
            long lifetimeInput = safeAdd(before.lifetimeInputTokens, usage.inputTokens);
            long lifetimeCached = safeAdd(
                    before.lifetimeCachedInputTokens, usage.cachedInputTokens);
            long lifetimeOutput = safeAdd(before.lifetimeOutputTokens, usage.outputTokens);
            long lifetimeTotal = safeAdd(before.lifetimeTotalTokens, usage.totalTokens);

            preferences.edit()
                    .putLong(prefix + "spent_bits", Double.doubleToLongBits(spent))
                    .putLong(prefix + "input_tokens", input)
                    .putLong(prefix + "cached_input_tokens", cached)
                    .putLong(prefix + "output_tokens", output)
                    .putLong(prefix + "total_tokens", total)
                    .putLong(prefix + LIFETIME_SPENT_BITS, Double.doubleToLongBits(lifetimeSpent))
                    .putLong(prefix + LIFETIME_INPUT_TOKENS, lifetimeInput)
                    .putLong(prefix + LIFETIME_CACHED_INPUT_TOKENS, lifetimeCached)
                    .putLong(prefix + LIFETIME_OUTPUT_TOKENS, lifetimeOutput)
                    .putLong(prefix + LIFETIME_TOTAL_TOKENS, lifetimeTotal)
                    .commit();
            return readSnapshot(prefix);
        }
    }

    Snapshot setBudgetLimitJpy(String npcId, double budgetLimitJpy) {
        synchronized (GLOBAL_BUDGET_LOCK) {
            String prefix = prefix(npcId);
            ensureLifetimeInitialized(prefix);
            ensureCurrentPeriod(prefix, currentMonthIndex());
            double normalized = NpcAiBudgetPolicy.normalizeBudgetLimitJpy(budgetLimitJpy);
            preferences.edit()
                    .putLong(prefix + BUDGET_LIMIT_BITS, Double.doubleToLongBits(normalized))
                    .commit();
            return readSnapshot(prefix);
        }
    }

    Snapshot resetCurrentBudget(String npcId) {
        synchronized (GLOBAL_BUDGET_LOCK) {
            String prefix = prefix(npcId);
            ensureLifetimeInitialized(prefix);
            preferences.edit()
                    .putLong(prefix + "spent_bits", Double.doubleToLongBits(0.0))
                    .putLong(prefix + "input_tokens", 0L)
                    .putLong(prefix + "cached_input_tokens", 0L)
                    .putLong(prefix + "output_tokens", 0L)
                    .putLong(prefix + "total_tokens", 0L)
                    .putInt(prefix + MONTH_SUFFIX, currentMonthIndex())
                    .commit();
            return readSnapshot(prefix);
        }
    }

    Reservation tryReserve(String npcId, double requestJpy) {
        String id = NpcId.of(npcId).value();
        double requested = Math.max(0.0, requestJpy);
        synchronized (GLOBAL_BUDGET_LOCK) {
            String prefix = prefix(id);
            ensureLifetimeInitialized(prefix);
            ensureCurrentPeriod(prefix, currentMonthIndex());
            Snapshot before = readSnapshot(prefix);
            double outstanding = Math.max(0.0, RESERVED_JPY.getOrDefault(id, 0.0));
            if (!NpcAiBudgetPolicy.canReserve(
                    before.spentJpy,
                    outstanding,
                    requested,
                    before.budgetLimitJpy)) {
                return null;
            }
            RESERVED_JPY.put(id, outstanding + requested);
            return new Reservation(id, requested);
        }
    }

    void releaseReservation(Reservation reservation) {
        if (reservation == null) return;
        synchronized (GLOBAL_BUDGET_LOCK) {
            if (reservation.released) return;
            reservation.released = true;
            double outstanding = Math.max(0.0,
                    RESERVED_JPY.getOrDefault(reservation.npcId, 0.0));
            double next = Math.max(0.0, outstanding - reservation.reservedJpy);
            if (next <= 0.000000001) RESERVED_JPY.remove(reservation.npcId);
            else RESERVED_JPY.put(reservation.npcId, next);
        }
    }

    private Snapshot readSnapshot(String prefix) {
        double spent = sanitizeMoney(Double.longBitsToDouble(preferences.getLong(
                prefix + "spent_bits", Double.doubleToLongBits(0.0))));
        double budgetLimit = NpcAiBudgetPolicy.normalizeBudgetLimitJpy(
                Double.longBitsToDouble(preferences.getLong(
                        prefix + BUDGET_LIMIT_BITS,
                        Double.doubleToLongBits(DungeonTokenCostPolicy.DEFAULT_BUDGET_JPY))));
        return new Snapshot(
                spent,
                budgetLimit,
                preferences.getLong(prefix + "input_tokens", 0L),
                preferences.getLong(prefix + "cached_input_tokens", 0L),
                preferences.getLong(prefix + "output_tokens", 0L),
                preferences.getLong(prefix + "total_tokens", 0L),
                sanitizeMoney(Double.longBitsToDouble(preferences.getLong(
                        prefix + LIFETIME_SPENT_BITS, Double.doubleToLongBits(0.0)))),
                preferences.getLong(prefix + LIFETIME_INPUT_TOKENS, 0L),
                preferences.getLong(prefix + LIFETIME_CACHED_INPUT_TOKENS, 0L),
                preferences.getLong(prefix + LIFETIME_OUTPUT_TOKENS, 0L),
                preferences.getLong(prefix + LIFETIME_TOTAL_TOKENS, 0L));
    }

    /**
     * One-time v0.4.45 migration. Seed lifetime usage from the legacy current counters before
     * monthly rollover can clear them. The flag prevents duplicate seeding on every read.
     */
    private void ensureLifetimeInitialized(String prefix) {
        if (preferences.getBoolean(prefix + LIFETIME_INITIALIZED, false)) return;
        double spent = sanitizeMoney(Double.longBitsToDouble(preferences.getLong(
                prefix + "spent_bits", Double.doubleToLongBits(0.0))));
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(prefix + LIFETIME_SPENT_BITS, Double.doubleToLongBits(spent))
                .putLong(prefix + LIFETIME_INPUT_TOKENS,
                        Math.max(0L, preferences.getLong(prefix + "input_tokens", 0L)))
                .putLong(prefix + LIFETIME_CACHED_INPUT_TOKENS,
                        Math.max(0L, preferences.getLong(prefix + "cached_input_tokens", 0L)))
                .putLong(prefix + LIFETIME_OUTPUT_TOKENS,
                        Math.max(0L, preferences.getLong(prefix + "output_tokens", 0L)))
                .putLong(prefix + LIFETIME_TOTAL_TOKENS,
                        Math.max(0L, preferences.getLong(prefix + "total_tokens", 0L)))
                .putBoolean(prefix + LIFETIME_INITIALIZED, true);
        editor.commit();
    }

    /**
     * Current-window counters reset when the local calendar month moves forward. Lifetime counters
     * and the configured NPC budget limit are deliberately untouched. Moving the device clock
     * backwards never resets or restores usage.
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

    private static double sanitizeMoney(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, value);
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
