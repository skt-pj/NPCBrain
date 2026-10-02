package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ClefRoutingSourceTest {
    @Test
    public void clefReplacesOnlyActionSelectionRequestWithoutAddingBrainStage() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/sktpj/npcbrain/BrainEngine.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("\"action_selection\".equals(module.id)"));
        assertTrue(source.contains("clefActionSelectionRuntime.request(prompt.fullText())"));
        assertTrue(source.contains("parallel_specialists_then_global_workspace"));

        assertEquals(9, BrainEngine.moduleCount());
        assertEquals(10, BrainEngine.stageIds().length);
        assertEquals("global_workspace", BrainEngine.stageIds()[9]);
    }
}
