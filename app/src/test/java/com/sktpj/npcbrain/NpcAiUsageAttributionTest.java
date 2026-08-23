package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NpcAiUsageAttributionTest {
    @Test
    public void extractsNpcFromDirectConversationPrompt() {
        String prompt = "Runtime JSON: {\\\"mode\\\":\\\"conversational_message\\\","
                + "\\\"character_id\\\":\\\"npc3\\\"}";
        assertEquals("npc3", OpenAiClient.attributedNpcId(prompt));
    }

    @Test
    public void ignoresNonNpcAnalysisPrompt() {
        assertEquals("", OpenAiClient.attributedNpcId(
                "Analyze LINE data as JSON. target_name=someone"));
    }

    @Test
    public void findsNpcInNestedEscapedRuntimeJson() {
        String prompt = "Input JSON: {\"user_input\":\"Runtime JSON: "
                + "{\\\"character_id\\\":\\\"npc12\\\"}\"}";
        assertEquals("npc12", OpenAiClient.attributedNpcId(prompt));
    }
}
