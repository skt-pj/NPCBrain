package com.sktpj.npcbrain;

import org.json.JSONObject;

/**
 * Builds one bounded grounded snapshot shared by all nine CLEF specialists.
 * No extra model runs here: this only reshapes already-available state.
 */
final class ClefSpecialistContextBuilder {
    private static final int CHARACTER_LEVEL = 4;
    private static final int MEMORY_LEVEL = 5;
    private static final int RUNTIME_LEVEL = 4;
    private static final int STIMULUS_LIMIT = 640;
    private static final int RAW_INPUT_LIMIT = 900;

    static String build(String commonContextJson) {
        try {
            JSONObject source = new JSONObject(commonContextJson == null ? "{}" : commonContextJson);
            JSONObject result = new JSONObject();

            putInput(result, source.optString("user_input", ""));

            JSONObject character = source.optJSONObject("character_state");
            if (character != null) {
                result.put("character_state",
                        LocalPromptCompactor.compactGroundedJson(character, CHARACTER_LEVEL));
            }

            JSONObject memory = source.optJSONObject("long_term_memory");
            if (memory != null) {
                result.put("long_term_memory",
                        LocalPromptCompactor.compactGroundedJson(
                                new JSONObject().put("long_term_memory", memory),
                                MEMORY_LEVEL)
                                .optJSONObject("long_term_memory"));
            }

            JSONObject parallel = source.optJSONObject("parallel_phase");
            if (parallel != null) result.put("parallel_phase", parallel);

            return result.toString();
        } catch (Exception ignored) {
            return headTail(commonContextJson, RAW_INPUT_LIMIT);
        }
    }

    private static void putInput(JSONObject result, String raw) {
        try {
            ParsedRuntime parsed = parseRuntime(raw);
            if (parsed.runtime != null) {
                result.put("runtime",
                        LocalPromptCompactor.compactGroundedJson(parsed.runtime, RUNTIME_LEVEL));
                if (!parsed.stimulus.isEmpty()) {
                    result.put("stimulus", headTail(parsed.stimulus, STIMULUS_LIMIT));
                }
            } else {
                result.put("stimulus", headTail(raw, RAW_INPUT_LIMIT));
            }
        } catch (Exception ignored) {
        }
    }

    private static ParsedRuntime parseRuntime(String raw) {
        String text = raw == null ? "" : raw;
        String marker = "Runtime JSON:\n";
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) return new ParsedRuntime(null, text.trim());

        int start = text.indexOf('{', markerIndex + marker.length());
        if (start < 0) return new ParsedRuntime(null, text.trim());
        int end = findObjectEnd(text, start);
        if (end < 0) return new ParsedRuntime(null, text.trim());

        try {
            JSONObject runtime = new JSONObject(text.substring(start, end + 1));
            String stimulus = text.substring(end + 1).trim();
            return new ParsedRuntime(runtime, stimulus);
        } catch (Exception ignored) {
            return new ParsedRuntime(null, text.trim());
        }
    }

    private static int findObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String headTail(String value, int maxChars) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxChars) return text;
        int payload = Math.max(32, maxChars - 28);
        int head = payload * 2 / 3;
        int tail = payload - head;
        return text.substring(0, head) + "\n...[omitted]...\n"
                + text.substring(text.length() - tail);
    }

    private static final class ParsedRuntime {
        final JSONObject runtime;
        final String stimulus;

        ParsedRuntime(JSONObject runtime, String stimulus) {
            this.runtime = runtime;
            this.stimulus = stimulus == null ? "" : stimulus;
        }
    }

    private ClefSpecialistContextBuilder() {
    }
}
