package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.regex.Pattern;

public class PromptCacheDebugProbeTest {
    @Test
    public void explicitCacheRequestHasStableBreakpointAndSharedKey() {
        JSONObject body = PromptCacheDebugClient.buildRequestBody(
                "stable-prefix",
                "dynamic-suffix",
                "cache-key-047",
                64);

        assertEquals(OpenAiClient.MODEL, body.optString("model"));
        assertEquals("cache-key-047", body.optString("prompt_cache_key"));
        assertEquals("explicit",
                body.optJSONObject("prompt_cache_options").optString("mode"));
        assertEquals("low", body.optJSONObject("reasoning").optString("effort"));
        assertEquals(64, body.optInt("max_output_tokens"));

        JSONArray input = body.optJSONArray("input");
        assertEquals(1, input.length());
        JSONObject message = input.optJSONObject(0);
        assertEquals("message", message.optString("type"));
        assertEquals("user", message.optString("role"));
        JSONArray content = message.optJSONArray("content");
        assertEquals(2, content.length());
        JSONObject stable = content.optJSONObject(0);
        JSONObject dynamic = content.optJSONObject(1);
        assertEquals("input_text", stable.optString("type"));
        assertEquals("stable-prefix", stable.optString("text"));
        assertEquals("explicit",
                stable.optJSONObject("prompt_cache_breakpoint").optString("mode"));
        assertEquals("input_text", dynamic.optString("type"));
        assertEquals("dynamic-suffix", dynamic.optString("text"));
        assertFalse(dynamic.has("prompt_cache_breakpoint"));
    }

    @Test
    public void resultSeparatesWarmupFromReuseHitRate() {
        PromptCacheDebugProbe.Result result = new PromptCacheDebugProbe.Result(Arrays.asList(
                new PromptCacheDebugProbe.CallResult(1, 100, 0, 10),
                new PromptCacheDebugProbe.CallResult(2, 100, 80, 11),
                new PromptCacheDebugProbe.CallResult(3, 100, 90, 12)));

        assertEquals(300L, result.totalInputTokens());
        assertEquals(170L, result.totalCachedTokens());
        assertEquals(200L, result.reuseInputTokens());
        assertEquals(170L, result.reuseCachedTokens());
        assertEquals(170.0 / 300.0, result.totalHitRate(), 0.000001);
        assertEquals(0.85, result.reuseHitRate(), 0.000001);
        assertTrue(result.displayText().contains("Reuse (Call 2+3)"));
    }

    @Test
    public void zeroInputProducesZeroRateWithoutError() {
        PromptCacheDebugProbe.CallResult call =
                new PromptCacheDebugProbe.CallResult(1, 0, 99, 0);
        PromptCacheDebugProbe.Result result =
                new PromptCacheDebugProbe.Result(Arrays.asList(call));
        assertEquals(0L, call.cachedTokens);
        assertEquals(0.0, call.hitRate(), 0.0);
        assertEquals(0.0, result.totalHitRate(), 0.0);
        assertEquals(0.0, result.reuseHitRate(), 0.0);
    }

    @Test
    public void stablePrefixIsLargeAndCannotAttributeToNpcBudget() {
        String prefix = PromptCacheDebugProbe.stablePrefix();
        assertTrue(prefix.length() >= PromptCacheDebugProbe.MIN_STABLE_PREFIX_CHARS);
        assertFalse(prefix.contains("character_id"));
        assertFalse(Pattern.compile("npc\\d+", Pattern.CASE_INSENSITIVE).matcher(prefix).find());
        assertEquals("", OpenAiClient.attributedNpcId(prefix));
        assertTrue(PromptCacheDebugProbe.promptCacheKey(123L).length() <= 64);
    }

    @Test
    public void usageReadsAndClampsCachedInputTokens() {
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
