package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class BrainPromptCacheTest {
    @Test
    public void nineSpecialistsShareCacheSegmentsButKeepDistinctRoleContracts() throws Exception {
        JSONObject common = PromptCacheDebugProbe.syntheticCommonContext();
        String[] ids = BrainEngine.specialistIds();
        assertEquals(9, ids.length);

        PromptCacheRequest.Prompt first = BrainEngine.specialistPromptForTest(
                0,
                common,
                PromptCacheDebugProbe.syntheticGraphFocus(ids[0]));
        assertEquals(2, first.cacheSegments().size());
        assertTrue(first.cacheSegments().get(1).contains("peer_outputs_available"));
        assertTrue(first.cacheSegments().get(1).contains("working_memory"));
        assertFalse(first.cacheSegments().get(1).contains("cognitive_graph_focus"));

        Set<String> suffixes = new HashSet<>();
        for (int i = 0; i < ids.length; i++) {
            PromptCacheRequest.Prompt prompt = BrainEngine.specialistPromptForTest(
                    i,
                    common,
                    PromptCacheDebugProbe.syntheticGraphFocus(ids[i]));
            assertEquals(first.promptCacheKey(), prompt.promptCacheKey());
            assertEquals(first.cacheSegments(), prompt.cacheSegments());
            assertTrue(prompt.dynamicSuffix().contains("You are the " + ids[i] + " function"));
            assertTrue(prompt.dynamicSuffix().contains("cognitive_graph_focus for this specialist only"));
            suffixes.add(prompt.dynamicSuffix());
        }
        assertEquals(9, suffixes.size());
    }

    @Test
    public void cachedRequestBodyPlacesBothSharedSegmentsBeforeModuleContract() throws Exception {
        String[] ids = BrainEngine.specialistIds();
        PromptCacheRequest.Prompt prompt = BrainEngine.specialistPromptForTest(
                4,
                PromptCacheDebugProbe.syntheticCommonContext(),
                PromptCacheDebugProbe.syntheticGraphFocus(ids[4]));
        JSONObject body = OpenAiClient.cachedRequestBodyForTest(prompt, "low", 512);

        assertEquals("explicit",
                body.optJSONObject("prompt_cache_options").optString("mode"));
        assertEquals(prompt.promptCacheKey(), body.optString("prompt_cache_key"));
        JSONArray content = body.optJSONArray("input").optJSONObject(0).optJSONArray("content");
        assertEquals(3, content.length());
        assertEquals(prompt.cacheSegments().get(0), content.optJSONObject(0).optString("text"));
        assertEquals(prompt.cacheSegments().get(1), content.optJSONObject(1).optString("text"));
        assertEquals(prompt.dynamicSuffix(), content.optJSONObject(2).optString("text"));
        assertEquals("explicit", content.optJSONObject(0)
                .optJSONObject("prompt_cache_breakpoint").optString("mode"));
        assertEquals("explicit", content.optJSONObject(1)
                .optJSONObject("prompt_cache_breakpoint").optString("mode"));
        assertFalse(content.optJSONObject(2).has("prompt_cache_breakpoint"));
    }

    @Test
    public void globalWorkspaceUsesIndependentCacheContractAndKeepsNineResultsDynamic() throws Exception {
        JSONObject context = new JSONObject()
                .put("user_input", "cache contract test")
                .put("character_state", new JSONObject().put("display_name", "test"))
                .put("long_term_memory", new JSONObject())
                .put("working_memory", new JSONArray());
        for (String id : BrainEngine.specialistIds()) {
            context.optJSONArray("working_memory").put(new JSONObject()
                    .put("module", id)
                    .put("content", "result-" + id));
        }

        PromptCacheRequest.Prompt specialist = BrainEngine.specialistPromptForTest(
                0,
                PromptCacheDebugProbe.syntheticCommonContext(),
                PromptCacheDebugProbe.syntheticGraphFocus("perception"));
        PromptCacheRequest.Prompt global = BrainEngine.globalWorkspacePromptForTest(context);

        assertNotEquals(specialist.promptCacheKey(), global.promptCacheKey());
        assertEquals(1, global.cacheSegments().size());
        assertTrue(global.fullText().startsWith("You are the existing Global Workspace"));
        assertTrue(global.dynamicSuffix().contains("result-perception"));
        assertTrue(global.dynamicSuffix().contains("result-action_selection"));
        assertTrue(OpenAiClient.isGlobalWorkspacePrompt(global.fullText()));
    }

    @Test
    public void productionArchitectureStillDeclaresNineParallelSpecialistsPlusWorkspace() {
        assertEquals(9, BrainEngine.moduleCount());
        assertEquals(9, BrainEngine.specialistParallelism());
        assertEquals(10, BrainEngine.stageIds().length);
        assertEquals("parallel_specialists_then_global_workspace", BrainEngine.executionMode());
    }
}
