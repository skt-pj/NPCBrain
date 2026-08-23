package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DungeonAiStaminaPolicyTest {
    private static final double EPS = 0.000001;

    @Test
    public void costUsesUncachedCachedAndOutputRates() {
        assertEquals(0.20 * DungeonTokenCostPolicy.USD_TO_JPY,
                DungeonTokenCostPolicy.costJpy(1_000_000L, 0L, 0L), EPS);
        assertEquals(0.02 * DungeonTokenCostPolicy.USD_TO_JPY,
                DungeonTokenCostPolicy.costJpy(1_000_000L, 1_000_000L, 0L), EPS);
        assertEquals(1.20 * DungeonTokenCostPolicy.USD_TO_JPY,
                DungeonTokenCostPolicy.costJpy(0L, 0L, 1_000_000L), EPS);
    }

    @Test
    public void remainingBudgetClampsToTenYen() {
        assertEquals(10.0, DungeonTokenCostPolicy.remainingJpy(0.0), EPS);
        assertEquals(100, DungeonTokenCostPolicy.remainingPercent(0.0));
        assertEquals(5.0, DungeonTokenCostPolicy.remainingJpy(5.0), EPS);
        assertEquals(50, DungeonTokenCostPolicy.remainingPercent(5.0));
        assertEquals(0.0, DungeonTokenCostPolicy.remainingJpy(12.0), EPS);
        assertEquals(0, DungeonTokenCostPolicy.remainingPercent(12.0));
    }

    @Test
    public void dungeonBrainUsesSmallOutputCaps() {
        for (int i = 1; i <= BrainEngine.moduleCount(); i++) {
            assertEquals(256, DungeonBrainRuntime.outputLimitForOrdinal(i));
        }
        assertEquals(768, DungeonBrainRuntime.outputLimitForOrdinal(BrainEngine.moduleCount() + 1));
        assertEquals(8192, OpenAiClient.DEFAULT_MAX_OUTPUT_TOKENS);
        assertEquals(8192, OpenAiClient.normalizeMaxOutputTokens(99_999));
    }

    @Test
    public void normalDungeonSignalsRemainLocalOnly() {
        assertTrue(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.OBJECTIVE_CHANGED));
        assertTrue(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.PROGRESS_STALLED));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.FLOOR_START));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.ENEMY_SPOTTED));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.STAIRS_SPOTTED));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.HP_RISK));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.COMBAT_CHANGE));
        assertFalse(DungeonCognitionGate.isStrategyTrigger(DungeonCognitionGate.PERIODIC));
    }
}
