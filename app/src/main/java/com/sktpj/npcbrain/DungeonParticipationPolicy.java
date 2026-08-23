package com.sktpj.npcbrain;

final class DungeonParticipationPolicy {
    static final double ACCEPT_WILLINGNESS = 0.62;
    static final double ACCEPT_RESOLVE = 0.55;

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
            this.personalReason = personalReason == null ? "" : personalReason.trim();
        }

        static Candidate none() {
            return new Candidate(false, "none", 0.0, 0.0, 0.0, "");
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

        double willingness = moveToward(before.willingness, candidate.willingness, 0.28, 0.35);
        double fear = moveToward(before.fear, candidate.fear, 0.25, 0.18);
        double resolve = moveToward(before.resolve, candidate.resolve, 0.26, 0.32);
        String reason = cleanReason(candidate.personalReason);
        if (reason.isEmpty()) reason = before.personalReason;

        String stance;
        switch (candidate.decision) {
            case DungeonParticipationState.ACCEPT:
                boolean ready = willingness >= ACCEPT_WILLINGNESS
                        && resolve >= ACCEPT_RESOLVE
                        && !cleanReason(candidate.personalReason).isEmpty();
                stance = ready ? DungeonParticipationState.ACCEPT : DungeonParticipationState.HESITATE;
                break;
            case DungeonParticipationState.WITHDRAW:
                stance = DungeonParticipationState.WITHDRAW;
                break;
            case DungeonParticipationState.REFUSE:
                stance = DungeonParticipationState.REFUSE;
                break;
            default:
                stance = DungeonParticipationState.HESITATE;
                break;
        }
        return new DungeonParticipationState(
                stance,
                willingness,
                fear,
                resolve,
                reason,
                nowMs);
    }

    static DungeonParticipationState emergencyWithdraw(
            DungeonParticipationState previous,
            long nowMs,
            String reason
    ) {
        DungeonParticipationState before = previous == null
                ? DungeonParticipationState.initial() : previous;
        return new DungeonParticipationState(
                DungeonParticipationState.WITHDRAW,
                Math.max(0.0, before.willingness - 0.20),
                Math.min(1.0, before.fear + 0.18),
                Math.max(0.0, before.resolve - 0.24),
                cleanReason(reason),
                nowMs);
    }

    static boolean canAutoExecute(
            DungeonParticipationState participation,
            DungeonObjective objective
    ) {
        return participation != null
                && participation.isAccepted()
                && objective != null
                && objective.isActive();
    }

    private static double moveToward(double from, double target, double maxUp, double maxDown) {
        double start = clamp01(from);
        double end = clamp01(target);
        if (end > start) return Math.min(end, start + maxUp);
        return Math.max(end, start - maxDown);
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
