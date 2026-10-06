package com.sktpj.npcbrain;

final class ApiPricingPolicy {
    static final String MODEL_GPT56_LUNA = "gpt-5.6-luna";
    static final String MODEL_GPT6_LUNA = "gpt-6-luna";
    static final String MODEL_JEV_LATEST = "jev-latest";

    static final long OPENAI_LONG_CONTEXT_THRESHOLD_TOKENS = 272_000L;
    static final double USD_TO_JPY = 158.975;

    static final class Rate {
        final double inputUsdPerMillion;
        final double cachedInputUsdPerMillion;
        final double cacheWriteUsdPerMillion;
        final double outputUsdPerMillion;

        Rate(
                double inputUsdPerMillion,
                double cachedInputUsdPerMillion,
                double cacheWriteUsdPerMillion,
                double outputUsdPerMillion
        ) {
            this.inputUsdPerMillion = inputUsdPerMillion;
            this.cachedInputUsdPerMillion = cachedInputUsdPerMillion;
            this.cacheWriteUsdPerMillion = cacheWriteUsdPerMillion;
            this.outputUsdPerMillion = outputUsdPerMillion;
        }
    }

    private ApiPricingPolicy() {
    }

    static Rate rate(String model, long inputTokens) {
        String normalized = normalizeBillingModel(model);
        if (MODEL_JEV_LATEST.equals(normalized)) {
            return new Rate(0.042, 0.0, 0.0, 0.0);
        }

        boolean longContext = Math.max(0L, inputTokens)
                > OPENAI_LONG_CONTEXT_THRESHOLD_TOKENS;
        if (MODEL_GPT6_LUNA.equals(normalized)) {
            return longContext
                    ? new Rate(0.20, 0.02, 0.25, 0.75)
                    : new Rate(0.10, 0.01, 0.125, 0.50);
        }
        if (MODEL_GPT56_LUNA.equals(normalized)) {
            return longContext
                    ? new Rate(0.40, 0.04, 0.50, 1.80)
                    : new Rate(0.20, 0.02, 0.25, 1.20);
        }
        throw new IllegalArgumentException("Unsupported billing model: " + model);
    }

    static double costJpy(
            String model,
            long inputTokens,
            long cachedInputTokens,
            long cacheWriteTokens,
            long outputTokens
    ) {
        long input = Math.max(0L, inputTokens);
        long cached = Math.max(0L, Math.min(input, cachedInputTokens));
        long remainingAfterCached = input - cached;
        long cacheWrite = Math.max(0L, Math.min(remainingAfterCached, cacheWriteTokens));
        long ordinary = input - cached - cacheWrite;
        long output = Math.max(0L, outputTokens);

        Rate rate = rate(model, input);
        double usd = (
                ordinary * rate.inputUsdPerMillion
                        + cached * rate.cachedInputUsdPerMillion
                        + cacheWrite * rate.cacheWriteUsdPerMillion
                        + output * rate.outputUsdPerMillion
        ) / 1_000_000.0;
        return Math.max(0.0, usd * USD_TO_JPY);
    }

    static double reservationJpy(
            String model,
            long conservativeInputTokens,
            int maxOutputTokens
    ) {
        long input = Math.max(0L, conservativeInputTokens);
        long output = Math.max(0L, (long) maxOutputTokens);
        Rate rate = rate(model, input);
        double usd = (
                input * Math.max(rate.inputUsdPerMillion, rate.cacheWriteUsdPerMillion)
                        + output * rate.outputUsdPerMillion
        ) / 1_000_000.0;
        return Math.max(0.0, usd * USD_TO_JPY);
    }

    static String normalizeBillingModel(String model) {
        String value = model == null ? "" : model.trim().toLowerCase(java.util.Locale.US);
        if (value.startsWith(MODEL_GPT56_LUNA)) return MODEL_GPT56_LUNA;
        if (value.startsWith(MODEL_GPT6_LUNA)) return MODEL_GPT6_LUNA;
        if (value.startsWith("jev-") || value.startsWith("typesafe/jev-")) {
            return MODEL_JEV_LATEST;
        }
        return value;
    }
}
