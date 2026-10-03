package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class RoutingSettingsSourceTest {
    @Test
    public void sharedSettingsExposeTwoBrainRoutesAndProviderSettings() throws Exception {
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("脳への割り当て"));
        assertTrue(settings.contains("Global Workspace"));
        assertTrue(settings.contains("Specialist Brain"));
        assertTrue(settings.contains("OpenAI 共通設定"));
        assertTrue(settings.contains("routingSettingsStore.setGlobalModel"));
        assertTrue(settings.contains("routingSettingsStore.setSpecialistModel"));
    }

    @Test
    public void runtimeChoosesModelByBrainStage() throws Exception {
        String client = read("src/main/java/com/sktpj/npcbrain/OpenAiClient.java");
        assertTrue(client.contains("diagnosticBrainStage(fullPrompt)"));
        assertTrue(client.contains("effectiveModelForStage(stage)"));

        String store = read("src/main/java/com/sktpj/npcbrain/NpcModelStore.java");
        assertTrue(store.contains("effectiveGlobalModel()"));
        assertTrue(store.contains("effectiveSpecialistModel()"));
        assertTrue(store.contains("\"global_workspace\".equals(stageId)"));
    }

    @Test
    public void debugNpcSettingsAreOverridesOfCommonDefaults() throws Exception {
        String manager = read("src/main/java/com/sktpj/npcbrain/NpcManagerActivity.java");
        assertTrue(manager.contains("共通設定 · "));
        assertTrue(manager.contains("Global Workspace 上書き"));
        assertTrue(manager.contains("Specialist Brain 上書き"));
        assertTrue(manager.contains("clearGlobalOverride()"));
        assertTrue(manager.contains("clearSpecialistOverride()"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
