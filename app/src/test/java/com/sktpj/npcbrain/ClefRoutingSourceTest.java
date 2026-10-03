package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClefRoutingSourceTest {
    @Test
    public void clefRoutesAllNineSpecialistsIndependentlyWithoutAddingBrainStage() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/BrainEngine.java");
        String runtime = read("src/main/java/com/sktpj/npcbrain/ClefSpecialistRuntime.java");
        String queue = read("src/main/java/com/sktpj/npcbrain/ClefLocalExecutionQueue.java");

        assertTrue(source.contains("decisionSpecialists"));
        assertTrue(source.contains("clefSpecialistRuntime.request(module.id, decisionModelState)"));
        assertTrue(source.contains("decision_model_queue"));
        assertTrue(source.contains("independent_fifo_items"));
        assertFalse(source.contains("\"action_selection\".equals(module.id)"));
        assertTrue(runtime.contains("ProcessingQueueRegistry.enqueue("));
        assertTrue(runtime.contains("\"decision_model\""));
        assertTrue(queue.contains("new LinkedBlockingQueue<>()"));
        assertTrue(queue.contains("new ThreadPoolExecutor("));
        assertTrue(source.contains("parallel_specialists_then_global_workspace"));

        assertEquals(9, BrainEngine.moduleCount());
        assertEquals(10, BrainEngine.stageIds().length);
        assertEquals("global_workspace", BrainEngine.stageIds()[9]);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
