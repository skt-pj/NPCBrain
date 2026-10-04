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
        String runtime = read("src/main/java/com/sktpj/npcbrain/ClefSpecialistRuntime.java");
        String repository = read("src/main/java/com/sktpj/npcbrain/ClefLocalModelRepository.java");
        String nativeSource = read("src/main/cpp/clef_jni.cpp");
        String cmake = read("src/main/cpp/CMakeLists.txt");
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");

        assertTrue(runtime.contains("ClefNativeRuntime.evaluate("));
        assertTrue(runtime.contains("ClefLocalExecutionQueue.execute("));
        assertFalse(runtime.contains("ClefDecisionClient"));
        assertTrue(repository.contains("ggml-org/Clef-Flash-GGUF"));
        assertTrue(repository.contains("Clef-Flash-Q4_K_M.gguf"));
        assertTrue(nativeSource.contains("\"general.architecture\""));
        assertTrue(nativeSource.contains("!= \"clef\""));
        assertTrue(nativeSource.contains("llama_batch_ext_set_decision_order"));
        assertTrue(nativeSource.contains("LLAMA_DECISION_ORDER_OPTION"));
        assertTrue(nativeSource.contains("CLEF_CONTEXT_TOKENS = 2048"));
        assertTrue(nativeSource.contains("append_bounded_state"));
        assertTrue(cmake.contains("99b95488cac0f00ce3f05af113a8c1e287753f87"));

        assertTrue(settings.contains("CLEF-Flash 共通設定"));
        assertTrue(settings.contains("CLEFモデルをダウンロード"));
        assertTrue(settings.contains("分割脳9専門"));
        assertFalse(settings.contains("Cloudflare Account ID"));
        assertFalse(settings.contains("Cloudflare API token"));
    }

    @Test
    public void localClefBuildAndRuntimeSupportGpuWithCpuFallback() throws Exception {
        String nativeSource = read("src/main/cpp/clef_jni.cpp");
        String cmake = read("src/main/cpp/CMakeLists.txt");
        String vulkanPatch = read("src/main/cpp/PatchLlamaAndroidVulkan.cmake");

        assertTrue(cmake.contains("set(GGML_VULKAN ON CACHE BOOL \"\" FORCE)"));
        assertTrue(cmake.contains("Vulkan_GLSLC_EXECUTABLE"));
        assertTrue(cmake.contains("SPIRV-Headers"));
        assertTrue(cmake.contains("OVERRIDE_FIND_PACKAGE"));
        assertTrue(cmake.contains("Vulkan-Headers"));
        assertTrue(cmake.contains("PatchLlamaAndroidVulkan.cmake"));
        assertTrue(vulkanPatch.contains("vkGetInstanceProcAddr"));
        assertTrue(vulkanPatch.contains("vkGetPhysicalDeviceFeatures2KHR"));
        assertTrue(nativeSource.contains("enum class ClefBackendMode"));
        assertTrue(nativeSource.contains("model_params.n_gpu_layers = gpu ? -1 : 0"));
        assertTrue(nativeSource.contains("context_params.offload_kqv = gpu"));
        assertTrue(nativeSource.contains("context_params.op_offload = gpu"));
        assertTrue(nativeSource.contains("run_decision_once_locked("));
        assertTrue(nativeSource.contains("ClefBackendMode::GPU"));
        assertTrue(nativeSource.contains("ClefBackendMode::CPU"));
        assertTrue(nativeSource.contains("GPU failed; retrying CPU"));
        assertFalse(nativeSource.contains("OpenAiClient"));
        assertFalse(nativeSource.contains("LocalLlmRuntime"));
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
