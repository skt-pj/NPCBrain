package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HumanConsentTest {
    @Test
    public void ordinaryHumanBaselineKeepsSurvivalHighest() {
        JSONObject baseline = HumanBaseline.toJson();
        assertEquals(1.0, baseline.optDouble("death_aversion"), 0.000001);
        assertEquals(0.95, baseline.optDouble("self_preservation"), 0.000001);
        assertTrue(baseline.optDouble("death_aversion") > baseline.optDouble("violence_aversion"));
        assertTrue(baseline.optDouble("self_preservation") > baseline.optDouble("uncertainty_aversion"));
        assertTrue(baseline.optBoolean("ordinary_human", false));
    }

    @Test
    public void participationInitialStateIsReluctantAndRoundTrips() {
        DungeonParticipationState initial = DungeonParticipationState.initial();
        assertEquals(DungeonParticipationState.NOT_ASKED, initial.stance);
        assertEquals(0.08, initial.willingness, 0.000001);
        assertEquals(0.82, initial.fear, 0.000001);
        assertEquals(0.08, initial.resolve, 0.000001);
        assertFalse(initial.isAccepted());

        DungeonParticipationState restored = DungeonParticipationState.fromJson(initial.toJson());
        assertEquals(initial.stance, restored.stance);
        assertEquals(initial.willingness, restored.willingness, 0.000001);
        assertEquals(initial.fear, restored.fear, 0.000001);
        assertEquals(initial.resolve, restored.resolve, 0.000001);
    }

    @Test
    public void oneConversationCannotFlipInitialStateToAccept() {
        DungeonParticipationState initial = DungeonParticipationState.initial();
        DungeonParticipationPolicy.Candidate candidate = new DungeonParticipationPolicy.Candidate(
                true,
                DungeonParticipationState.ACCEPT,
                1.0,
                0.0,
                1.0,
                "君を一人で危険な場所へ行かせたくないから、同行する。"
        );
        DungeonParticipationState after = DungeonParticipationPolicy.apply(initial, candidate, 10L);
        assertEquals(DungeonParticipationState.HESITATE, after.stance);
        assertEquals(0.36, after.willingness, 0.000001);
        assertEquals(0.64, after.fear, 0.000001);
        assertEquals(0.34, after.resolve, 0.000001);
        assertFalse(after.isAccepted());
    }

    @Test
    public void acceptRequiresOwnReasonAndEnoughResolve() {
        DungeonParticipationState state = DungeonParticipationState.initial();
        DungeonParticipationPolicy.Candidate noReason = new DungeonParticipationPolicy.Candidate(
                true,
                DungeonParticipationState.ACCEPT,
                1.0, 0.4, 1.0, "");
        state = DungeonParticipationPolicy.apply(state, noReason, 10L);
        state = DungeonParticipationPolicy.apply(state, noReason, 20L);
        assertFalse(state.isAccepted());

        DungeonParticipationPolicy.Candidate withReason = new DungeonParticipationPolicy.Candidate(
                true,
                DungeonParticipationState.ACCEPT,
                1.0, 0.5, 1.0,
                "怖いけど、君を一人で行かせたくないから一緒に行く。"
        );
        state = DungeonParticipationPolicy.apply(state, withReason, 30L);
        assertTrue(state.isAccepted());
        assertFalse(state.personalReason.isEmpty());
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
        assertEquals(before.willingness, after.willingness, 0.0);
        assertEquals(before.fear, after.fear, 0.0);
        assertEquals(before.updatedTimeMs, after.updatedTimeMs);
    }

    @Test
    public void expressedRefusalWinsOverUserRequest() {
        JSONArray trace = trace("危険を強く感じ、参加したくないと判断した");
        DungeonParticipationPolicy.Candidate candidate = DungeonParticipationInference.infer(
                DungeonParticipationState.initial(),
                "お願いだからダンジョンに行って。もう行くって決めたよね？",
                "嫌だ。死にたくないし、そんな危険な所には行きたくない。",
                trace);
        DungeonParticipationState state = DungeonParticipationPolicy.apply(
                DungeonParticipationState.initial(), candidate, 100L);
        assertEquals(DungeonParticipationState.REFUSE, state.stance);
        assertFalse(state.isAccepted());
    }

    @Test
    public void repeatedExpressedCommitmentCanBecomeAccepted() {
        DungeonParticipationState state = DungeonParticipationState.initial();
        String npc = "怖いけど、君を一人で行かせたくないから一緒にダンジョンへ行く。";
        JSONArray trace = trace("恐怖はあるが、相手を一人で行かせたくないという本人の理由で同行を選ぶ");
        DungeonParticipationPolicy.Candidate first = DungeonParticipationInference.infer(
                state, "一緒にダンジョンへ来てほしい", npc, trace);
        state = DungeonParticipationPolicy.apply(state, first, 100L);
        assertFalse(state.isAccepted());
        DungeonParticipationPolicy.Candidate second = DungeonParticipationInference.infer(
                state, "本当に無理なら断っていい。どうしたい？", npc, trace);
        state = DungeonParticipationPolicy.apply(state, second, 200L);
        assertTrue(state.isAccepted());
        assertTrue(state.personalReason.contains("一人で"));
    }

    @Test
    public void acceptedNpcCanWithdrawAndExecutionGateNeedsObjective() {
        DungeonParticipationState accepted = new DungeonParticipationState(
                DungeonParticipationState.ACCEPT, 0.8, 0.5, 0.8, "助けたいから行く", 1L);
        assertFalse(DungeonParticipationPolicy.canAutoExecute(accepted, DungeonObjective.none()));
        assertTrue(DungeonParticipationPolicy.canAutoExecute(accepted, DungeonObjective.reachTop(2L)));

        DungeonParticipationState withdrawn = DungeonParticipationPolicy.emergencyWithdraw(
                accepted, 3L, "死にたくないのでこれ以上は進めない");
        assertEquals(DungeonParticipationState.WITHDRAW, withdrawn.stance);
        assertFalse(DungeonParticipationPolicy.canAutoExecute(
                withdrawn, DungeonObjective.reachTop(2L)));
        assertTrue(withdrawn.fear > accepted.fear);
        assertTrue(withdrawn.resolve < accepted.resolve);
    }

    private static JSONArray trace(String summary) {
        JSONArray trace = new JSONArray();
        JSONObject stage = new JSONObject();
        try {
            stage.put("stage_id", "global_workspace");
            stage.put("summary", summary);
            trace.put(stage);
        } catch (Exception ignored) {
        }
        return trace;
    }
}
