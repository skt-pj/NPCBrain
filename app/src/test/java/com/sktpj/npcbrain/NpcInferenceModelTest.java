package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NpcInferenceModelTest {
    @Test
    public void defaultAndUnknownNormalizeToLocalLight() {
        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize(null));
        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize(""));
        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize("unknown"));
    }

    @Test
    public void onlyOpenAiLunaUsesOpenAi() {
        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_LIGHT));
        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_MEDIUM));
        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_HEAVY));
        assertTrue(NpcInferenceModel.usesOpenAi(NpcInferenceModel.OPENAI_LUNA));
    }

    @Test
    public void localModelSpecsArePinned() {
        LocalModelRepository.ModelSpec light = LocalModelRepository.spec(NpcInferenceModel.LOCAL_LIGHT);
        assertEquals("litert-community/Gemma3-1B-IT", light.repository);
        assertEquals("42d538a932e8d5b12e6b3b455f5572560bd60b2c", light.revision);
        assertEquals("gemma3-1b-it-int4.litertlm", light.fileName);

        LocalModelRepository.ModelSpec medium = LocalModelRepository.spec(NpcInferenceModel.LOCAL_MEDIUM);
        assertEquals("litert-community/Qwen2.5-1.5B-Instruct", medium.repository);
        assertEquals("19edb84c69a0212f29a6ef17ba0d6f278b6a1614", medium.revision);
        assertEquals("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", medium.fileName);

        LocalModelRepository.ModelSpec heavy = LocalModelRepository.spec(NpcInferenceModel.LOCAL_HEAVY);
        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", heavy.repository);
        assertEquals("6e5c4f1e395deb959c494953478fa5cec4b8008f", heavy.revision);
        assertEquals("gemma-4-E2B-it.litertlm", heavy.fileName);
    }
}
