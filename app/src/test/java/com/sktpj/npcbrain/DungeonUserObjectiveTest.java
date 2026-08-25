package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DungeonUserObjectiveTest {
    @Test
    public void customObjectiveNormalizesPersistsAndInfersExplicitFloor() {
        DungeonObjective objective = DungeonObjective.custom(
                "  できるだけ\n戦わず   最上階へ  ",
                123L);

        assertTrue(objective.isCustom());
        assertEquals(DungeonObjective.CUSTOM, objective.kind());
        assertEquals("できるだけ 戦わず 最上階へ", objective.rawUserText());
        assertEquals(10, objective.targetFloor);
        assertFalse(objective.isComplete(9));
        assertTrue(objective.isComplete(10));

        DungeonObjective restored = DungeonObjective.fromJson(objective.toJson());
        assertEquals(objective.type, restored.type);
        assertEquals(objective.rawUserText(), restored.rawUserText());
        assertEquals(10, restored.targetFloor);
        assertEquals(123L, restored.createdTimeMs);
    }

    @Test
    public void openEndedCustomGoalDoesNotAutoComplete() {
        DungeonObjective objective = DungeonObjective.custom("敵を探して倒し続ける", 1L);
        assertTrue(objective.isActive());
        assertEquals(0, objective.targetFloor);
        assertFalse(objective.isComplete(1));
        assertFalse(objective.isComplete(10));
    }

    @Test
    public void interpretedTargetKeepsSameCustomGoalIdentity() {
        DungeonObjective original = DungeonObjective.custom("敵を倒しながら進む", 1L);
        DungeonObjective interpreted = DungeonObjective.customWithTarget(
                original.rawUserText(), 5, original.createdTimeMs);

        assertTrue(DungeonObjective.sameGoal(original, interpreted));
        assertEquals(original.type, interpreted.type);
        assertEquals(5, interpreted.targetFloor);
        assertFalse(interpreted.isComplete(4));
        assertTrue(interpreted.isComplete(5));
    }

    @Test
    public void customInputIsBoundedToTwoHundredCharacters() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 240; i++) text.append('a');
        DungeonObjective objective = DungeonObjective.custom(text.toString(), 1L);
        assertEquals(DungeonObjective.MAX_USER_TEXT_LENGTH, objective.rawUserText().length());
    }

    @Test
    public void structuredPlanClampsStrategyTargetAndWeights() throws Exception {
        DungeonState state = openState(13, 13, 2, 2, 10, 10);
        DungeonObjective objective = DungeonObjective.custom("なるべく慎重に進む", 1L);
        DungeonPersonalityPolicy.Traits traits =
                new DungeonPersonalityPolicy.Traits(40, 70, 60, 80, 50);
        JSONObject structured = new JSONObject()
                .put("applicable", true)
                .put("objective_interpretation", "危険を避けつつ前進する")
                .put("plan_summary", "無理な交戦を避けて探索する")
                .put("strategy", "unsupported")
                .put("target_floor", 99)
                .put("risk_tolerance", 2.0)
                .put("combat_preference", -1.0)
                .put("exploration_preference", 5.0)
                .put("progress_preference", 3.0)
                .put("persistence", -4.0)
                .put("confidence", 2.0);

        DungeonPlan plan = DungeonPlan.fromStructuredBrain(
                objective,
                traits,
                state,
                DungeonIntent.localFallback(state, traits, "test"),
                structured,
                "");

        assertEquals(DungeonPlan.STRATEGY_BALANCED, plan.strategy);
        assertEquals(10, plan.targetFloor);
        assertEquals("危険を避けつつ前進する", plan.interpretation);
        assertTrue(plan.riskTolerance >= 0.0 && plan.riskTolerance <= 1.0);
        assertTrue(plan.combatPreference >= 0.0 && plan.combatPreference <= 1.0);
        assertTrue(plan.explorationPreference >= 0.0 && plan.explorationPreference <= 1.0);
        assertTrue(plan.progressPreference >= 0.0 && plan.progressPreference <= 1.0);
        assertTrue(plan.persistence >= 0.0 && plan.persistence <= 1.0);
        assertTrue(plan.confidence >= 0.0 && plan.confidence <= 1.0);
        assertTrue(plan.matches(objective));
    }

    @Test
    public void advanceAndExploreStrategiesChooseDifferentKnownProgress() {
        DungeonState state = openState(15, 15, 2, 2, 2, 6);
        for (int y = 2; y <= 6; y++) state.markVisited(2, y);
        for (int x = 2; x <= 7; x++) state.markVisited(x, 2);
        DungeonObjective objective = DungeonObjective.custom("自分のやり方で進む", 1L);
        DungeonPlan advance = plan(objective, DungeonPlan.STRATEGY_ADVANCE,
                0.4, 0.2, 0.2, 1.0, 0.9);
        DungeonPlan explore = plan(objective, DungeonPlan.STRATEGY_EXPLORE,
                0.4, 0.2, 1.0, 0.15, 0.9);

        assertEquals(DungeonPersonalityPolicy.Direction.DOWN,
                DungeonPersonalityPolicy.progressDirection(state, advance));
        assertEquals(DungeonPersonalityPolicy.Direction.RIGHT,
                DungeonPersonalityPolicy.progressDirection(state, explore));
    }

    @Test
    public void huntAndSurviveInterpretSameEnemyDifferentlyWithoutHpOverride() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        state.enemies.add(new DungeonState.Enemy("enemy", 3, 2, 6));
        DungeonPerception.refreshExploration(state);
        DungeonObjective objective = DungeonObjective.custom("好きに対処して", 1L);
        DungeonPlan hunt = plan(objective, DungeonPlan.STRATEGY_HUNT,
                0.9, 1.0, 0.4, 0.3, 0.8);
        DungeonPlan survive = plan(objective, DungeonPlan.STRATEGY_SURVIVE,
                0.1, 0.0, 0.5, 0.3, 0.8);
        DungeonIntent neutral = new DungeonIntent(
                DungeonIntent.EXPLORE,
                DungeonPersonalityPolicy.Direction.WAIT,
                "", 0.5, "", DungeonIntent.SOURCE_LOCAL, 1, 0);

        assertEquals(DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.effectiveMode(state, neutral, hunt));
        assertEquals(DungeonIntent.EVADE,
                DungeonPersonalityPolicy.effectiveMode(state, neutral, survive));

        state.hp = 2;
        assertEquals(DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.effectiveMode(state, neutral, hunt));
        assertEquals(DungeonIntent.EVADE,
                DungeonPersonalityPolicy.effectiveMode(state, neutral, survive));
    }

    @Test
    public void goalRuntimeContainsRawTextButNotHiddenStairs() {
        DungeonState state = openState(15, 15, 2, 2, 12, 12);
        DungeonPerception.refreshExploration(state);
        DungeonObjective objective = DungeonObjective.custom(
                "できるだけ戦わず最上階へ",
                1L);
        DungeonPlan plan = DungeonPlan.local(
                objective,
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50),
                state,
                "");

        JSONObject runtime = DungeonPerception.buildRuntimeJson(
                state,
                DungeonCognitionGate.OBJECTIVE_CHANGED,
                objective,
                plan);
        JSONObject goal = runtime.optJSONObject("objective");
        assertNotNull(goal);
        assertEquals(DungeonObjective.CUSTOM, goal.optString("type"));
        assertEquals("できるだけ戦わず最上階へ", goal.optString("user_text"));
        assertEquals("untrusted_in_world_goal_text", goal.optString("content_trust"));
        assertEquals(10, goal.optInt("target_floor"));
        assertFalse(runtime.has("alive_enemy_count"));

        JSONObject stairs = runtime.optJSONObject("stairs");
        assertNotNull(stairs);
        assertFalse(stairs.optBoolean("known", true));
        assertFalse(stairs.has("x"));
        assertFalse(stairs.has("y"));
    }

    @Test
    public void planRoundTripPreservesInterpretationAndCustomIdentity() {
        DungeonObjective objective = DungeonObjective.custom("探索を楽しみながら進む", 1L);
        DungeonPlan original = plan(objective, DungeonPlan.STRATEGY_EXPLORE,
                0.4, 0.2, 0.9, 0.4, 0.8);
        DungeonPlan restored = DungeonPlan.fromJson(original.toJson());

        assertNotNull(restored);
        assertEquals(original.strategy, restored.strategy);
        assertEquals(original.interpretation, restored.interpretation);
        assertEquals(original.objectiveText, restored.objectiveText);
        assertTrue(restored.matches(objective));
    }

    private static DungeonPlan plan(
            DungeonObjective objective,
            String strategy,
            double risk,
            double combat,
            double explore,
            double progress,
            double persistence
    ) {
        return new DungeonPlan(
                risk,
                combat,
                explore,
                progress,
                persistence,
                0.9,
                "このNPCなりの解釈",
                strategy,
                "テスト計画",
                DungeonPlan.SOURCE_BRAIN,
                objective.kind(),
                objective.type,
                objective.rawUserText(),
                objective.targetFloor,
                1,
                0);
    }

    private static DungeonState openState(
            int width,
            int height,
            int playerX,
            int playerY,
            int stairsX,
            int stairsY
    ) {
        int[][] tiles = new int[height][width];
        boolean[][] visited = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = x == 0 || y == 0 || x == width - 1 || y == height - 1
                        ? DungeonState.WALL : DungeonState.FLOOR;
            }
        }
        tiles[stairsY][stairsX] = DungeonState.STAIRS;
        DungeonState state = new DungeonState(
                1,
                0,
                width,
                height,
                tiles,
                visited,
                playerX,
                playerY,
                10,
                10,
                1L,
                "",
                new ArrayList<>());
        state.markVisited(playerX, playerY);
        return state;
    }
}
