package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class BrainParallelArchitectureTest {
    @Test
    public void keepsNineSpecialistsAndOneWorkspaceWithNineWayFanOut() {
        assertEquals(9, BrainEngine.moduleCount());
        assertEquals(9, BrainEngine.specialistParallelism());
        assertEquals(10, BrainEngine.stageIds().length);
        assertEquals("global_workspace", BrainEngine.stageIds()[9]);
        assertEquals("parallel_specialists_then_global_workspace", BrainEngine.executionMode());
    }
}
