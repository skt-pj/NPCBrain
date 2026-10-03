package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class Gpt6LunaSourceTest {
    @Test
    public void cloudRoutingSupportsBothLunaGenerations() throws Exception {
        String models = read("src/main/java/com/sktpj/npcbrain/NpcInferenceModel.java");
        String client = read("src/main/java/com/sktpj/npcbrain/OpenAiClient.java");
        String routing = read("src/main/java/com/sktpj/npcbrain/RoutingSettingsStore.java");
        String settings = read("src/main/java/com/sktpj/npcbrain/SettingsActivity.java");

        assertTrue(models.contains("OPENAI_GPT56_LUNA"));
        assertTrue(models.contains("OPENAI_GPT6_LUNA"));
        assertTrue(models.contains("gpt-5.6-luna"));
        assertTrue(models.contains("gpt-6-luna"));
        assertTrue(client.contains("selectedOpenAiApiModel"));
        assertTrue(client.contains("response.optString(\"model\", MODEL)"));
        assertTrue(routing.contains("OPENAI_GPT6_LUNA"));
        assertTrue(settings.contains("GPT-6 Luna / GPT-5.6 Luna"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
