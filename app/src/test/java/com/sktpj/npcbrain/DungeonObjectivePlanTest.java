package com.sktpj.npcbrain;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DungeonObjectivePlanTest {
    @Test
    public void reachTopObjectiveCompletesAtFloorTenAndRoundTrips() {
        DungeonObjective objective = DungeonObjective.reachTop(123L);
        assertTrue(objective.isActive());
        assertEquals(10, objective.targetFloor);
        assertFalse(objective.isComplete(9));
        assertTrue(objective.isComplete(10));
        DungeonObjective restored = DungeonObjective.fromJson(objective.toJson());
        assertEquals(DungeonObjective.REACH_TOP, restored.type);
        assertEquals(10, restored.targetFloor);
        assertEquals(123L, restored.createdTimeMs);
    }

    @Test
    public void localPlanIsTraitIndependentAndBounded() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        DungeonObjective objective = DungeonObjective.reachTop(1L);
        DungeonPlan timid = DungeonPlan.local(
                objective,
                new DungeonPersonalityPolicy.Traits(0, 100, 100, 0, 0),
                state,
                "");
        DungeonPlan bold = DungeonPlan.local(
                objective,
                new DungeonPersonalityPolicy.Traits(100, 0, 0, 100, 100),
                state,
                "");
        assertEquals(timid.riskTolerance, bold.riskTolerance, 0.000001);
        assertEquals(timid.combatPreference, bold.combatPreference, 0.000001);
        assertEquals(timid.explorationPreference, bold.explorationPreference, 0.000001);
        assertEquals(timid.progressPreference, bold.progressPreference, 0.000001);
        assertEquals(timid.persistence, bold.persistence, 0.000001);
        assertEquals(DungeonPlan.STRATEGY_ADVANCE, timid.strategy);
        assertTrue(timid.matches(objective));
    }

    @Test
    public void structuredBrainPlanIsNotBlendedWithLocalPsychology() throws Exception {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        DungeonObjective objective = DungeonObjective.reachTop(1L);
        DungeonIntent intent = new DungeonIntent(
                DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.Direction.RIGHT,
                "enemy", 0.91, "", DungeonIntent.SOURCE_BRAIN, 1, 0);
        JSONObject structured = new JSONObject()
                .put("applicable", true)
                .put("strategy", DungeonPlan.STRATEGY_HUNT)
                .put("target_floor", 10)
                .put("risk_tolerance", 0.83)
                .put("combat_preference", 0.94)
                .put("exploration_preference", 0.21)
                .put("progress_preference", 0.47)
                .put("persistence", 0.62)
                .put("confidence", 0.88)
                .put("objective_interpretation", "戦いながら進む")
                .put("plan_summary", "必要なら交戦する");

        DungeonPlan plan = DungeonPlan.fromStructuredBrain(
                objective,
                new DungeonPersonalityPolicy.Traits(0, 100, 100, 0, 0),
                state,
                intent,
                structured,
                "");
        assertEquals(0.83, plan.riskTolerance, 0.000001);
        assertEquals(0.94, plan.combatPreference, 0.000001);
        assertEquals(0.21, plan.explorationPreference, 0.000001);
        assertEquals(0.47, plan.progressPreference, 0.000001);
        assertEquals(0.62, plan.persistence, 0.000001);
        assertEquals(0.88, plan.confidence, 0.000001);
        assertEquals(DungeonPlan.SOURCE_BRAIN, plan.source);
    }

    @Test
    public void brainIntentFallbackKeepsPsychologicalWeightsNeutral() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        DungeonObjective objective = DungeonObjective.reachTop(1L);
        DungeonIntent engage = new DungeonIntent(
                DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.Direction.RIGHT,
                "enemy", 1.0, "", DungeonIntent.SOURCE_BRAIN, 1, 0);
        DungeonPlan a = DungeonPlan.fromBrain(
                objective,
                new DungeonPersonalityPolicy.Traits(0, 100, 100, 0, 0),
                state,
                engage,
                "攻める");
        DungeonPlan b = DungeonPlan.fromBrain(
                objective,
                new DungeonPersonalityPolicy.Traits(100, 0, 0, 100, 100),
                state,
                engage,
                "攻める");
        assertEquals(0.5, a.riskTolerance, 0.000001);
        assertEquals(0.5, a.combatPreference, 0.000001);
        assertEquals(0.5, a.explorationPreference, 0.000001);
        assertEquals(0.5, a.progressPreference, 0.000001);
        assertEquals(0.5, a.persistence, 0.000001);
        assertEquals(a.riskTolerance, b.riskTolerance, 0.000001);
        assertEquals(a.combatPreference, b.combatPreference, 0.000001);
        assertEquals(a.strategy, b.strategy);
        assertEquals(DungeonPlan.STRATEGY_HUNT, a.strategy);
    }

    @Test
    public void planChangesPreferenceButCannotLegalizeWall() {
        DungeonState state = openState(7, 7, 2, 2, 5, 5);
        state.enemies.add(new DungeonState.Enemy("enemy", 3, 2, 6));
        state.tiles[1][2] = DungeonState.WALL;
        DungeonPerception.refreshExploration(state);
        DungeonPersonalityPolicy.Traits traits =
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50);
        DungeonIntent engage = new DungeonIntent(
                DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.Direction.WAIT,
                "enemy", 1.0, "", DungeonIntent.SOURCE_LOCAL, 1, 0);
        DungeonPlan highCombat = new DungeonPlan(
                1.0, 1.0, 0.5, 0.8, "", DungeonPlan.SOURCE_BRAIN,
                DungeonObjective.REACH_TOP, 10, 1, 0);
        DungeonPlan lowCombat = new DungeonPlan(
                0.0, 0.0, 0.5, 0.8, "", DungeonPlan.SOURCE_BRAIN,
                DungeonObjective.REACH_TOP, 10, 1, 0);
        double highAttack = DungeonPersonalityPolicy.scoreDirection(
                state, traits, DungeonPersonalityPolicy.Direction.RIGHT, engage, highCombat);
        double lowAttack = DungeonPersonalityPolicy.scoreDirection(
                state, traits, DungeonPersonalityPolicy.Direction.RIGHT, engage, lowCombat);
        assertTrue(highAttack > lowAttack);
        assertEquals(-Double.MAX_VALUE,
                DungeonPersonalityPolicy.scoreDirection(
                        state, traits, DungeonPersonalityPolicy.Direction.UP, engage, highCombat),
                0.0);
    }

    @Test
    public void progressMonitorUsesStallThresholdAndBrainCooldown() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        state.turn = 0;
        DungeonProgressMonitor.Snapshot initial = DungeonProgressMonitor.initial(state);
        state.turn = 48;
        DungeonProgressMonitor.Result blocked = DungeonProgressMonitor.observe(initial, state, 0);
        assertFalse(blocked.shouldReplan);
        state.turn = 96;
        DungeonProgressMonitor.Result replan = DungeonProgressMonitor.observe(initial, state, 0);
        assertTrue(replan.shouldReplan);
    }

    @Test
    public void strategyGateStillLimitsApiReplanningToStrategicReasons() {
        assertTrue(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.OBJECTIVE_CHANGED));
        assertTrue(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.PROGRESS_STALLED));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.PERIODIC));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.HP_RISK));
    }

    @Test
    public void planningRuntimeIncludesObjectiveWithoutHiddenStairs() {
        DungeonState state = openState(13, 13, 2, 2, 10, 10);
        DungeonPerception.refreshExploration(state);
        DungeonObjective objective = DungeonObjective.reachTop(1L);
        DungeonPlan plan = DungeonPlan.local(
                objective,
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50),
                state,
                "");
        JSONObject runtime = DungeonPerception.buildRuntimeJson(
                state, DungeonCognitionGate.OBJECTIVE_CHANGED, objective, plan);
        assertEquals(DungeonObjective.REACH_TOP,
                runtime.optJSONObject("objective").optString("type"));
        assertNotNull(runtime.optJSONObject("existing_plan"));
        JSONObject stairs = runtime.optJSONObject("stairs");
        assertFalse(stairs.optBoolean("known", true));
        assertFalse(stairs.has("x"));
    }

    @Test
    public void neutralLocalObjectiveExecutionCanReachTopWithoutApiWhenNoEnemies() {
        for (long seed = 100L; seed < 103L; seed++) {
            DungeonState state = DungeonGenerator.generate(seed, 1);
            DungeonObjective objective = DungeonObjective.reachTop(1L);
            DungeonPersonalityPolicy.Traits traits =
                    new DungeonPersonalityPolicy.Traits(20, 90, 85, 80, 65);
            DungeonPlan plan = DungeonPlan.local(objective, traits, state, "test");
            int steps = 0;
            while (!objective.isComplete(state.floor) && steps < 5000) {
                state.enemies.clear();
                DungeonIntent intent = DungeonIntent.localFallback(state, traits, "test");
                state = DungeonEngine.step(state, traits, intent, plan);
                steps++;
            }
            assertTrue("seed=" + seed + " floor=" + state.floor + " steps=" + steps,
                    objective.isComplete(state.floor));
        }
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
                1, 0, width, height, tiles, visited,
                playerX, playerY, 10, 10, 1L, "", new ArrayList<>());
        state.markVisited(playerX, playerY);
        return state;
    }
}
