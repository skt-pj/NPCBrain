package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure request-shape helper shared by production Brain and Debug cache diagnostics. */
final class PromptCacheRequest {
    static final String EXPLICIT_MODE = "explicit";
    static final int MAX_BREAKPOINTS = 4;

    static final class Prompt {
        private final List<String> cacheSegments;
        private final String dynamicSuffix;
        private final String promptCacheKey;

        Prompt(List<String> cacheSegments, String dynamicSuffix, String promptCacheKey) {
            if (cacheSegments == null || cacheSegments.isEmpty()) {
                throw new IllegalArgumentException("At least one cache segment is required");
            }
            if (cacheSegments.size() > MAX_BREAKPOINTS) {
                throw new IllegalArgumentException("At most four explicit cache segments are supported");
            }
            List<String> normalized = new ArrayList<>();
            for (String segment : cacheSegments) {
                String value = segment == null ? "" : segment;
                if (value.isEmpty()) throw new IllegalArgumentException("Cache segment must not be empty");
                normalized.add(value);
            }
            String key = promptCacheKey == null ? "" : promptCacheKey.trim();
            if (key.isEmpty()) throw new IllegalArgumentException("promptCacheKey is required");
            if (key.length() > 64) throw new IllegalArgumentException("promptCacheKey must be <= 64 chars");
            this.cacheSegments = Collections.unmodifiableList(normalized);
            this.dynamicSuffix = dynamicSuffix == null ? "" : dynamicSuffix;
            this.promptCacheKey = key;
        }

        List<String> cacheSegments() {
            return cacheSegments;
        }

        String dynamicSuffix() {
            return dynamicSuffix;
        }

        String promptCacheKey() {
            return promptCacheKey;
        }

        String fullText() {
            StringBuilder result = new StringBuilder();
            for (String segment : cacheSegments) result.append(segment);
            result.append(dynamicSuffix);
            return result.toString();
        }
    }

    private PromptCacheRequest() {
    }

    static JSONObject buildBody(
            String model,
            String reasoningEffort,
            int maxOutputTokens,
            Prompt prompt
    ) {
        if (prompt == null) throw new IllegalArgumentException("prompt is required");
        try {
            JSONObject body = new JSONObject();
            body.put("model", model == null ? "" : model);
            body.put("reasoning", new JSONObject().put("effort", reasoningEffort == null ? "" : reasoningEffort));
            body.put("max_output_tokens", maxOutputTokens);
            body.put("prompt_cache_key", prompt.promptCacheKey());
            body.put("prompt_cache_options", new JSONObject().put("mode", EXPLICIT_MODE));

            JSONArray content = new JSONArray();
            for (String segment : prompt.cacheSegments()) {
                content.put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", segment)
                        .put("prompt_cache_breakpoint", new JSONObject().put("mode", EXPLICIT_MODE)));
            }
            if (!prompt.dynamicSuffix().isEmpty()) {
                content.put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", prompt.dynamicSuffix()));
            }
            body.put("input", new JSONArray().put(new JSONObject()
                    .put("type", "message")
                    .put("role", "user")
                    .put("content", content)));
            return body;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to build Prompt Cache request body", error);
        }
    }
}
