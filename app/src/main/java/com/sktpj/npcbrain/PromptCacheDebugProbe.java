package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Runs a small real-API Prompt Cache measurement without touching NPC state or usage ledgers. */
final class PromptCacheDebugProbe {
    static final int CALL_COUNT = 3;
    static final int MAX_OUTPUT_TOKENS = 64;
    static final int MIN_STABLE_PREFIX_CHARS = 10_000;

    static final class CallResult {
        final int index;
        final long inputTokens;
        final long cachedTokens;
        final long latencyMs;

        CallResult(int index, long inputTokens, long cachedTokens, long latencyMs) {
            this.index = Math.max(1, index);
            this.inputTokens = Math.max(0L, inputTokens);
            this.cachedTokens = Math.max(0L, Math.min(this.inputTokens, cachedTokens));
            this.latencyMs = Math.max(0L, latencyMs);
        }

        double hitRate() {
            return ratio(cachedTokens, inputTokens);
        }
    }

    static final class Result {
        private final List<CallResult> calls;

        Result(List<CallResult> calls) {
            List<CallResult> source = calls == null
                    ? Collections.emptyList()
                    : calls;
            this.calls = Collections.unmodifiableList(new ArrayList<>(source));
        }

        List<CallResult> calls() {
            return calls;
        }

        long totalInputTokens() {
            long sum = 0L;
            for (CallResult call : calls) sum = safeAdd(sum, call.inputTokens);
            return sum;
        }

        long totalCachedTokens() {
            long sum = 0L;
            for (CallResult call : calls) sum = safeAdd(sum, call.cachedTokens);
            return sum;
        }

        long reuseInputTokens() {
            long sum = 0L;
            for (CallResult call : calls) {
                if (call.index >= 2) sum = safeAdd(sum, call.inputTokens);
            }
            return sum;
        }

        long reuseCachedTokens() {
            long sum = 0L;
            for (CallResult call : calls) {
                if (call.index >= 2) sum = safeAdd(sum, call.cachedTokens);
            }
            return sum;
        }

        double totalHitRate() {
            return ratio(totalCachedTokens(), totalInputTokens());
        }

        double reuseHitRate() {
            return ratio(reuseCachedTokens(), reuseInputTokens());
        }

        String displayText() {
            StringBuilder text = new StringBuilder();
            for (CallResult call : calls) {
                if (text.length() > 0) text.append('\n');
                text.append(String.format(
                        Locale.JAPAN,
                        "Call %d  input %,d / cached %,d / hit %.1f%% / %,d ms",
                        call.index,
                        call.inputTokens,
                        call.cachedTokens,
                        call.hitRate() * 100.0,
                        call.latencyMs));
            }
            if (text.length() > 0) text.append("\n\n");
            text.append(String.format(
                    Locale.JAPAN,
                    "Total  %,d / %,d  hit %.1f%%\nReuse (Call 2+3)  %,d / %,d  hit %.1f%%",
                    totalCachedTokens(),
                    totalInputTokens(),
                    totalHitRate() * 100.0,
                    reuseCachedTokens(),
                    reuseInputTokens(),
                    reuseHitRate() * 100.0));
            return text.toString();
        }
    }

    private final String apiKey;

    PromptCacheDebugProbe(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    Result run() throws Exception {
        if (apiKey.isEmpty()) throw new IllegalStateException("OpenAI APIキーが未設定です。");
        String prefix = stablePrefix();
        String key = promptCacheKey(System.nanoTime());
        PromptCacheDebugClient client = new PromptCacheDebugClient(apiKey);
        List<CallResult> calls = new ArrayList<>();
        for (int i = 1; i <= CALL_COUNT; i++) {
            long started = System.nanoTime();
            OpenAiClient.Usage usage = client.execute(
                    prefix,
                    dynamicSuffix(i),
                    key,
                    MAX_OUTPUT_TOKENS);
            long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            calls.add(new CallResult(
                    i,
                    usage == null ? 0L : usage.inputTokens,
                    usage == null ? 0L : usage.cachedInputTokens,
                    elapsed));
        }
        return new Result(calls);
    }

    static String stablePrefix() {
        StringBuilder text = new StringBuilder(18_000);
        text.append("You are running a deterministic developer diagnostic for OpenAI Prompt Cache behavior. ")
                .append("Treat every sentence before the explicit cache breakpoint as immutable shared context. ")
                .append("Do not infer any fictional person, user preference, world state, or autonomous-agent state from this diagnostic. ")
                .append("The only requested output is a tiny JSON acknowledgement specified after the shared prefix.\n");
        String block = "Stable diagnostic context: preserve this exact wording, punctuation, ordering, and semantic meaning; "
                + "it exists only to create a long identical prefix for repeated cache measurements and must not affect any external state. ";
        int sequence = 0;
        while (text.length() < MIN_STABLE_PREFIX_CHARS + 2_000) {
            text.append(block)
                    .append("Stable sequence marker ")
                    .append(sequence++)
                    .append(" remains part of the immutable diagnostic prefix.\n");
        }
        text.append("End of immutable diagnostic prefix. The following suffix is the only per-call variation.\n");
        return text.toString();
    }

    static String dynamicSuffix(int callIndex) {
        int index = Math.max(1, callIndex);
        return "Probe call " + index + ". Return ONLY JSON with exactly this shape: "
                + "{\"ok\":true,\"probe_call\":" + index + "}. Do not add prose.";
    }

    static String promptCacheKey(long nonce) {
        return "npcbrain-cache-test-v047-" + Long.toHexString(nonce);
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0L) return 0.0;
        return Math.max(0.0, Math.min(1.0, (double) numerator / (double) denominator));
    }

    private static long safeAdd(long a, long b) {
        long left = Math.max(0L, a);
        long right = Math.max(0L, b);
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
