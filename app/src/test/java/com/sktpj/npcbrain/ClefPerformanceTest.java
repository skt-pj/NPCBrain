package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ClefPerformanceTest {
    @Test
    public void snapshotComputesAverageAndFormatsMemory() {
        ClefPerformanceStore.Snapshot snapshot = new ClefPerformanceStore.Snapshot(
                4L,
                3L,
                1L,
                1000L,
                300L,
                400L,
                100000L,
                120000L,
                130000L,
                10L * 1024L * 1024L,
                12L * 1024L * 1024L,
                14L * 1024L * 1024L,
                1L,
                "Clef-flash 9B Q4_K_M · ローカル",
                true,
                "");
        assertEquals(250L, snapshot.averageDurationMs());
        assertEquals("1.0 MiB", ClefPerformanceStore.formatBytes(1024L * 1024L));
        assertEquals("1.0 MiB", ClefPerformanceStore.formatKiB(1024L));
    }

    @Test
    public void specialistDecisionRuntimeMeasuresTheRealRequestOnly() throws Exception {
        String runtime = read("src/main/java/com/sktpj/npcbrain/SpecialistDecisionModelRuntime.java");
        assertTrue(runtime.contains("SystemClock.elapsedRealtimeNanos()"));
        assertTrue(runtime.contains("Debug.getPss()"));
        assertTrue(runtime.contains("new ClefPerformanceStore(appContext).record("));
        assertEquals(1, occurrences(runtime, "ClefNativeRuntime.decide("));
        assertTrue(runtime.contains("localRepository.isDownloaded()"));
        assertTrue(runtime.contains("CloudflareDecisionModelClient"));

        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("判断モデル 実測パフォーマンス"));
        assertTrue(settings.contains("実測データをリセット"));
        assertTrue(settings.contains("clefPerformanceStore.snapshot().displayText()"));
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int cursor = 0;
        while (true) {
            int at = source.indexOf(target, cursor);
            if (at < 0) return count;
            count++;
            cursor = at + target.length();
        }
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
