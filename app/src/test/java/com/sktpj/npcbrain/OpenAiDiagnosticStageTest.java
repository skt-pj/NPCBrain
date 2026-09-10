package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OpenAiDiagnosticStageTest {
    @Test
    public void specialistPromptCarriesActualBrainStageIdentity() throws Exception {
        String prompt = BrainEngine.specialistPromptForTest(
                0,
                new org.json.JSONObject().put("character_id", "npc9"),
                new org.json.JSONObject()).fullText();
        assertEquals("perception", OpenAiClient.diagnosticBrainStage(prompt));
    }

    @Test
    public void globalWorkspaceCarriesWorkspaceIdentity() {
        String prompt = BrainEngine.globalWorkspacePromptForTest(new org.json.JSONObject()).fullText();
        assertEquals("global_workspace", OpenAiClient.diagnosticBrainStage(prompt));
    }
}
