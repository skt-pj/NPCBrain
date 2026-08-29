package com.sktpj.npcbrain;

final class HumanMemoryPolicy {
    static final long MAINTENANCE_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    static final long RECENT_DETAIL_PROTECTION_MS = 72L * 60L * 60L * 1000L;
    static final long GIST_ELIGIBLE_MS = 7L * 24L * 60L * 60L * 1000L;
    static final long FORGET_ELIGIBLE_MS = 30L * 24L * 60L * 60L * 1000L;

    enum RetentionAction {
        KEEP_DETAIL,
        KEEP_GIST,
        FORGET
    }

    private HumanMemoryPolicy() {
    }

    static double retentionScore(
            double importance,
            double emotionality,
            double socialRelevance,
            double repetition,
            int retrievalCount,
            long ageMs
    ) {
        double retrieval = Math.min(1.0, Math.log1p(Math.max(0, retrievalCount)) / Math.log(6.0));
        double base = 0.40 * clamp01(importance)
                + 0.18 * clamp01(emotionality)
                + 0.18 * clamp01(socialRelevance)
                + 0.14 * clamp01(repetition)
                + 0.10 * clamp01(retrieval);
        double ageDays = Math.max(0.0, ageMs) / (24.0 * 60.0 * 60.0 * 1000.0);
        double ageFactor = 1.0 / Math.pow(1.0 + ageDays / 7.0, 0.55);
        return clamp01(base * ageFactor);
    }

    static RetentionAction localEnvelope(
            long ageMs,
            double importance,
            double emotionality,
            double socialRelevance,
            double repetition,
            int retrievalCount
    ) {
        long age = Math.max(0L, ageMs);
        if (age < RECENT_DETAIL_PROTECTION_MS) return RetentionAction.KEEP_DETAIL;
        double score = retentionScore(
                importance,
                emotionality,
                socialRelevance,
                repetition,
                retrievalCount,
                age);
        if (age >= FORGET_ELIGIBLE_MS && score < 0.12) return RetentionAction.FORGET;
        if (age >= GIST_ELIGIBLE_MS && score < 0.24) return RetentionAction.KEEP_GIST;
        return RetentionAction.KEEP_DETAIL;
    }

    static RetentionAction conservativeMerge(RetentionAction local, String llmDecision) {
        RetentionAction llm = parse(llmDecision);
        if (local == RetentionAction.KEEP_DETAIL) return RetentionAction.KEEP_DETAIL;
        if (local == RetentionAction.KEEP_GIST) {
            return llm == RetentionAction.KEEP_DETAIL
                    ? RetentionAction.KEEP_DETAIL
                    : RetentionAction.KEEP_GIST;
        }
        return llm;
    }

    static double clampRelationshipDelta(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(-0.15, Math.min(0.15, value));
    }

    static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    static double clampSigned(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static RetentionAction parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        if ("forget".equals(normalized)) return RetentionAction.FORGET;
        if ("gist".equals(normalized)) return RetentionAction.KEEP_GIST;
        return RetentionAction.KEEP_DETAIL;
    }
}
