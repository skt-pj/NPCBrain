package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AiManagementHierarchySourceTest {
    @Test
    public void aiManagementUsesLocationThenModelThenDetailHierarchy() throws Exception {
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("実行場所 → モデル → 詳細"));
        assertTrue(settings.contains("① 実行場所"));
        assertTrue(settings.contains("② モデル"));
        assertTrue(settings.contains("③ 詳細"));
        assertTrue(settings.contains("showModelPicker"));
        assertTrue(settings.contains("showLocalDetailDialog"));
        assertTrue(settings.contains("showOpenAiDetailDialog"));
        assertTrue(settings.contains("詳細を選択"));
    }

    @Test
    public void realModelNamesReplaceLightMediumHeavyAsSelections() throws Exception {
        String models = read("src/main/java/com/sktpj/npcbrain/NpcInferenceModel.java");
        assertTrue(models.contains("Qwen2 0.5B Instruct"));
        assertTrue(models.contains("Qwen2.5 1.5B Instruct"));
        assertTrue(models.contains("Gemma 4 E2B"));
        assertTrue(models.contains("GPT-5.6 Luna"));
        assertTrue(models.contains("GPT-6 Luna"));
        assertFalse(models.contains("軽い（ローカル）"));
        assertFalse(models.contains("中（ローカル）"));
        assertFalse(models.contains("重い（ローカル）"));
    }

    @Test
    public void clefIsOnlyActionSelectionChoice() throws Exception {
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("Specialist Brainと同じ"));
        assertTrue(settings.contains("CLEF-Flash 9B Q4_K_M"));
        assertTrue(settings.contains("CLEFは一般モデルではなくAction Selection専用です"));
        String models = read("src/main/java/com/sktpj/npcbrain/NpcInferenceModel.java");
        assertFalse(models.contains("CLEF-Flash"));
    }

    @Test
    public void localDetailCheckboxesAffectRuntime() throws Exception {
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("GPUを優先する"));
        assertTrue(settings.contains("長い入力を自動圧縮する"));
        assertTrue(settings.contains("JSON失敗時に1回再試行する"));

        String runtime = read("src/main/java/com/sktpj/npcbrain/LocalLlmRuntime.java");
        assertTrue(runtime.contains("localSettings.preferGpu()"));
        assertTrue(runtime.contains("localSettings.autoCompact()"));
        assertTrue(runtime.contains("localSettings.retryInvalidJson()"));
        assertTrue(runtime.contains("resetEngines()"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
