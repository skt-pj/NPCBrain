package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class JevCloudSourceTest {
    @Test
    public void settingsExposeLocalClefAndCloudJevAsDecisionChoices() throws Exception {
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");
        assertTrue(settings.contains("判断モデル（ローカル CLEF-Flash）"));
        assertTrue(settings.contains("判断モデル（クラウド Jev）"));
        assertTrue(settings.contains("SecureTypeSafeApiKeyStore"));
        assertTrue(settings.contains("TypeSafe APIキー"));
        assertTrue(settings.contains("showTypeSafeApiKeyDialog"));
        assertTrue(settings.contains("confirmClearTypeSafeApiKey"));
    }

    @Test
    public void cloudJevDoesNotEnterLocalClefExecutionPath() throws Exception {
        String runtime = read("src/main/java/com/sktpj/npcbrain/ClefSpecialistRuntime.java");
        assertTrue(runtime.contains("DecisionModelSettingsStore.ROUTE_CLOUD_JEV"));
        assertTrue(runtime.contains("new JevClient"));
        assertTrue(runtime.contains("provider=typesafe"));
        assertTrue(runtime.contains("model=jev-latest"));
        assertTrue(runtime.contains("requestJev("));
        assertTrue(runtime.contains("requestLocalClef("));
        assertFalse(runtime.contains("fallback"));
    }

    @Test
    public void typeSafeCredentialUsesSeparateEncryptedStore() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/SecureTypeSafeApiKeyStore.java");
        assertTrue(source.contains("AndroidKeyStore"));
        assertTrue(source.contains("npcbrain_typesafe_key"));
        assertTrue(source.contains("npcbrain_typesafe_api_key"));
        assertFalse(source.contains("npcbrain_openai_key"));
    }

    @Test
    public void billingPathsCarryActualModelAndCacheWrites() throws Exception {
        String client = read("src/main/java/com/sktpj/npcbrain/OpenAiClient.java");
        String store = read("src/main/java/com/sktpj/npcbrain/NpcAiStaminaStore.java");
        assertTrue(client.contains("cache_write_tokens"));
        assertTrue(client.contains("response.optString(\"model\""));
        assertTrue(client.contains("ApiPricingPolicy"));
        assertTrue(store.contains("cache_write_input_tokens"));
        assertTrue(store.contains("ApiPricingPolicy.costJpy"));
        assertFalse(store.contains("DungeonTokenCostPolicy.costJpy("));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
