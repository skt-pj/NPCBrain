package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NpcInferenceModelTest {
    @Test
    public void defaultAndUnknownNormalizeToLocalLight() {
        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize(null));
        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize(""));
        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize("unknown"));
    }

    @Test
    public void onlyOpenAiLunaUsesOpenAi() {
        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_LIGHT));
        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_MEDIUM));
        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_HEAVY));
        assertTrue(NpcInferenceModel.usesOpenAi(NpcInferenceModel.OPENAI_LUNA));
    }

    @Test
    public void localModelSpecsArePinned() {
        LocalModelRepository.ModelSpec light = LocalModelRepository.spec(NpcInferenceModel.LOCAL_LIGHT);
        assertEquals("litert-community/Qwen2-0.5B-Instruct", light.repository);
        assertEquals("0e209e163e1bc302c19d0fb67101e6ca2cdd1fcb", light.revision);
        assertEquals("Qwen2_0.5B_Instruct.litertlm", light.fileName);
        assertEquals(647377840L, light.expectedSizeBytes);
        assertTrue(light.downloadUrl().contains(light.revision));

        LocalModelRepository.ModelSpec medium = LocalModelRepository.spec(NpcInferenceModel.LOCAL_MEDIUM);
        assertEquals("litert-community/Qwen2.5-1.5B-Instruct", medium.repository);
        assertEquals("19edb84c69a0212f29a6ef17ba0d6f278b6a1614", medium.revision);
        assertEquals("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", medium.fileName);

        LocalModelRepository.ModelSpec heavy = LocalModelRepository.spec(NpcInferenceModel.LOCAL_HEAVY);
        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", heavy.repository);
        assertEquals("6e5c4f1e395deb959c494953478fa5cec4b8008f", heavy.revision);
        assertEquals("gemma-4-E2B-it.litertlm", heavy.fileName);
    }

    @Test
    public void downloadStatusTextDistinguishesAllStates() {
        LocalModelDownloadManager.Snapshot missing = new LocalModelDownloadManager.Snapshot(
                false, false, 0L, -1L, "");
        assertEquals("モデルデータ  未ダウンロード", missing.displayText());

        LocalModelDownloadManager.Snapshot downloading = new LocalModelDownloadManager.Snapshot(
                false, true, 50L, 100L, "");
        assertTrue(downloading.displayText().contains("ダウンロード中 50%"));

        LocalModelDownloadManager.Snapshot ready = new LocalModelDownloadManager.Snapshot(
                true, false, 1024L, 1024L, "");
        assertTrue(ready.displayText().contains("ダウンロード済み"));

        LocalModelDownloadManager.Snapshot failed = new LocalModelDownloadManager.Snapshot(
                false, false, 0L, -1L, "network failed");
        assertTrue(failed.displayText().contains("ERROR"));
        assertTrue(failed.displayText().contains("network failed"));
    }

    @Test
    public void byteFormattingIsReadable() {
        assertEquals("0 B", LocalModelDownloadManager.formatBytes(0L));
        assertEquals("1.0 KiB", LocalModelDownloadManager.formatBytes(1024L));
        assertEquals("1.0 MiB", LocalModelDownloadManager.formatBytes(1024L * 1024L));
        assertEquals("1.00 GiB", LocalModelDownloadManager.formatBytes(1024L * 1024L * 1024L));
    }
}
