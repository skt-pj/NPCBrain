package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalLlmRuntimeSourceTest {
    @Test
    public void localJsonKeepsSchemaConstraintWithoutShapeSensitiveLogitsProcessors() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/LocalLlmRuntime.java");

        assertTrue(source.contains("ResponseFormat.json(responseSchema)"));
        assertFalse(source.contains("new NoRepeatNgramConfig"));
        assertFalse(source.contains("new RepetitionPenaltyConfig"));
        assertFalse(source.contains("import com.google.ai.edge.litertlm.NoRepeatNgramConfig"));
        assertFalse(source.contains("import com.google.ai.edge.litertlm.RepetitionPenaltyConfig"));

        String sendCall = source.substring(source.indexOf("Message result = conversation.sendMessage("));
        assertTrue(sendCall.contains("Collections.emptyMap(),\n                    null,\n                    null,\n                    null,\n                    outputLimit,\n                    null,\n                    responseFormat"));
    }

    @Test
    public void localRuntimeStillHasBoundedJsonRetryAndNoOpenAiFallback() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/LocalLlmRuntime.java");
        assertTrue(source.contains("JSON_GENERATION_ATTEMPTS = 2"));
        assertTrue(source.contains("LocalPromptCompactor.MAX_COMPACTION_LEVEL"));
        assertTrue(source.contains("Never falls back to OpenAI"));
        assertFalse(source.contains("new OpenAiClient("));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
