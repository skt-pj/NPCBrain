package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DungeonWalkAnimationPolicyTest {
    @Test
    public void directionRowsFollowSpriteSheetContract() {
        assertEquals(DungeonWalkAnimationPolicy.DOWN,
                DungeonWalkAnimationPolicy.directionRow(0, 1, DungeonWalkAnimationPolicy.UP));
        assertEquals(DungeonWalkAnimationPolicy.LEFT,
                DungeonWalkAnimationPolicy.directionRow(-1, 0, DungeonWalkAnimationPolicy.DOWN));
        assertEquals(DungeonWalkAnimationPolicy.RIGHT,
                DungeonWalkAnimationPolicy.directionRow(1, 0, DungeonWalkAnimationPolicy.DOWN));
        assertEquals(DungeonWalkAnimationPolicy.UP,
                DungeonWalkAnimationPolicy.directionRow(0, -1, DungeonWalkAnimationPolicy.DOWN));
        assertEquals(DungeonWalkAnimationPolicy.LEFT,
                DungeonWalkAnimationPolicy.directionRow(0, 0, DungeonWalkAnimationPolicy.LEFT));
    }

    @Test
    public void onlySameStateSameFloorCardinalStepStartsWalk() {
        assertTrue(DungeonWalkAnimationPolicy.isSingleStep(true, true, 1, 0));
        assertTrue(DungeonWalkAnimationPolicy.isSingleStep(true, true, -1, 0));
        assertTrue(DungeonWalkAnimationPolicy.isSingleStep(true, true, 0, 1));
        assertTrue(DungeonWalkAnimationPolicy.isSingleStep(true, true, 0, -1));
        assertFalse(DungeonWalkAnimationPolicy.isSingleStep(true, true, 0, 0));
        assertFalse(DungeonWalkAnimationPolicy.isSingleStep(true, true, 1, 1));
        assertFalse(DungeonWalkAnimationPolicy.isSingleStep(true, true, 2, 0));
        assertFalse(DungeonWalkAnimationPolicy.isSingleStep(false, true, 1, 0));
        assertFalse(DungeonWalkAnimationPolicy.isSingleStep(true, false, 1, 0));
    }

    @Test
    public void framesAdvanceAcrossWalkAndReturnToIdle() {
        assertEquals(0f, DungeonWalkAnimationPolicy.progress(0L), 0f);
        assertEquals(0, DungeonWalkAnimationPolicy.frameIndex(0L));
        assertEquals(0, DungeonWalkAnimationPolicy.frameIndex(59L));
        assertEquals(1, DungeonWalkAnimationPolicy.frameIndex(60L));
        assertEquals(2, DungeonWalkAnimationPolicy.frameIndex(120L));
        assertEquals(3, DungeonWalkAnimationPolicy.frameIndex(180L));
        assertTrue(DungeonWalkAnimationPolicy.isActive(239L));
        assertFalse(DungeonWalkAnimationPolicy.isActive(240L));
        assertEquals(0, DungeonWalkAnimationPolicy.frameIndex(240L));
        assertEquals(1f, DungeonWalkAnimationPolicy.progress(240L), 0f);
    }
}
