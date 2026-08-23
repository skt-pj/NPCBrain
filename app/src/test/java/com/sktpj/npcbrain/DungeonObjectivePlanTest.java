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
        assertTrue(objective.isComplete(11));

        DungeonObjective restored = DungeonObjective.fromJson(objective.toJson());
        assertEquals(DungeonObjective.REACH_TOP, restored.type);
        assertEquals(10, restored.targetFloor);
        assertEquals(123L, restored.createdTimeMs);
    }

    @Test
    public void localPlanIsDeterministicAndBounded() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        DungeonPersonalityPolicy.Traits traits =
                new DungeonPersonalityPolicy.Traits(20, 90, 80, 75, 60);
        DungeonObjective objective = DungeonObjective.reachTop(1L);

        DungeonPlan a = DungeonPlan.local(objective, traits, state, "");
        DungeonPlan b = DungeonPlan.local(objective, traits, state, "");
        assertEquals(a.riskTolerance, b.riskTolerance, 0.000001);
        assertEquals(a.combatPreference, b.combatPreference, 0.000001);
        assertEquals(a.explorationPreference, b.explorationPreference, 0.000001);
        assertEquals(a.persistence, b.persistence, 0.000001);
        assertTrue(a.riskTolerance >= 0.0 && a.riskTolerance <= 1.0);
        assertTrue(a.combatPreference >= 0.0 && a.combatPreference <= 1.0);
        assertTrue(a.explorationPreference >= 0.0 && a.explorationPreference <= 1.0);
        assertTrue(a.persistence >= 0.0 && a.persistence <= 1.0);
        assertTrue(a.persistence >= 0.65);
        assertTrue(a.matches(objective));
    }

    @Test
    public void brainIntentBecomesPersistentBoundedPlan() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        DungeonPersonalityPolicy.Traits traits =
                new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50);
        DungeonObjective objective = DungeonObjective.reachTop(1L);
        DungeonIntent engage = new DungeonIntent(
                DungeonIntent.ENGAGE,
                DungeonPersonalityPolicy.Direction.RIGHT,
                "enemy",
                1.0,
                "",
                DungeonIntent.SOURCE_BRAIN,
                1,
                0);
        DungeonIntent evade = new DungeonIntent(
                DungeonIntent.EVADE,
                DungeonPersonalityPolicy.Direction.LEFT,
                "enemy",
                1.0,
                "",
                DungeonIntent.SOURCE_BRAIN,
                1,
                0);

        DungeonPlan aggressive = DungeonPlan.fromBrain(
                objective, traits, state, engage, "攻めながら突破する");
        DungeonPlan cautious = DungeonPlan.fromBrain(
                objective, traits, state, evade, "生存を優先して突破する");
        assertEquals(DungeonPlan.SOURCE_BRAIN, aggressive.source);
        assertTrue(aggressive.combatPreference > cautious.combatPreference);
        assertTrue(aggressive.riskTolerance > cautious.riskTolerance);
        assertEquals("攻めながら突破する", aggressive.summary);
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
                DungeonPersonalityPolicy.Direction.RIGHT,
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
                        state,
                        traits,
                        DungeonPersonalityPolicy.Direction.UP,
                        engage,
                        highCombat),
                0.0);
    }

    @Test
    public void progressMonitorUsesStallThresholdAndBrainCooldown() {
        DungeonState state = openState(9, 9, 2, 2, 7, 7);
        state.turn = 0;
        DungeonProgressMonitor.Snapshot initial = DungeonProgressMonitor.initial(state);

        state.turn = 48;
        DungeonProgressMonitor.Result cooldownBlocked = DungeonProgressMonitor.observe(
                initial, state, 0);
        assertFalse(cooldownBlocked.progressed);
        assertFalse(cooldownBlocked.shouldReplan);

        state.turn = 96;
        DungeonProgressMonitor.Result replan = DungeonProgressMonitor.observe(
                initial, state, 0);
        assertTrue(replan.shouldReplan);

        state.visited[2][3] = true;
        state.turn = 97;
        DungeonProgressMonitor.Result progressed = DungeonProgressMonitor.observe(
                replan.snapshot, state, 96);
        assertTrue(progressed.progressed);
        assertFalse(progressed.shouldReplan);

        state.turn = 144;
        DungeonProgressMonitor.Result notYetStalled = DungeonProgressMonitor.observe(
                progressed.snapshot, state, 96);
        assertFalse(notYetStalled.shouldReplan);
    }

    @Test
    public void strategyGateRejectsRoutineAndPeriodicReasons() {
        assertTrue(DungeonCognitionGate.isStrategyTrigger(
                DungeonCognitionGate.OBJECTIVE_CHANGED));
        assertTrue(DungeonCognitionGate.isStrategyTrigger(
                DungeonCognitionGate.PROGRESS_STALLED));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.PERIODIC));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.FLOOR_START));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.ENEMY_SPOTTED));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.STAIRS_SPOTTED));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.HP_RISK));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.COMBAT_CHANGE));
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
                state,
                DungeonCognitionGate.OBJECTIVE_CHANGED,
                objective,
                plan);
        assertEquals(DungeonObjective.REACH_TOP,
                runtime.optJSONObject("objective").optString("type"));
        assertEquals(10, runtime.optJSONObject("objective").optInt("target_floor"));
        assertNotNull(runtime.optJSONObject("existing_plan"));
        JSONObject stairs = runtime.optJSONObject("stairs");
        assertFalse(stairs.optBoolean("known", true));
        assertFalse(stairs.has("x"));
        assertFalse(stairs.has("y"));
    }

    @Test
    public void localObjectiveExecutionCanReachTopWithoutApi() {
        for (long seed = 100L; seed < 104L; seed++) {
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
