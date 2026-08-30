package com.sktpj.npcbrain;

final class DungeonAutonomyPolicy {
    private DungeonAutonomyPolicy() {
    }

    static boolean shouldSelfDive(
            DungeonParticipationState participation,
            DungeonPersonalityPolicy.Traits traits,
            boolean alive,
            String npcId,
            long dayBucket
    ) {
        if (!alive) return false;
        DungeonParticipationState state = participation == null
                ? DungeonParticipationState.initial() : participation;
        if (DungeonParticipationState.REFUSE.equals(state.stance)
                || DungeonParticipationState.WITHDRAW.equals(state.stance)) return false;
        if (state.isAccepted()) return true;
        if (traits == null) return false;

        double curiosity = traits.openness / 100.0;
        double initiative = traits.extraversion / 100.0;
        double followThrough = traits.conscientiousness / 100.0;
        double caution = traits.neuroticism / 100.0;
        double willingness = state.willingness;
        double fear = state.fear;
        double resolve = state.resolve;
        double stableVariation = stableUnit(npcId, dayBucket);

        double drive = 0.25 * curiosity
                + 0.16 * initiative
                + 0.12 * followThrough
                + 0.17 * willingness
                + 0.12 * resolve
                + 0.10 * (1.0 - caution)
                + 0.08 * stableVariation
                - 0.14 * fear;
        if (DungeonParticipationState.HESITATE.equals(state.stance)) drive -= 0.05;
        return drive >= 0.50;
    }

    /** Legacy v0.4.42 party helper retained only for compatibility tests/callers. */
    static boolean shouldSelfJoin(
            DungeonParticipationState participation,
            DungeonPersonalityPolicy.Traits traits,
            boolean alive,
            int rosterSize,
            String npcId,
            long dayBucket
    ) {
        if (rosterSize >= DungeonRosterPolicy.MAX_ACTIVE) return false;
        return shouldSelfDive(participation, traits, alive, npcId, dayBucket);
    }

    static long dayBucket(long nowMs) {
        return Math.max(0L, nowMs) / 86_400_000L;
    }

    private static double stableUnit(String npcId, long bucket) {
        long value = (npcId == null ? 0L : npcId.hashCode()) * 0x9E3779B97F4A7C15L ^ bucket;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        long positive = value & Long.MAX_VALUE;
        return (positive % 10_001L) / 10_000.0;
    }
}
