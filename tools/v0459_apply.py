from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


path = Path("app/src/main/java/com/sktpj/npcbrain/LocalLlmRuntime.java")
text = path.read_text()

text = replace_once(
    text,
    "import com.google.ai.edge.litertlm.Message;\n",
    "import com.google.ai.edge.litertlm.Message;\n"
    "import com.google.ai.edge.litertlm.NoRepeatNgramConfig;\n"
    "import com.google.ai.edge.litertlm.RepetitionPenaltyConfig;\n"
    "import com.google.ai.edge.litertlm.ResponseFormat;\n"
    "import com.google.ai.edge.litertlm.SamplerConfig;\n",
    "imports",
)

text = replace_once(
    text,
    "    private static final Map<String, EngineHolder> ENGINES = new ConcurrentHashMap<>();\n"
    "    private static final Object ENGINE_INIT_LOCK = new Object();\n",
    "    private static final Map<String, EngineHolder> ENGINES = new ConcurrentHashMap<>();\n"
    "    private static final Object ENGINE_INIT_LOCK = new Object();\n"
    "    private static final int JSON_GENERATION_ATTEMPTS = 2;\n"
    "    private static final SamplerConfig JSON_SAMPLER =\n"
    "            new SamplerConfig(20, 0.90d, 0.10d, 0);\n"
    "    private static final RepetitionPenaltyConfig JSON_REPETITION =\n"
    "            new RepetitionPenaltyConfig(1.10f, 0.05f, 0.03f, 256);\n"
    "    private static final NoRepeatNgramConfig JSON_NO_REPEAT =\n"
    "            new NoRepeatNgramConfig(12, 256);\n",
    "constants",
)

text = replace_once(
    text,
    "        JSONObject first = parseJson(sendWithBackendFallback(\n"
    "                model,\n"
    "                modelFile,\n"
    "                id,\n"
    "                prompt,\n"
    "                maxOutputTokens,\n"
    "                tool));\n",
    "        JSONObject first = generateJson(\n"
    "                model,\n"
    "                modelFile,\n"
    "                id,\n"
    "                prompt,\n"
    "                maxOutputTokens,\n"
    "                tool);\n",
    "first parse",
)

text = replace_once(
    text,
    "        return parseJson(sendWithBackendFallback(\n"
    "                model,\n"
    "                modelFile,\n"
    "                id,\n"
    "                continuation,\n"
    "                maxOutputTokens,\n"
    "                null));\n",
    "        return generateJson(\n"
    "                model,\n"
    "                modelFile,\n"
    "                id,\n"
    "                continuation,\n"
    "                maxOutputTokens,\n"
    "                null);\n",
    "continuation parse",
)

needle = "    private String sendWithBackendFallback(\n"
generate_method = r'''    private JSONObject generateJson(
            String modelId,
            File modelFile,
            String npcId,
            String originalPrompt,
            int maxOutputTokens,
            OpenAiClient.FunctionTool tool
    ) {
        IllegalStateException lastParseFailure = null;
        String source = originalPrompt == null ? "" : originalPrompt;
        for (int attempt = 0; attempt < JSON_GENERATION_ATTEMPTS; attempt++) {
            String candidate = attempt == 0 ? source : jsonRetryPrompt(source);
            String raw = sendWithBackendFallback(
                    modelId, modelFile, npcId, candidate, maxOutputTokens, tool);
            try {
                return parseJson(raw);
            } catch (IllegalStateException parseFailure) {
                lastParseFailure = parseFailure;
            }
        }
        if (lastParseFailure != null) throw lastParseFailure;
        throw new IllegalStateException("Local LLM JSON generation produced no parseable response");
    }

    private static String jsonRetryPrompt(String prompt) {
        String source = prompt == null ? "" : prompt;
        String compacted = LocalPromptCompactor.compact(
                source, LocalPromptCompactor.MAX_COMPACTION_LEVEL);
        return compacted
                + "\n\nLOCAL JSON RETRY: The previous local generation did not finish as valid JSON. "
                + "Return the shortest complete JSON object allowed by the required schema. "
                + "Keep string values concise, do not repeat keys or phrases, and close every array/string/object.";
    }

'''
if text.count(needle) != 1:
    raise SystemExit("sendWithBackendFallback insertion point mismatch")
text = text.replace(needle, generate_method + needle, 1)

text = replace_once(
    text,
    "                return send(holder, localPrompt(npcId, candidate, tool), maxOutputTokens);\n",
    "                String schema = LocalJsonSchema.schemaFor(candidate, tool);\n"
    "                return send(\n"
    "                        holder, localPrompt(npcId, candidate, tool), maxOutputTokens, schema);\n",
    "send call",
)

old_send = r'''    private String send(EngineHolder holder, String prompt, int maxOutputTokens) {
        int outputLimit = Math.max(1, maxOutputTokens);
        ConversationConfig conversationConfig = new ConversationConfig(
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                true,
                null,
                Collections.emptyMap(),
                null,
                false,
                outputLimit);

        Conversation conversation = null;
        try {
            conversation = holder.engine.createConversation(conversationConfig);
            Message result = conversation.sendMessage(prompt, Collections.emptyMap());
            return result == null ? "" : result.toString();
        } finally {
            if (conversation != null) {
                try {
                    conversation.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }
'''
new_send = r'''    private String send(
            EngineHolder holder,
            String prompt,
            int maxOutputTokens,
            String responseSchema
    ) {
        int outputLimit = Math.max(1, maxOutputTokens);
        ConversationConfig conversationConfig = new ConversationConfig(
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                JSON_SAMPLER,
                true,
                null,
                Collections.emptyMap(),
                null,
                false,
                outputLimit,
                null,
                true);

        Conversation conversation = null;
        try {
            conversation = holder.engine.createConversation(conversationConfig);
            ResponseFormat responseFormat = ResponseFormat.json(responseSchema);
            Message result = conversation.sendMessage(
                    prompt,
                    Collections.emptyMap(),
                    JSON_REPETITION,
                    JSON_NO_REPEAT,
                    null,
                    outputLimit,
                    null,
                    responseFormat);
            return result == null ? "" : result.toString();
        } finally {
            if (conversation != null) {
                try {
                    conversation.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }
'''
text = replace_once(text, old_send, new_send, "send method")

text = replace_once(
    text,
    "            throw new IllegalStateException(\"Local LLM output was not valid JSON: \" + text, error);\n",
    "            throw new IllegalStateException(\n"
    "                    \"Local LLM output was not valid JSON: \" + preview(text, 700), error);\n",
    "parse error",
)

parse_end = "    }\n}\n"
if not text.endswith(parse_end):
    raise SystemExit("unexpected LocalLlmRuntime ending")
text = text[:-len(parse_end)] + r'''    }

    private static String preview(String value, int maxChars) {
        String text = value == null ? "" : value.trim();
        int limit = Math.max(32, maxChars);
        if (text.length() <= limit) return text;
        return text.substring(0, limit) + "…";
    }
}
'''

path.write_text(text)

version = Path("version.properties")
version_text = version.read_text()
version_text = replace_once(version_text, "VERSION_NAME=0.4.58", "VERSION_NAME=0.4.59", "version name")
version_text = replace_once(version_text, "VERSION_CODE=78", "VERSION_CODE=79", "version code")
version.write_text(version_text)
