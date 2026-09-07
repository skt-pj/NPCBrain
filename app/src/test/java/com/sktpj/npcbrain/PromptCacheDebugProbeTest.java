package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class PromptCacheDebugProbeTest {
    @Test
    public void explicitCacheRequestSupportsTwoBrainCacheSegments() throws Exception {
        PromptCacheRequest.Prompt prompt = new PromptCacheRequest.Prompt(
                Arrays.asList("static-brain-protocol", "shared-cycle-context"),
                "module-specific-contract",
                "cache-key-048");
        JSONObject body = PromptCacheDebugClient.buildRequestBody(prompt, 384);

        assertEquals(OpenAiClient.MODEL, body.optString("model"));
        assertEquals("cache-key-048", body.optString("prompt_cache_key"));
        assertEquals("explicit",
                body.optJSONObject("prompt_cache_options").optString("mode"));
        assertEquals("low", body.optJSONObject("reasoning").optString("effort"));
        assertEquals(384, body.optInt("max_output_tokens"));

        JSONArray input = body.optJSONArray("input");
        assertEquals(1, input.length());
        JSONObject message = input.optJSONObject(0);
        assertEquals("message", message.optString("type"));
        assertEquals("user", message.optString("role"));
        JSONArray content = message.optJSONArray("content");
        assertEquals(3, content.length());
        assertEquals("static-brain-protocol", content.optJSONObject(0).optString("text"));
        assertEquals("shared-cycle-context", content.optJSONObject(1).optString("text"));
        assertEquals("module-specific-contract", content.optJSONObject(2).optString("text"));
        assertEquals("explicit", content.optJSONObject(0)
                .optJSONObject("prompt_cache_breakpoint").optString("mode"));
        assertEquals("explicit", content.optJSONObject(1)
                .optJSONObject("prompt_cache_breakpoint").optString("mode"));
        assertFalse(content.optJSONObject(2).has("prompt_cache_breakpoint"));
    }

    @Test
    public void resultSeparatesWarmupFromEightSpecialistReuseHitRate() {
        PromptCacheDebugProbe.Result result = new PromptCacheDebugProbe.Result(Arrays.asList(
                new PromptCacheDebugProbe.CallResult(1, "perception", true, 100, 0, 10),
                new PromptCacheDebugProbe.CallResult(2, "salience", false, 100, 80, 11),
                new PromptCacheDebugProbe.CallResult(3, "episodic_memory", false, 100, 90, 12)));

        assertEquals(300L, result.totalInputTokens());
        assertEquals(170L, result.totalCachedTokens());
        assertEquals(200L, result.reuseInputTokens());
        assertEquals(170L, result.reuseCachedTokens());
        assertEquals(170.0 / 300.0, result.totalHitRate(), 0.000001);
        assertEquals(0.85, result.reuseHitRate(), 0.000001);
        assertTrue(result.displayText().contains("Reuse (8 specialists)"));
        assertTrue(result.displayText().contains("perception [warm-up]"));
    }

    @Test
    public void zeroInputProducesZeroRateWithoutError() {
        PromptCacheDebugProbe.CallResult call =
                new PromptCacheDebugProbe.CallResult(1, "perception", true, 0, 99, 0);
        PromptCacheDebugProbe.Result result =
                new PromptCacheDebugProbe.Result(Arrays.asList(call));
        assertEquals(0L, call.cachedTokens);
        assertEquals(0.0, call.hitRate(), 0.0);
        assertEquals(0.0, result.totalHitRate(), 0.0);
        assertEquals(0.0, result.reuseHitRate(), 0.0);
    }

    @Test
    public void syntheticBrainContextIsLargeAndKeepsParallelIsolation() throws Exception {
        JSONObject common = PromptCacheDebugProbe.syntheticCommonContext();
        String text = common.toString();
        assertTrue(text.length() >= PromptCacheDebugProbe.MIN_COMMON_CONTEXT_CHARS);
        assertEquals(0, common.optJSONArray("working_memory").length());
        JSONObject phase = common.optJSONObject("parallel_phase");
        assertFalse(phase.optBoolean("peer_outputs_available", true));
        assertEquals(9, phase.optInt("specialist_count"));
        assertEquals("", OpenAiClient.attributedNpcId(text));
    }

    @Test
    public void usageReadsAndClampsCachedInputTokens() throws Exception {
        JSONObject response = new JSONObject()
                .put("usage", new JSONObject()
                        .put("input_tokens", 100L)
                        .put("output_tokens", 12L)
                        .put("total_tokens", 112L)
                        .put("input_tokens_details", new JSONObject()
                                .put("cached_tokens", 140L)));
        OpenAiClient.Usage usage = OpenAiClient.Usage.fromResponse(response);
        assertEquals(100L, usage.inputTokens);
        assertEquals(100L, usage.cachedInputTokens);
        assertEquals(12L, usage.outputTokens);
        assertEquals(112L, usage.totalTokens);
    }
}
