package com.sktpj.npcbrain;

final class DungeonParticipationPolicy {
    static final class Candidate {
        final boolean applicable;
        final String decision;
        final double willingness;
        final double fear;
        final double resolve;
        final String personalReason;

        Candidate(
                boolean applicable,
                String decision,
                double willingness,
                double fear,
                double resolve,
                String personalReason
        ) {
            this.applicable = applicable;
            this.decision = normalizeDecision(decision);
            this.willingness = clamp01(willingness);
            this.fear = clamp01(fear);
            this.resolve = clamp01(resolve);
            this.personalReason = cleanReason(personalReason);
        }

        static Candidate none() {
            return new Candidate(false, "none", 0.5, 0.5, 0.5, "");
        }
    }

    private DungeonParticipationPolicy() {
    }

    static DungeonParticipationState apply(
            DungeonParticipationState previous,
            Candidate candidate,
            long nowMs
    ) {
        DungeonParticipationState before = previous == null
                ? DungeonParticipationState.initial() : previous;
        if (candidate == null || !candidate.applicable || "none".equals(candidate.decision)) {
            return before;
        }
        String reason = candidate.personalReason.isEmpty()
                ? before.personalReason : candidate.personalReason;
        return new DungeonParticipationState(
                candidate.decision,
                candidate.willingness,
                candidate.fear,
                candidate.resolve,
                reason,
                nowMs);
    }

    /** Legacy compatibility only. Dungeon-specific danger cannot revoke ordinary area access. */
    static DungeonParticipationState emergencyWithdraw(
            DungeonParticipationState previous,
            long nowMs,
            String reason
    ) {
        return previous == null ? DungeonParticipationState.initial() : previous;
    }

    /**
     * Legacy compatibility helper. Participation state is no longer an execution prerequisite;
     * dungeon access is governed by the same ordinary NPC/world state rules as other areas.
     */
    static boolean canAutoExecute(
            DungeonParticipationState participation,
            DungeonObjective objective
    ) {
        return true;
    }

    private static String normalizeDecision(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        if (DungeonParticipationState.REFUSE.equals(normalized)
                || DungeonParticipationState.HESITATE.equals(normalized)
                || DungeonParticipationState.ACCEPT.equals(normalized)
                || DungeonParticipationState.WITHDRAW.equals(normalized)) {
            return normalized;
        }
        return "none";
    }

    private static String cleanReason(String value) {
        String text = value == null ? "" : value.replace('\n', ' ').trim().replaceAll("\\s+", " ");
        if (text.length() > 220) text = text.substring(0, 220);
        return text;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
