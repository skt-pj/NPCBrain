package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Real-API cache measurement using the production Brain specialist prompt contracts. */
final class PromptCacheDebugProbe {
    static final int CALL_COUNT = 9;
    static final int WARMUP_INDEX = 0;
    static final int MAX_OUTPUT_TOKENS = 384;
    static final int MIN_COMMON_CONTEXT_CHARS = 10_000;

    static final class CallResult {
        final int index;
        final String moduleId;
        final boolean warmup;
        final long inputTokens;
        final long cachedTokens;
        final long latencyMs;

        CallResult(
                int index,
                String moduleId,
                boolean warmup,
                long inputTokens,
                long cachedTokens,
                long latencyMs
        ) {
            this.index = Math.max(1, index);
            this.moduleId = moduleId == null ? "" : moduleId.trim();
            this.warmup = warmup;
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
                if (!call.warmup) sum = safeAdd(sum, call.inputTokens);
            }
            return sum;
        }

        long reuseCachedTokens() {
            long sum = 0L;
            for (CallResult call : calls) {
                if (!call.warmup) sum = safeAdd(sum, call.cachedTokens);
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
                        "%s%s  input %,d / cached %,d / hit %.1f%% / %,d ms",
                        call.moduleId,
                        call.warmup ? " [warm-up]" : "",
                        call.inputTokens,
                        call.cachedTokens,
                        call.hitRate() * 100.0,
                        call.latencyMs));
            }
            if (text.length() > 0) text.append("\n\n");
            text.append(String.format(
                    Locale.JAPAN,
                    "Total  %,d / %,d  hit %.1f%%\nReuse (8 specialists)  %,d / %,d  hit %.1f%%\n"
                            + "※ warm-up後のparallel fan-out測定。production cold-startと同一とは限りません。",
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
        String[] modules = BrainEngine.specialistIds();
        if (modules.length != CALL_COUNT) {
            throw new IllegalStateException("Brain specialist count mismatch: " + modules.length);
        }

        JSONObject common = syntheticCommonContext();
        PromptCacheDebugClient client = new PromptCacheDebugClient(apiKey);
        List<CallResult> results = new ArrayList<>();

        results.add(executeOne(client, WARMUP_INDEX, modules[WARMUP_INDEX], common, true));

        List<CallResult> parallel = ParallelCognitionScheduler.run(
                modules.length - 1,
                index -> {
                    int moduleIndex = index + 1;
                    return executeOne(
                            client,
                            moduleIndex,
                            modules[moduleIndex],
                            common,
                            false);
                },
                null);
        results.addAll(parallel);
        return new Result(results);
    }

    private static CallResult executeOne(
            PromptCacheDebugClient client,
            int moduleIndex,
            String moduleId,
            JSONObject common,
            boolean warmup
    ) throws Exception {
        PromptCacheRequest.Prompt prompt = BrainEngine.specialistPromptForTest(
                moduleIndex,
                common,
                syntheticGraphFocus(moduleId));
        long started = System.nanoTime();
        OpenAiClient.Usage usage = client.execute(prompt, MAX_OUTPUT_TOKENS);
        long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        return new CallResult(
                moduleIndex + 1,
                moduleId,
                warmup,
                usage == null ? 0L : usage.inputTokens,
                usage == null ? 0L : usage.cachedInputTokens,
                elapsed);
    }

    static JSONObject syntheticCommonContext() throws Exception {
        JSONObject character = new JSONObject();
        character.put("display_name", "Cache Diagnostic NPC");
        character.put("profile", new JSONObject()
                .put("occupation", "developer diagnostic")
                .put("age_stage", "adult"));
        character.put("personality", new JSONObject()
                .put("openness", 0.61)
                .put("conscientiousness", 0.58)
                .put("extraversion", 0.47)
                .put("agreeableness", 0.55)
                .put("neuroticism", 0.43));
        character.put("dynamic_state", new JSONObject()
                .put("valence", 0.0)
                .put("arousal", 0.45)
                .put("stress", 0.2));

        StringBuilder evidence = new StringBuilder(14_000);
        int sequence = 0;
        while (evidence.length() < MIN_COMMON_CONTEXT_CHARS) {
            evidence.append("Grounded cache diagnostic evidence ")
                    .append(sequence++)
                    .append(": this sentence is synthetic test context, carries no user secret, and must not mutate any NPC state. ");
        }

        JSONObject memory = new JSONObject()
                .put("recent_memory", new JSONArray())
                .put("episodic_memory", new JSONArray())
                .put("semantic_memory", new JSONArray().put(new JSONObject()
                        .put("type", "world_fact")
                        .put("text", evidence.toString())))
                .put("policy", "Developer-only synthetic memory evidence for cache measurement.");

        return new JSONObject()
                .put("user_input", "Developer diagnostic scene: observe the same grounded situation independently according to your specialist role.")
                .put("character_state", character)
                .put("long_term_memory", memory)
                .put("working_memory", new JSONArray())
                .put("parallel_phase", new JSONObject()
                        .put("mode", "parallel_specialists")
                        .put("peer_outputs_available", false)
                        .put("specialist_count", CALL_COUNT));
    }

    static JSONObject syntheticGraphFocus(String moduleId) throws Exception {
        return new JSONObject()
                .put("nodes", new JSONArray().put(new JSONObject()
                        .put("id", "diagnostic-grounded-node")
                        .put("label", "synthetic grounded developer diagnostic")
                        .put("activation", 0.5)))
                .put("edges", new JSONArray())
                .put("policy", "Synthetic graph focus for " + (moduleId == null ? "" : moduleId));
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
