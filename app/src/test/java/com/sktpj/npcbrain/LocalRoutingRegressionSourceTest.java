package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LocalRoutingRegressionSourceTest {
    @Test
    public void conversationOnlyRequiresApiKeyWhenRoomRoutesToOpenAi() throws Exception {
        String shell = read("src/main/java/com/sktpj/npcbrain/WorldShellActivityV212.java");
        assertTrue(shell.contains("apiKey.isEmpty() && demoRuntime.roomUsesOpenAi(currentRoomId)"));
        assertFalse(shell.contains("if (apiKey.isEmpty()) {\n            Toast.makeText(this, \"設定タブでOpenAI APIキーを設定してください\""));

        String runtime = read("src/main/java/com/sktpj/npcbrain/DemoRuntimeV032.java");
        assertTrue(runtime.contains("boolean roomUsesOpenAi(String roomId)"));
        assertTrue(runtime.contains("NpcInferenceAccess.usesOpenAi(appContext, npcId)"));
    }

    @Test
    public void legacyNpcModelDoesNotSilentlyOverrideSharedDefaults() throws Exception {
        String store = read("src/main/java/com/sktpj/npcbrain/NpcModelStore.java");
        assertTrue(store.contains("remove(LEGACY_SELECTED_MODEL)"));
        assertFalse(store.contains("return legacyOverride()"));
        assertTrue(store.contains("return preferences.contains(GLOBAL_OVERRIDE)"));
        assertTrue(store.contains("return preferences.contains(SPECIALIST_OVERRIDE)"));
    }

    @Test
    public void clefNativePromptBoundsStateToExactContextBudget() throws Exception {
        String nativeSource = read("../app/src/main/cpp/clef_jni.cpp");
        assertTrue(nativeSource.contains("append_bounded_state"));
        assertTrue(nativeSource.contains("state_budget"));
        assertTrue(nativeSource.contains("fixed.tokens.size()"));
        assertTrue(nativeSource.contains("CLEF_CONTEXT_TOKENS"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
