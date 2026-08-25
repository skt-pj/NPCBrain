package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HumanConsentTest {
    @Test
    public void humanBaselineIsPrincipleNotFixedPsychologicalNumbers() {
        JSONObject baseline = HumanBaseline.toJson();
        assertTrue(baseline.optBoolean("ordinary_human", false));
        assertEquals("global_workspace", baseline.optString("decision_owner"));
        assertFalse(baseline.has("death_aversion"));
        assertFalse(baseline.has("self_preservation"));
        assertFalse(baseline.has("violence_aversion"));
    }

    @Test
    public void participationInitialStateIsNeutralUnknownAndRoundTrips() {
        DungeonParticipationState initial = DungeonParticipationState.initial();
        assertEquals(DungeonParticipationState.NOT_ASKED, initial.stance);
        assertEquals(0.50, initial.willingness, 0.000001);
        assertEquals(0.50, initial.fear, 0.000001);
        assertEquals(0.50, initial.resolve, 0.000001);
        assertFalse(initial.isAccepted());

        DungeonParticipationState restored = DungeonParticipationState.fromJson(initial.toJson());
        assertEquals(initial.stance, restored.stance);
        assertEquals(initial.willingness, restored.willingness, 0.000001);
        assertEquals(initial.fear, restored.fear, 0.000001);
        assertEquals(initial.resolve, restored.resolve, 0.000001);
    }

    @Test
    public void oldParticipationJsonRemainsReadableWithoutBecomingAGate() throws Exception {
        JSONObject old = new JSONObject()
                .put("stance", DungeonParticipationState.HESITATE)
                .put("willingness", 0.08)
                .put("fear", 0.82)
                .put("resolve", 0.08)
                .put("personal_reason", "")
                .put("updated_time_ms", 1L);
        DungeonParticipationState restored = DungeonParticipationState.fromJson(old);
        assertEquals(0.08, restored.willingness, 0.000001);
        assertEquals(0.82, restored.fear, 0.000001);
        assertEquals(DungeonParticipationState.HESITATE, restored.stance);

        DungeonParticipationState accepted = DungeonParticipationPolicy.apply(
                restored,
                new DungeonParticipationPolicy.Candidate(
                        true,
                        DungeonParticipationState.ACCEPT,
                        0.10,
                        0.95,
                        0.10,
                        ""),
                2L);
        assertTrue(accepted.isAccepted());
    }

    @Test
    public void oneIntegratedAcceptIsAuthoritativeEvenWithFearAndNoReason() {
        DungeonParticipationState after = DungeonParticipationPolicy.apply(
                DungeonParticipationState.initial(),
                new DungeonParticipationPolicy.Candidate(
                        true,
                        DungeonParticipationState.ACCEPT,
                        0.30,
                        0.92,
                        0.35,
                        ""),
                10L);
        assertEquals(DungeonParticipationState.ACCEPT, after.stance);
        assertTrue(after.isAccepted());
        assertEquals(0.92, after.fear, 0.000001);
        assertTrue(after.personalReason.isEmpty());
    }

    @Test
    public void refusalAndWithdrawAreAlsoAuthoritative() {
        DungeonParticipationState refused = DungeonParticipationPolicy.apply(
                DungeonParticipationState.initial(),
                new DungeonParticipationPolicy.Candidate(
                        true,
                        DungeonParticipationState.REFUSE,
                        0.80,
                        0.10,
                        0.90,
                        "行きたくない"),
                10L);
        assertEquals(DungeonParticipationState.REFUSE, refused.stance);

        DungeonParticipationState withdrawn = DungeonParticipationPolicy.apply(
                new DungeonParticipationState(
                        DungeonParticipationState.ACCEPT, 0.8, 0.6, 0.8, "", 1L),
                new DungeonParticipationPolicy.Candidate(
                        true,
                        DungeonParticipationState.WITHDRAW,
                        0.2,
                        0.9,
                        0.2,
                        "ここでやめる"),
                20L);
        assertEquals(DungeonParticipationState.WITHDRAW, withdrawn.stance);
    }

    @Test
    public void nonApplicableOutputDoesNotChangeState() {
        DungeonParticipationState before = new DungeonParticipationState(
                DungeonParticipationState.REFUSE, 0.15, 0.91, 0.12, "危険だから嫌だ", 5L);
        DungeonParticipationState after = DungeonParticipationPolicy.apply(
                before,
                DungeonParticipationPolicy.Candidate.none(),
                99L);
        assertEquals(before.stance, after.stance);
        assertEquals(before.updatedTimeMs, after.updatedTimeMs);
    }

    @Test
    public void hpPolicyCannotAutomaticallyRevokeAcceptance() {
        DungeonParticipationState accepted = new DungeonParticipationState(
                DungeonParticipationState.ACCEPT, 0.8, 0.9, 0.4, "", 1L);
        DungeonParticipationState after = DungeonParticipationPolicy.emergencyWithdraw(
                accepted, 3L, "HPが低い");
        assertEquals(DungeonParticipationState.ACCEPT, after.stance);
        assertTrue(after.isAccepted());
        assertTrue(DungeonParticipationPolicy.canAutoExecute(
                after, DungeonObjective.reachTop(2L)));
    }

    @Test
    public void executionStillRequiresExplicitAcceptedStanceAndActiveObjective() {
        DungeonParticipationState accepted = new DungeonParticipationState(
                DungeonParticipationState.ACCEPT, 0.5, 0.5, 0.5, "", 1L);
        assertFalse(DungeonParticipationPolicy.canAutoExecute(accepted, DungeonObjective.none()));
        assertTrue(DungeonParticipationPolicy.canAutoExecute(accepted, DungeonObjective.reachTop(2L)));
        assertFalse(DungeonParticipationPolicy.canAutoExecute(
                DungeonParticipationState.initial(), DungeonObjective.reachTop(2L)));
    }
}
