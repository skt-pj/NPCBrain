package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ApiPricingPolicyTest {
    @Test
    public void openAiLunaRatesAreModelSpecific() {
        ApiPricingPolicy.Rate gpt56 =
                ApiPricingPolicy.rate(ApiPricingPolicy.MODEL_GPT56_LUNA, 10_000L);
        assertEquals(0.20, gpt56.inputUsdPerMillion, 0.0);
        assertEquals(0.02, gpt56.cachedInputUsdPerMillion, 0.0);
        assertEquals(0.25, gpt56.cacheWriteUsdPerMillion, 0.0);
        assertEquals(1.20, gpt56.outputUsdPerMillion, 0.0);

        ApiPricingPolicy.Rate gpt6 =
                ApiPricingPolicy.rate(ApiPricingPolicy.MODEL_GPT6_LUNA, 10_000L);
        assertEquals(0.10, gpt6.inputUsdPerMillion, 0.0);
        assertEquals(0.01, gpt6.cachedInputUsdPerMillion, 0.0);
        assertEquals(0.125, gpt6.cacheWriteUsdPerMillion, 0.0);
        assertEquals(0.50, gpt6.outputUsdPerMillion, 0.0);
    }

    @Test
    public void openAiLongContextRatesApplyPublishedMultipliers() {
        ApiPricingPolicy.Rate gpt56 =
                ApiPricingPolicy.rate(ApiPricingPolicy.MODEL_GPT56_LUNA, 272_001L);
        assertEquals(0.40, gpt56.inputUsdPerMillion, 0.0);
        assertEquals(0.04, gpt56.cachedInputUsdPerMillion, 0.0);
        assertEquals(0.50, gpt56.cacheWriteUsdPerMillion, 0.0);
        assertEquals(1.80, gpt56.outputUsdPerMillion, 0.0);

        ApiPricingPolicy.Rate gpt6 =
                ApiPricingPolicy.rate(ApiPricingPolicy.MODEL_GPT6_LUNA, 272_001L);
        assertEquals(0.20, gpt6.inputUsdPerMillion, 0.0);
        assertEquals(0.02, gpt6.cachedInputUsdPerMillion, 0.0);
        assertEquals(0.25, gpt6.cacheWriteUsdPerMillion, 0.0);
        assertEquals(0.75, gpt6.outputUsdPerMillion, 0.0);
    }

    @Test
    public void jevBillsInputOnly() {
        ApiPricingPolicy.Rate jev =
                ApiPricingPolicy.rate(ApiPricingPolicy.MODEL_JEV_LATEST, 1_000_000L);
        assertEquals(0.042, jev.inputUsdPerMillion, 0.0);
        assertEquals(0.0, jev.cachedInputUsdPerMillion, 0.0);
        assertEquals(0.0, jev.cacheWriteUsdPerMillion, 0.0);
        assertEquals(0.0, jev.outputUsdPerMillion, 0.0);

        assertEquals(
                0.042 * ApiPricingPolicy.USD_TO_JPY,
                ApiPricingPolicy.costJpy(
                        ApiPricingPolicy.MODEL_JEV_LATEST,
                        1_000_000L,
                        0L,
                        0L,
                        999_999L),
                0.000000001);
    }

    @Test
    public void cacheReadsAndWritesUseSeparatePricesWithoutDoubleCounting() {
        double expectedUsd = (
                500L * 0.20
                        + 200L * 0.02
                        + 300L * 0.25
                        + 400L * 1.20) / 1_000_000.0;
        assertEquals(
                expectedUsd * ApiPricingPolicy.USD_TO_JPY,
                ApiPricingPolicy.costJpy(
                        ApiPricingPolicy.MODEL_GPT56_LUNA,
                        1_000L,
                        200L,
                        300L,
                        400L),
                0.000000001);
    }

    @Test
    public void openAiUsageParsesCacheReadAndCacheWriteSeparately() throws Exception {
        org.json.JSONObject response = new org.json.JSONObject()
                .put("usage", new org.json.JSONObject()
                        .put("input_tokens", 1000)
                        .put("input_tokens_details", new org.json.JSONObject()
                                .put("cached_tokens", 200)
                                .put("cache_write_tokens", 300))
                        .put("output_tokens", 400)
                        .put("total_tokens", 1400));

        OpenAiClient.Usage usage = OpenAiClient.Usage.fromResponse(response);
        assertEquals(1000L, usage.inputTokens);
        assertEquals(200L, usage.cachedInputTokens);
        assertEquals(300L, usage.cacheWriteTokens);
        assertEquals(400L, usage.outputTokens);
        assertEquals(1400L, usage.totalTokens);
    }

    @Test
    public void reservationUsesTheSelectedModelRate() {
        double gpt56 = ApiPricingPolicy.reservationJpy(
                ApiPricingPolicy.MODEL_GPT56_LUNA, 5_000L, 500);
        double gpt6 = ApiPricingPolicy.reservationJpy(
                ApiPricingPolicy.MODEL_GPT6_LUNA, 5_000L, 500);
        double jev = ApiPricingPolicy.reservationJpy(
                ApiPricingPolicy.MODEL_JEV_LATEST, 5_000L, 500);
        assertTrue(gpt56 > gpt6);
        assertTrue(gpt6 > jev);
    }
}
