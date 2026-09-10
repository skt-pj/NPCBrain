package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProcessingQueueUiSourceTest {
    @Test
    public void queueUiShowsOnlyLogicalProcessingItems() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/ProcessingQueueActivity.java");

        assertTrue(source.contains("!\"llm_request\".equals(entry.type)"));
        assertTrue(source.contains("現在の処理"));
        assertTrue(source.contains("直近の処理結果"));
        assertTrue(source.contains("Brain内部のLLM呼び出しは表示しません"));

        assertFalse(source.contains("親処理"));
        assertFalse(source.contains("ローカルLLM FIFOキュー"));
        assertFalse(source.contains("直近のローカルLLM結果"));
        assertFalse(source.contains("LLM推論"));
    }

    @Test
    public void logicalCardsKeepQueueTimingFields() throws Exception {
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
