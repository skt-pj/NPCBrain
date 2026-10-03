
    OpenAiClient(Context context, String apiKey, String reasoningEffort) {
        this(context, apiKey, reasoningEffort, null, null);
    }

    OpenAiClient(
            Context context,
            String apiKey,
            String reasoningEffort,
            UsageListener usageListener,
            OutputLimitPolicy outputLimitPolicy
    ) {
        this.appContext = context.getApplicationContext();
        this.apiKey = apiKey;
        this.reasoningEffort = ModelSettingsStore.normalizeReasoningEffort(reasoningEffort);
        this.usageListener = usageListener;
        this.outputLimitPolicy = outputLimitPolicy;
    }

    static void setFunctionToolForCurrentThread(FunctionTool tool) {
        if (tool == null) FUNCTION_TOOL.remove();
        else FUNCTION_TOOL.set(tool);
    }

    static void clearFunctionToolForCurrentThread() {
        FUNCTION_TOOL.remove();
    }

    String reasoningEffort() {
        return reasoningEffort;
    }

    JSONObject requestJson(String prompt) throws Exception {
        int ordinal = logicalRequestOrdinal.incrementAndGet();
        int requestedLimit;
        if (outputLimitPolicy != null) {
            requestedLimit = outputLimitPolicy.maxOutputTokens(ordinal);
        } else if (!attributedNpcId(prompt).isEmpty()) {
            requestedLimit = NpcAiBudgetPolicy.npcDefaultMaxOutputTokens(prompt);
        } else {
            requestedLimit = DEFAULT_MAX_OUTPUT_TOKENS;
        }
        return requestJsonInternal(prompt, normalizeMaxOutputTokens(requestedLimit));
    }

    JSONObject requestJson(String prompt, int maxOutputTokens) throws Exception {
        logicalRequestOrdinal.incrementAndGet();
        return requestJsonInternal(prompt, normalizeMaxOutputTokens(maxOutputTokens));
    }

    JSONObject requestJson(PromptCacheRequest.Prompt prompt) throws Exception {
        if (prompt == null) throw new IllegalArgumentException("prompt is required");
        String fullPrompt = prompt.fullText();
        int ordinal = logicalRequestOrdinal.incrementAndGet();
        int requestedLimit;
        if (outputLimitPolicy != null) {
            requestedLimit = outputLimitPolicy.maxOutputTokens(ordinal);
        } else if (!attributedNpcId(fullPrompt).isEmpty()) {
            requestedLimit = NpcAiBudgetPolicy.npcDefaultMaxOutputTokens(fullPrompt);
        } else {
            requestedLimit = DEFAULT_MAX_OUTPUT_TOKENS;
        }
        return requestJsonInternal(prompt, normalizeMaxOutputTokens(requestedLimit));
    }

    JSONObject requestJson(PromptCacheRequest.Prompt prompt, int maxOutputTokens) throws Exception {
        if (prompt == null) throw new IllegalArgumentException("prompt is required");
        logicalRequestOrdinal.incrementAndGet();
        return requestJsonInternal(prompt, normalizeMaxOutputTokens(maxOutputTokens));
    }

    static int normalizeMaxOutputTokens(int value) {
        return Math.max(1, Math.min(DEFAULT_MAX_OUTPUT_TOKENS, value));
    }

    static boolean isGlobalWorkspacePrompt(String prompt) {
        return prompt != null && prompt.startsWith("You are the existing Global Workspace");
    }

    static String toolChoice(OpenAiClient.FunctionTool tool) {
        return tool != null && tool.requiredInvocation() ? "required" : "auto";
    }

    static JSONObject cachedRequestBodyForTest(
            PromptCacheRequest.Prompt prompt,
            String reasoningEffort,
            int maxOutputTokens
    ) {
        return PromptCacheRequest.buildBody(
                MODEL,
                ModelSettingsStore.normalizeReasoningEffort(reasoningEffort),
                normalizeMaxOutputTokens(maxOutputTokens),
                prompt);
    }

    private JSONObject requestJsonInternal(String prompt, int maxOutputTokens) throws Exception {
        String queueId = beginDiagnosticLlmRequest(prompt);
        try {
            JSONObject result = requestJsonInternalUnobserved(prompt, maxOutputTokens);
            ProcessingQueueRegistry.attachResponse(queueId, result.toString());
            completeDiagnosticLlmRequest(queueId);
            return result;
        } catch (Exception error) {
            failDiagnosticLlmRequest(queueId, error);
            throw error;
        }
    }

    private JSONObject requestJsonInternalUnobserved(String prompt, int maxOutputTokens) throws Exception {
        FunctionTool tool = isGlobalWorkspacePrompt(prompt) ? FUNCTION_TOOL.get() : null;
        JSONObject local = requestLocalIfSelected(prompt, maxOutputTokens, tool);
        if (local != null) return local;
        JSONObject body = new JSONObject();
        body.put("model", selectedOpenAiApiModel(prompt));
        body.put("reasoning", new JSONObject().put("effort", reasoningEffort));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("input", prompt);
        if (tool != null) {
            body.put("tools", new JSONArray().put(tool.definition()));
            body.put("tool_choice", toolChoice(tool));
            body.put("parallel_tool_calls", false);
        }
        return executeLogicalRequest(body, prompt, tool, maxOutputTokens);
    }

    private JSONObject requestJsonInternal(
            PromptCacheRequest.Prompt prompt,
            int maxOutputTokens
    ) throws Exception {
        String fullPrompt = prompt.fullText();
        String queueId = beginDiagnosticLlmRequest(fullPrompt);
        try {
            JSONObject result = requestJsonInternalUnobserved(prompt, maxOutputTokens);
            ProcessingQueueRegistry.attachResponse(queueId, result.toString());
            completeDiagnosticLlmRequest(queueId);
            return result;
        } catch (Exception error) {
            failDiagnosticLlmRequest(queueId, error);
            throw error;
        }
    }

    private JSONObject requestJsonInternalUnobserved(
            PromptCacheRequest.Prompt prompt,
            int maxOutputTokens
    ) throws Exception {
        String fullPrompt = prompt.fullText();
        FunctionTool tool = isGlobalWorkspacePrompt(fullPrompt) ? FUNCTION_TOOL.get() : null;
        JSONObject local = requestLocalIfSelected(fullPrompt, maxOutputTokens, tool);
        if (local != null) return local;
        JSONObject body = PromptCacheRequest.buildBody(
                selectedOpenAiApiModel(fullPrompt),
                reasoningEffort,
                maxOutputTokens,
                prompt);
        if (tool != null) {
            body.put("tools", new JSONArray().put(tool.definition()));
            body.put("tool_choice", toolChoice(tool));
            body.put("parallel_tool_calls", false);
        }
        return executeLogicalRequest(body, fullPrompt, tool, maxOutputTokens);
    }

    private String beginDiagnosticLlmRequest(String fullPrompt) {
        try {
            String npcId = attributedNpcId(fullPrompt);
            if (npcId.isEmpty()) return "";
            String stage = diagnosticBrainStage(fullPrompt);
            String selectedModel = new NpcModelStore(appContext, npcId).effectiveModelForStage(stage);
            String queueId = ProcessingQueueRegistry.startRunning(
                    "llm_request",
                    npcId,
                    selectedModel + " · brain_stage=" + stage,
                    System.currentTimeMillis());
            ProcessingQueueRegistry.attachRequest(queueId, fullPrompt);
            return queueId;
        } catch (Exception ignored) {
            return "";
        }
    }

    static String diagnosticBrainStage(String fullPrompt) {
        if (isGlobalWorkspacePrompt(fullPrompt)) return "global_workspace";
        String source = fullPrompt == null ? "" : fullPrompt;
        String suffix = " function inside the brain-inspired NPC cognitive architecture.";
        int suffixAt = source.indexOf(suffix);
        if (suffixAt > 0) {
            int prefixAt = source.lastIndexOf("You are the ", suffixAt);
            if (prefixAt >= 0) {
                String candidate = source.substring(prefixAt + "You are the ".length(), suffixAt).trim();
                for (String stageId : BrainEngine.specialistIds()) {
                    if (stageId.equals(candidate)) return stageId;
                }
            }
        }
        if (source.contains("encoding/appraisal pass of a memory-maintenance system")) {
            return "memory_appraisal";
        }
        if (source.contains("consolidation/schema pass")) {
            return "memory_consolidation";
        }
        if (source.contains("retention/forgetting pass")) {
            return "memory_retention";
        }
        return "local_request";
    }

    private void completeDiagnosticLlmRequest(String queueId) {
        if (queueId == null || queueId.isEmpty()) return;
        ProcessingQueueRegistry.markCompleted(queueId);
    }

    private void failDiagnosticLlmRequest(String queueId, Exception error) {
        if (queueId == null || queueId.isEmpty()) return;
        ProcessingQueueRegistry.markFailed(queueId, error);
    }

    private String selectedOpenAiApiModel(String fullPrompt) {
        String npcId = attributedNpcId(fullPrompt);
        if (npcId.isEmpty()) return GPT6_MODEL;
        String stage = diagnosticBrainStage(fullPrompt);
        String selectedModel = new NpcModelStore(appContext, npcId).effectiveModelForStage(stage);
        if (!NpcInferenceModel.usesOpenAi(selectedModel)) return GPT6_MODEL;
        return NpcInferenceModel.openAiApiModel(selectedModel);
    }

    private JSONObject requestLocalIfSelected(
            String fullPrompt,
            int maxOutputTokens,
            FunctionTool tool
    ) throws Exception {
        String npcId = attributedNpcId(fullPrompt);
        if (npcId.isEmpty()) return null;
        String stage = diagnosticBrainStage(fullPrompt);
        String selectedModel = new NpcModelStore(appContext, npcId).effectiveModelForStage(stage);
        if (NpcInferenceModel.usesOpenAi(selectedModel)) return null;
        return new LocalLlmRuntime(appContext).requestJson(
                npcId, selectedModel, fullPrompt, maxOutputTokens, tool);
    }

    private JSONObject executeLogicalRequest(
            JSONObject body,
            String attributionPrompt,
            FunctionTool tool,
            int maxOutputTokens
    ) throws Exception {
        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        IOException firstFailure = null;
        String attributedNpcId = attributedNpcId(attributionPrompt);

        for (int pass = 0; pass < CONNECTION_RETRY_PASSES; pass++) {
            if (pass > 0) {
                try {
                    Thread.sleep(CONNECTION_RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            Network preferred = validCachedNetwork();
            if (preferred != null) {
                try {
                    JSONObject result = executeRequest(
                            preferred, request, attributedNpcId, tool, maxOutputTokens);
                    rememberNetwork(preferred);
                    return result;
                } catch (IOException error) {
                    if (firstFailure == null) firstFailure = error;
                    clearCachedNetwork(preferred);
                    if (!isSafeConnectionRetry(error)) {
                        throw networkFailure(error);
                    }
                }
            }