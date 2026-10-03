package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClefLocalRuntimeSourceTest {
    @Test
    public void productionClefPathIsLocalAndUsesIntegratedJointHeadModel() throws Exception {
        String runtime = read("src/main/java/com/sktpj/npcbrain/ClefActionSelectionRuntime.java");
        String repository = read("src/main/java/com/sktpj/npcbrain/ClefLocalModelRepository.java");
        String nativeSource = read("src/main/cpp/clef_jni.cpp");
        String cmake = read("src/main/cpp/CMakeLists.txt");
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");

        assertTrue(runtime.contains("ClefNativeRuntime.decide("));
        assertFalse(runtime.contains("ClefDecisionClient"));
        assertTrue(repository.contains("ggml-org/Clef-Flash-GGUF"));
        assertTrue(repository.contains("Clef-Flash-Q4_K_M.gguf"));
        assertTrue(nativeSource.contains("\"general.architecture\""));
        assertTrue(nativeSource.contains("!= \"clef\""));
        assertTrue(nativeSource.contains("llama_batch_ext_set_decision_order"));
        assertTrue(nativeSource.contains("LLAMA_DECISION_ORDER_OPTION"));
        assertTrue(cmake.contains("99b95488cac0f00ce3f05af113a8c1e287753f87"));

        assertTrue(settings.contains("ローカル CLEF 行動選択"));
        assertTrue(settings.contains("ローカルモデルをダウンロード"));
        assertFalse(settings.contains("Cloudflare Account ID"));
        assertFalse(settings.contains("Cloudflare API token"));
    }

    @Test
    public void remoteClefCredentialAndHttpClientSourcesAreRemoved() {
        assertFalse(Files.exists(Paths.get(
                "src/main/java/com/sktpj/npcbrain/ClefDecisionClient.java")));
        assertFalse(Files.exists(Paths.get(
                "src/main/java/com/sktpj/npcbrain/SecureCloudflareTokenStore.java")));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
