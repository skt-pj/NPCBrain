package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AiManagementHierarchySourceTest {
    @Test
    public void specialistsChooseExactlyOneInferenceType() throws Exception {
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("分割脳（9専門）"));
        assertTrue(settings.contains("① 推論方式"));
        assertTrue(settings.contains("通常LLM"));
        assertTrue(settings.contains("判断モデル"));
        assertTrue(settings.contains("addDecisionModelControls"));
        assertTrue(settings.contains("specialistInferenceSettingsStore.setMode"));
        assertFalse(settings.contains("addActionSelectionGroup"));
        assertFalse(settings.contains("Specialist Brainと同じ"));
        assertFalse(settings.contains("Action Selection専用"));
    }

    @Test
    public void llmAndDecisionModelsHaveSeparateCatalogs() throws Exception {
        String llmModels = read("src/main/java/com/sktpj/npcbrain/NpcInferenceModel.java");
        assertTrue(llmModels.contains("Qwen2 0.5B Instruct"));
        assertTrue(llmModels.contains("Qwen2.5 1.5B Instruct"));
        assertTrue(llmModels.contains("Gemma 4 E2B"));
        assertTrue(llmModels.contains("GPT-5.6 Luna"));
        assertTrue(llmModels.contains("GPT-6 Luna"));
        assertFalse(llmModels.contains("Clef-flash"));

        String decisions = read("src/main/java/com/sktpj/npcbrain/DecisionModelCatalog.java");
        assertTrue(decisions.contains("Clef-flash 9B Q4_K_M"));
        assertTrue(decisions.contains("Clef-flash 9B"));
        assertTrue(decisions.contains("Clef 27B"));
        assertTrue(decisions.contains("Cloudflare Workers AI"));
    }

    @Test
    public void localLlmDetailCheckboxesStillAffectLlmRuntime() throws Exception {
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("GPUを優先する"));
        assertTrue(settings.contains("長い入力を自動圧縮する"));
        assertTrue(settings.contains("JSON失敗時に1回再試行する"));

        String runtime = read("src/main/java/com/sktpj/npcbrain/LocalLlmRuntime.java");
        assertTrue(runtime.contains("localSettings.preferGpu()"));
        assertTrue(runtime.contains("localSettings.autoCompact()"));
        assertTrue(runtime.contains("localSettings.retryInvalidJson()"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
