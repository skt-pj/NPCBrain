package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class ClefLocalRuntimeSourceTest {
    @Test
    public void localDecisionPathUsesIntegratedJointHeadModel() throws Exception {
        String runtime = read("src/main/java/com/sktpj/npcbrain/SpecialistDecisionModelRuntime.java");
        String repository = read("src/main/java/com/sktpj/npcbrain/ClefLocalModelRepository.java");
        String nativeSource = read("src/main/cpp/clef_jni.cpp");
        String cmake = read("src/main/cpp/CMakeLists.txt");

        assertTrue(runtime.contains("ClefNativeRuntime.decide("));
        assertTrue(repository.contains("ggml-org/Clef-Flash-GGUF"));
        assertTrue(repository.contains("Clef-Flash-Q4_K_M.gguf"));
        assertTrue(nativeSource.contains("\"general.architecture\""));
        assertTrue(nativeSource.contains("!= \"clef\""));
        assertTrue(nativeSource.contains("question_instruction"));
        assertTrue(nativeSource.contains("evidence_sufficient"));
        assertTrue(nativeSource.contains("llama_batch_ext_set_decision_order"));
        assertTrue(cmake.contains("99b95488cac0f00ce3f05af113a8c1e287753f87"));
    }

    @Test
    public void cloudDecisionPathUsesSystemOneWorkersAiAndSecureToken() throws Exception {
        String client = read("src/main/java/com/sktpj/npcbrain/CloudflareDecisionModelClient.java");
        String token = read("src/main/java/com/sktpj/npcbrain/SecureCloudflareTokenStore.java");
        String catalog = read("src/main/java/com/sktpj/npcbrain/DecisionModelCatalog.java");

        assertTrue(client.contains("/ai/run/"));
        assertTrue(client.contains("\"type\", \"choice\""));
        assertTrue(client.contains("\"type\", \"noul\""));
        assertTrue(catalog.contains("@cf/cloudflare/clef-flash"));
        assertTrue(catalog.contains("@cf/cloudflare/clef"));
        assertTrue(token.contains("AndroidKeyStore"));
        assertTrue(token.contains("AES/GCM/NoPadding"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
