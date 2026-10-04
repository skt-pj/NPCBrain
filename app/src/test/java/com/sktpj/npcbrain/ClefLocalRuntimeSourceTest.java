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
    public void localClefGpuRunsOutOfProcessAndFallsBackToCpuSafely() throws Exception {
        String nativeSource = read("src/main/cpp/clef_jni.cpp");
        String cmake = read("src/main/cpp/CMakeLists.txt");
        String vulkanPatch = read("src/main/cpp/PatchLlamaAndroidVulkan.cmake");
        String manifest = read("src/main/AndroidManifest.xml");
        String nativeRuntime = read("src/main/java/com/sktpj/npcbrain/ClefNativeRuntime.java");
        String nativeBridge = read("src/main/java/com/sktpj/npcbrain/ClefNativeBridge.java");
        String gpuClient = read("src/main/java/com/sktpj/npcbrain/ClefGpuProcessClient.java");
        String gpuService = read("src/main/java/com/sktpj/npcbrain/ClefGpuService.java");
        String gpuHealth = read("src/main/java/com/sktpj/npcbrain/ClefGpuHealthStore.java");
        String application = read("src/main/java/com/sktpj/npcbrain/NPCBrainApplication.java");

        assertTrue(cmake.contains("set(GGML_VULKAN ON CACHE BOOL \"\" FORCE)"));
        assertTrue(cmake.contains("Vulkan_GLSLC_EXECUTABLE"));
        assertTrue(cmake.contains("SPIRV-Headers"));
        assertTrue(cmake.contains("OVERRIDE_FIND_PACKAGE"));
        assertTrue(cmake.contains("Vulkan-Headers"));
        assertTrue(cmake.contains("PatchLlamaAndroidVulkan.cmake"));
        assertTrue(vulkanPatch.contains("vkGetInstanceProcAddr"));
        assertTrue(vulkanPatch.contains("vkGetPhysicalDeviceFeatures2KHR"));

        assertTrue(manifest.contains("android:name=\".ClefGpuService\""));
        assertTrue(manifest.contains("android:process=\":clef_gpu\""));
        assertTrue(gpuService.contains("ClefNativeBridge.evaluateGpuOnly("));
        assertTrue(gpuService.contains("ClefNativeBridge.evaluateCpuOnly("));
        assertTrue(gpuClient.contains("TRANSACTION_GET_PID"));
        assertTrue(gpuClient.contains("TRANSACTION_EVALUATE"));
        assertTrue(gpuClient.contains("future.get("));
        assertTrue(gpuClient.contains("Process.killProcess(servicePid)"));
        assertTrue(gpuClient.contains("ClefGpuHealthStore"));
        assertTrue(gpuHealth.contains("versionCode"));
        assertTrue(application.contains("isClefGpuProcess()"));
        assertTrue(application.contains("if (isClefGpuProcess()) return;"));

        assertTrue(nativeRuntime.contains("ClefGpuProcessClient.evaluate("));
        assertTrue(nativeRuntime.contains("ClefGpuProcessClient.evaluateCpu("));
        assertFalse(nativeRuntime.contains("System.loadLibrary"));
        assertTrue(nativeBridge.contains("System.loadLibrary(\"npcbrain_clef\")"));
        assertTrue(nativeBridge.contains("nativeEvaluateGpuOnly"));
        assertTrue(nativeBridge.contains("nativeEvaluateCpuOnly"));
        assertFalse(nativeRuntime.contains("OpenAiClient"));
        assertFalse(nativeRuntime.contains("LocalLlmRuntime"));

        assertTrue(nativeSource.contains("gpu_layer_limit_for_ram"));
        assertTrue(nativeSource.contains("return 2;"));
        assertTrue(nativeSource.contains("return 4;"));
        assertTrue(nativeSource.contains("return 8;"));
        assertFalse(nativeSource.contains("n_gpu_layers = gpu ? -1 : 0"));
        assertFalse(nativeSource.contains("n_gpu_layers = -1"));
        assertTrue(nativeSource.contains("context_params.offload_kqv = false"));
        assertTrue(nativeSource.contains("context_params.op_offload = false"));
        assertTrue(nativeSource.contains("ClefBackendMode::GPU"));
        assertTrue(nativeSource.contains("ClefBackendMode::CPU"));
        assertFalse(nativeSource.contains("GPU failed; retrying CPU"));
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
