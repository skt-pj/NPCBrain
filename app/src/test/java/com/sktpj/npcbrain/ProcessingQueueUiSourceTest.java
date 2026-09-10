package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProcessingQueueUiSourceTest {
    @Test
    public void queueUiAggregatesTopLevelByNpcAndExpandsInternalDetails() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/ProcessingQueueActivity.java");
        String viewModel = read("src/main/java/com/sktpj/npcbrain/NpcBrainQueueViewModel.java");

        assertTrue(source.contains("NpcBrainQueueViewModel.group"));
        assertTrue(source.contains("NPCごとの脳処理"));
        assertTrue(source.contains("タップして内部状況を見る"));
        assertTrue(source.contains("addExpandedDetails"));
        assertTrue(source.contains("internalRow"));
        assertTrue(source.contains("9専門Brain・Global Workspace"));

        // LLM invocation details are retained in the expanded NPC detail view, not erased.
        assertTrue(viewModel.contains("\"llm_request\".equals(entry.type)"));
        assertTrue(viewModel.contains("専門Brain"));
        assertTrue(viewModel.contains("Global Workspace"));

        // Rejected concepts must never return as top-level queue terminology.
        assertFalse(source.contains("親処理"));
        assertFalse(source.contains("ローカルLLM FIFOキュー"));
        assertFalse(source.contains("自発送信判断"));
    }

    @Test
    public void expandedInternalRowsKeepQueueTimingFields() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/ProcessingQueueActivity.java");
        assertTrue(source.contains("キュー投入"));
        assertTrue(source.contains("デキュー"));
        assertTrue(source.contains("待機時間"));
        assertTrue(source.contains("実処理時間"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
