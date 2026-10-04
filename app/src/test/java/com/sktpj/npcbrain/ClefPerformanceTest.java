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
                "clef-flash",
                true,
                "");
        assertEquals(250L, snapshot.averageDurationMs());
        assertEquals("1.0 MiB", ClefPerformanceStore.formatBytes(1024L * 1024L));
        assertEquals("1.0 MiB", ClefPerformanceStore.formatKiB(1024L));
    }

    @Test
    public void runtimeMeasuresExistingClefCallWithoutAddingProbeRequest() throws Exception {
        String runtime = read("src/main/java/com/sktpj/npcbrain/ClefSpecialistRuntime.java");
        assertTrue(runtime.contains("SystemClock.elapsedRealtimeNanos()"));
        assertTrue(runtime.contains("Debug.getPss()"));
        assertTrue(runtime.contains("new ClefPerformanceStore(appContext).record("));
        assertEquals(1, occurrences(runtime, "ClefNativeRuntime.evaluate("));
        assertTrue(runtime.contains("modelRepository.isDownloaded()"));

        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("CLEF 実測パフォーマンス"));
        assertTrue(settings.contains("実測データをリセット"));
        assertTrue(settings.contains("clefPerformanceStore.snapshot().displayText()"));
        assertTrue(settings.contains("ローカルCLEF 9専門を実測"));
        assertTrue(settings.contains("APIキー不要"));
        assertTrue(settings.contains("ClefPerformanceProbe.run("));
        assertTrue(settings.contains("APIキー不要・外部通信なし"));
        assertTrue(settings.contains("usesAnyOpenAiRoute()"));

        String probe = read("src/main/java/com/sktpj/npcbrain/ClefPerformanceProbe.java");
        assertTrue(probe.contains("ClefNativeRuntime.evaluate("));
        assertTrue(probe.contains("ClefLocalExecutionQueue.execute("));
        assertTrue(probe.contains("BrainEngine.specialistIds()"));
        assertTrue(probe.contains("ClefPerformanceStore"));
        assertTrue(probe.contains("ProgressListener"));
        assertTrue(probe.contains("listener.onProgress"));
        assertTrue(!probe.contains("SecureApiKeyStore"));
        assertTrue(!probe.contains("OpenAiClient"));
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