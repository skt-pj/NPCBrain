package com.sktpj.npcbrain;

final class NpcInnerLifePolicy {
    static final long HEARTBEAT_MS = 60_000L;
    static final long LOCAL_STREAM_INTERVAL_MS = 15L * 60L * 1000L;
    static final long MIN_AMBIENT_INTERVAL_MS = 45L * 60L * 1000L;
    static final long MAX_AMBIENT_INTERVAL_MS = 90L * 60L * 1000L;
    static final long REFLECTION_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    static final int REFLECTION_THOUGHT_COUNT = 8;

    static final class AdvanceResult {
        final NpcInnerLifeState state;
        final boolean appendLocalThought;

        AdvanceResult(NpcInnerLifeState state, boolean appendLocalThought) {
            this.state = state;
            this.appendLocalThought = appendLocalThought;
        }
    }

    private NpcInnerLifePolicy() {
    }

    /**
     * Advances bounded need/signal values only. Text activities and hard thresholds do not decide
     * mood, focus or intention; those remain the last integrated AI values until the next ambient
     * Brain update.
     */
    static AdvanceResult advance(
            NpcInnerLifeState current,
            long nowMs,
            String activity,
            String goal,
            double extraversion,
            double neuroticism,
            double conscientiousness,
            double openness,
            double valence,
            double stress
    ) {
        long now = Math.max(0L, nowMs);
        NpcInnerLifeState base = current == null
                ? NpcInnerLifeState.initial(now, extraversion, neuroticism, openness)
                : current;

        long elapsedMs = Math.max(0L, now - base.updatedAtMs);
        double hours = Math.min(12.0, elapsedMs / 3_600_000.0);

        double energy = clamp01(base.energy - 0.035 * hours);
        double hunger = clamp01(base.hunger + 0.055 * hours);
        double socialNeed = clamp01(base.socialNeed
                + (0.020 + 0.018 * clamp01(extraversion)) * hours);
        double boredom = clamp01(base.boredom
                + (0.020 + 0.020 * (1.0 - clamp01(conscientiousness))) * hours);

        double curiosityTarget = 0.24 + 0.62 * clamp01(openness);
        double curiosity = clamp01(base.curiosity
                + (curiosityTarget - base.curiosity) * Math.min(1.0, hours * 0.10)
                + boredom * 0.025 * hours);

        double safetyTarget = 0.08
                + 0.24 * clamp01(neuroticism)
                + 0.38 * clamp01(stress);
        double safetyConcern = clamp01(base.safetyConcern
                + (safetyTarget - base.safetyConcern) * Math.min(1.0, hours * 0.30));

        boolean firstStream = base.lastStreamAtMs <= 0L;
        boolean streamDue = firstStream
                || now - base.lastStreamAtMs >= LOCAL_STREAM_INTERVAL_MS;

        NpcInnerLifeState updated = base.withLocalState(
                now,
                energy,
                hunger,
                socialNeed,
                boredom,
                curiosity,
                safetyConcern,
                base.mood,
                base.focus,
                base.intention,
                streamDue
        );
        return new AdvanceResult(updated, streamDue);
    }

    static long ambientIntervalMs(
            double extraversion,
            double neuroticism,
            double openness
    ) {
        double drive = 0.40 * clamp01(openness)
                + 0.35 * clamp01(extraversion)
                + 0.25 * clamp01(neuroticism);
        long span = MAX_AMBIENT_INTERVAL_MS - MIN_AMBIENT_INTERVAL_MS;
        long interval = MAX_AMBIENT_INTERVAL_MS - Math.round(span * drive);
        return Math.max(MIN_AMBIENT_INTERVAL_MS, Math.min(MAX_AMBIENT_INTERVAL_MS, interval));
    }

    static boolean isAmbientDue(
            NpcInnerLifeState state,
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness
    ) {
        if (state == null) return false;
        long now = Math.max(0L, nowMs);
        long interval = ambientIntervalMs(extraversion, neuroticism, openness);
        return now >= state.lastAmbientAtMs
                && now - state.lastAmbientAtMs >= interval;
    }

    static boolean reflectionDue(NpcInnerLifeState state, long nowMs) {
        if (state == null) return false;
        if (state.aiThoughtCount >= REFLECTION_THOUGHT_COUNT) return true;
        long anchor = state.lastReflectionAtMs > 0L
                ? state.lastReflectionAtMs
                : state.initializedAtMs;
        long now = Math.max(anchor, nowMs);
        return now - anchor >= REFLECTION_INTERVAL_MS;
    }

    static String localThought(NpcInnerLifeState state, String activity, String goal) {
        if (state == null) return "内面状態を確認している。";
        String focus = safe(state.focus);
        if (!focus.isEmpty()) return "今は「" + limit(focus, 90) + "」に意識が向いている。";
        return "内面状態を確認している。";
    }

    static String compactNeeds(NpcInnerLifeState state) {
        if (state == null) return "";
        return "ENERGY " + percent(state.energy)
                + "% · HUNGER " + percent(state.hunger)
                + "% · SOCIAL " + percent(state.socialNeed)
                + "% · BOREDOM " + percent(state.boredom)
                + "% · CURIOSITY " + percent(state.curiosity)
                + "% · SAFETY " + percent(state.safetyConcern) + "%";
    }

    private static int percent(double value) {
        return (int) Math.round(clamp01(value) * 100.0);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
