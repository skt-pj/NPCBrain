package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class NpcBrainQueueViewModelTest {
    @Before
    public void setUp() {
        ProcessingQueueRegistry.clearForTests();
    }

    @After
    public void tearDown() {
        ProcessingQueueRegistry.clearForTests();
    }

    @Test
    public void nineSpecialistRequestsAreOneNpcBrainGroup() {
        for (int i = 0; i < 9; i++) {
            ProcessingQueueRegistry.startRunning(
                    "llm_request",
                    "npc9",
                    "local_light · brain_stage=perception",
                    1_000L + i);
        }
        ProcessingQueueRegistry.startRunning(
                "llm_request",
                "npc10",
                "local_light · brain_stage=salience",
                2_000L);
        ProcessingQueueRegistry.startRunning(
                "spontaneous_cognition",
                "",
                "foreground spontaneous",
                900L);

        List<NpcBrainQueueViewModel.BrainGroup> groups = NpcBrainQueueViewModel.group(
                ProcessingQueueRegistry.snapshot());

        assertEquals(2, groups.size());
        assertEquals("npc9", groups.get(0).npcId);
        assertEquals(9, groups.get(0).active.size());
        assertEquals(9, groups.get(0).activeLlmCount());
        assertEquals("npc10", groups.get(1).npcId);
        assertEquals(1, groups.get(1).active.size());
    }

    @Test
    public void activeAndRecentEntriesForSameNpcRemainOneGroup() {
        String completed = ProcessingQueueRegistry.startRunning(
                "llm_request",
                "npc9",
                "local_light · brain_stage=world_model",
                1_000L);
        ProcessingQueueRegistry.markRunning(completed, 1_200L);
        ProcessingQueueRegistry.markCompleted(completed, 1_500L, "done");
        ProcessingQueueRegistry.startRunning(
                "llm_request",
                "npc9",
                "local_light · brain_stage=valuation",
                2_000L);

        List<NpcBrainQueueViewModel.BrainGroup> groups = NpcBrainQueueViewModel.group(
                ProcessingQueueRegistry.snapshot());

        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).active.size());
        assertEquals(1, groups.get(0).recent.size());
        assertTrue(groups.get(0).hasActive());
    }

    @Test
    public void internalStageLabelUsesBrainModuleIdentity() {
        ProcessingQueueRegistry.Entry specialist = new ProcessingQueueRegistry.Entry(
                "q1",
                "llm_request",
                "npc9",
                "local_light · brain_stage=episodic_memory · purpose=cognition",
                ProcessingQueueRegistry.Status.QUEUED,
                1_000L,
                0L,
                0L,
                "");
        ProcessingQueueRegistry.Entry workspace = new ProcessingQueueRegistry.Entry(
                "q2",
                "llm_request",
                "npc9",
                "local_light · brain_stage=global_workspace · purpose=cognition",
                ProcessingQueueRegistry.Status.RUNNING,
                1_000L,
                1_100L,
                0L,
                "");

        assertEquals("専門Brain · エピソード記憶", NpcBrainQueueViewModel.internalLabel(specialist));
        assertEquals("Global Workspace", NpcBrainQueueViewModel.internalLabel(workspace));
        assertEquals("ローカル・軽い", NpcBrainQueueViewModel.modelLabel(specialist));
        assertFalse(NpcBrainQueueViewModel.belongsToNpcBrain(new ProcessingQueueRegistry.Entry(
                "q3", "spontaneous_cognition", "", "", ProcessingQueueRegistry.Status.RUNNING,
                1L, 1L, 0L, "")));
    }
}
