from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected text not found in {rel}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Route every NPC-owned request before OpenAI body/network/reservation.
patch(
    "app/src/main/java/com/sktpj/npcbrain/OpenAiClient.java",
    '''    private JSONObject requestJsonInternal(String prompt, int maxOutputTokens) throws Exception {\n        FunctionTool tool = isGlobalWorkspacePrompt(prompt) ? FUNCTION_TOOL.get() : null;\n        JSONObject body = new JSONObject();''',
    '''    private JSONObject requestJsonInternal(String prompt, int maxOutputTokens) throws Exception {\n        FunctionTool tool = isGlobalWorkspacePrompt(prompt) ? FUNCTION_TOOL.get() : null;\n        JSONObject local = requestLocalIfSelected(prompt, maxOutputTokens, tool);\n        if (local != null) return local;\n        JSONObject body = new JSONObject();''')
patch(
    "app/src/main/java/com/sktpj/npcbrain/OpenAiClient.java",
    '''        String fullPrompt = prompt.fullText();\n        FunctionTool tool = isGlobalWorkspacePrompt(fullPrompt) ? FUNCTION_TOOL.get() : null;\n        JSONObject body = PromptCacheRequest.buildBody(''',
    '''        String fullPrompt = prompt.fullText();\n        FunctionTool tool = isGlobalWorkspacePrompt(fullPrompt) ? FUNCTION_TOOL.get() : null;\n        JSONObject local = requestLocalIfSelected(fullPrompt, maxOutputTokens, tool);\n        if (local != null) return local;\n        JSONObject body = PromptCacheRequest.buildBody(''')
patch(
    "app/src/main/java/com/sktpj/npcbrain/OpenAiClient.java",
    '''    private JSONObject executeLogicalRequest(\n            JSONObject body,''',
    '''    private JSONObject requestLocalIfSelected(\n            String fullPrompt,\n            int maxOutputTokens,\n            FunctionTool tool\n    ) throws Exception {\n        String npcId = attributedNpcId(fullPrompt);\n        if (npcId.isEmpty()) return null;\n        String selectedModel = new NpcModelStore(appContext, npcId).selectedModel();\n        if (NpcInferenceModel.usesOpenAi(selectedModel)) return null;\n        return new LocalLlmRuntime(appContext).requestJson(\n                npcId, selectedModel, fullPrompt, maxOutputTokens, tool);\n    }\n\n    private JSONObject executeLogicalRequest(\n            JSONObject body,''')

# Conversation UI must not require an OpenAI key for local models.
old_key_gate = '''        String apiKey;\n        try {\n            apiKey = apiKeyStore.load();\n        } catch (Exception error) {\n            showErrorDialog("APIキーを読み出せません: " + error.getMessage(), false);\n            return;\n        }\n        if (apiKey.isEmpty()) {\n            showMissingApiKeyDialog();\n            return;\n        }'''
new_key_gate = '''        String apiKey;\n        try {\n            apiKey = apiKeyStore.load();\n        } catch (Exception ignored) {\n            apiKey = "";\n        }'''
patch("app/src/main/java/com/sktpj/npcbrain/DemoActivityV032.java", old_key_gate, new_key_gate)
patch("app/src/main/java/com/sktpj/npcbrain/DemoActivityV032.java", old_key_gate, new_key_gate)
patch(
    "app/src/main/java/com/sktpj/npcbrain/DemoActivityV032.java",
    '''        final String apiKey;\n        try {\n            apiKey = apiKeyStore.load().trim();\n        } catch (Exception ignored) {\n            return;\n        }\n        if (apiKey.isEmpty() || !demoRuntime.hasDueSpontaneousEvents()) return;''',
    '''        String apiKey;\n        try {\n            apiKey = apiKeyStore.load().trim();\n        } catch (Exception ignored) {\n            apiKey = "";\n        }\n        if (!demoRuntime.hasDueSpontaneousEvents()) return;''')
patch(
    "app/src/main/java/com/sktpj/npcbrain/DemoActivityV032.java",
    '''        return "GPT-5.6 Luna · reasoning "\n                + ModelSettingsStore.displayLabel(modelSettingsStore.reasoningEffort())\n                + " · APIキー " + (hasApiKey() ? "設定済み" : "未設定");''',
    '''        return "NPC別推論モデル · OpenAI Luna reasoning "\n                + ModelSettingsStore.displayLabel(modelSettingsStore.reasoningEffort())\n                + " · APIキー " + (hasApiKey() ? "設定済み" : "未設定");''')

# Ambient/local thought gate: only OpenAI selection observes OpenAI key/budget.
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcInnerLifeRuntime.java",
    '''        if (apiKey == null || apiKey.trim().isEmpty() || staminaStore.snapshot(npcId).exhausted()) {\n            recordAmbientFallback(store, state, now);\n            return;\n        }''',
    '''        if (!NpcInferenceAccess.canRun(appContext, npcId, apiKey)) {\n            recordAmbientFallback(store, state, now);\n            return;\n        }''')

# Periodic memory/social job gates per NPC/provider rather than globally by OpenAI state.
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcSocialMemoryJobService.java",
    '''            String apiKey = new SecureApiKeyStore(this).load();\n            if (apiKey == null || apiKey.trim().isEmpty()) return;\n            String key = apiKey.trim();''',
    '''            String apiKey = new SecureApiKeyStore(this).load();\n            String key = apiKey == null ? "" : apiKey.trim();''')
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcSocialMemoryJobService.java",
    '''                NpcAiStaminaStore.Snapshot budget = new NpcAiStaminaStore(this).snapshot(npcId);\n                if (budget.exhausted()) continue;''',
    '''                if (!NpcInferenceAccess.canRun(this, npcId, key)) continue;''')
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcSocialMemoryJobService.java",
    '''                if (!actor.isEmpty() && !new NpcAiStaminaStore(this).snapshot(actor).exhausted()) {''',
    '''                if (!actor.isEmpty() && NpcInferenceAccess.canRun(this, actor, key)) {''')

# Profile reconciliation chooses the NPC provider; key is mandatory only for OpenAI Luna.
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcProfileReconciler.java",
    '''        String apiKey = new SecureApiKeyStore(appContext).load();\n        if (apiKey == null || apiKey.trim().isEmpty()) {\n            throw new IllegalStateException("AI設定でOpenAI APIキーを設定してください。");\n        }\n        String effort = new ModelSettingsStore(appContext).reasoningEffort();\n        OpenAiClient client = new OpenAiClient(appContext, apiKey.trim(), effort);''',
    '''        String apiKey = new SecureApiKeyStore(appContext).load();\n        String key = apiKey == null ? "" : apiKey.trim();\n        if (NpcInferenceAccess.usesOpenAi(appContext, npcId) && key.isEmpty()) {\n            throw new IllegalStateException("OpenAI Lunaを使うNPCにはAPIキーを設定してください。");\n        }\n        String effort = new ModelSettingsStore(appContext).reasoningEffort();\n        OpenAiClient client = new OpenAiClient(appContext, key, effort);''')

# Reply timers can wake a local NPC with no OpenAI key/budget.
patch(
    "app/src/main/java/com/sktpj/npcbrain/ReplyTimerJobService.java",
    '''            String apiKey = new SecureApiKeyStore(this).load();\n            if (apiKey == null || apiKey.trim().isEmpty()) {\n                retry = true;\n                return;\n            }\n            DemoRuntimeV032 runtime = new DemoRuntimeV032(this, new ConversationStore(this));\n            runtime.processReplyTimer(\n                    task,\n                    apiKey.trim(),''',
    '''            String apiKey = new SecureApiKeyStore(this).load();\n            String key = apiKey == null ? "" : apiKey.trim();\n            if (!NpcInferenceAccess.canRun(this, task.npcId, key)) {\n                retry = true;\n                return;\n            }\n            DemoRuntimeV032 runtime = new DemoRuntimeV032(this, new ConversationStore(this));\n            runtime.processReplyTimer(\n                    task,\n                    key,''')

# Dungeon budget is an OpenAI-only concern.
patch(
    "app/src/main/java/com/sktpj/npcbrain/DungeonBrainRuntime.java",
    '''        DungeonAiStaminaStore staminaStore = new DungeonAiStaminaStore(appContext);\n        if (staminaStore.snapshot(npcId).exhausted()) {\n            throw new IllegalStateException("AI STAMINA exhausted");\n        }''',
    '''        if (NpcInferenceAccess.usesOpenAi(appContext, npcId)\n                && new DungeonAiStaminaStore(appContext).snapshot(npcId).exhausted()) {\n            throw new IllegalStateException("OpenAI Luna AI STAMINA exhausted");\n        }''')

# Debug NPC manager: persist model selection only; actual model I/O stays on inference worker threads.
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcManagerActivity.java",
    '''        card.addView(meta, mp);\n\n        if (!store.isDead()) {''',
    '''        card.addView(meta, mp);\n\n        NpcModelStore modelStore = new NpcModelStore(this, npcId);\n        TextView inference = new TextView(this);\n        inference.setText("推論モデル  " + NpcInferenceModel.displayLabel(modelStore.selectedModel()));\n        inference.setTextColor(Color.rgb(52, 67, 101));\n        inference.setTextSize(13);\n        inference.setTypeface(Typeface.DEFAULT_BOLD);\n        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(\n                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);\n        ip.topMargin = dp(9);\n        card.addView(inference, ip);\n\n        if (!store.isDead()) {\n            Button model = new Button(this);\n            model.setText("モデルを変更");\n            model.setAllCaps(false);\n            model.setOnClickListener(v -> showModelDialog(npcId));\n            LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(\n                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));\n            modelParams.topMargin = dp(8);\n            card.addView(model, modelParams);''')
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcManagerActivity.java",
    '''    private void confirmBrainReset(String npcId, String displayName) {''',
    '''    private void showModelDialog(String npcId) {\n        NpcModelStore store = new NpcModelStore(this, npcId);\n        String current = store.selectedModel();\n        String[] values = NpcInferenceModel.supportedValues();\n        String[] labels = new String[values.length];\n        int checked = 0;\n        for (int i = 0; i < values.length; i++) {\n            labels[i] = NpcInferenceModel.displayLabel(values[i]);\n            if (values[i].equals(current)) checked = i;\n        }\n        new AlertDialog.Builder(this)\n                .setTitle("推論モデル")\n                .setSingleChoiceItems(labels, checked, (dialog, which) -> {\n                    if (which >= 0 && which < values.length) {\n                        store.setSelectedModel(values[which]);\n                        dialog.dismiss();\n                        renderList();\n                        Toast.makeText(this, "推論モデルを保存しました。", Toast.LENGTH_SHORT).show();\n                    }\n                })\n                .setNegativeButton("キャンセル", null)\n                .show();\n    }\n\n    private void confirmBrainReset(String npcId, String displayName) {''')

# Settings labels and cost editing are explicitly OpenAI-Luna-only.
patch(
    "app/src/main/java/com/sktpj/npcbrain/SettingsActivity.java",
    '        card.addView(text("AI設定", 18, AppUiTheme.APP_TEXT, true));',
    '        card.addView(text("OpenAI Luna設定", 18, AppUiTheme.APP_TEXT, true));')
patch(
    "app/src/main/java/com/sktpj/npcbrain/SettingsActivity.java",
    '        TextView budgetTitle = text("NPC別 AI費用", 18, AppUiTheme.APP_TEXT, true);',
    '        TextView budgetTitle = text("NPC別 OpenAI Luna費用", 18, AppUiTheme.APP_TEXT, true);')
patch(
    "app/src/main/java/com/sktpj/npcbrain/SettingsActivity.java",
    '''        List<String> ids = registryStore.npcIds();\n        if (ids.isEmpty()) {\n            budgetContainer.addView(text("NPCがありません。", 12, AppUiTheme.APP_MUTED, false));\n            return;\n        }\n        for (String npcId : ids) budgetContainer.addView(buildBudgetCard(npcId));''',
    '''        List<String> ids = registryStore.npcIds();\n        int shown = 0;\n        for (String npcId : ids) {\n            if (!NpcInferenceAccess.usesOpenAi(this, npcId)) continue;\n            budgetContainer.addView(buildBudgetCard(npcId));\n            shown++;\n        }\n        if (shown == 0) {\n            budgetContainer.addView(text(\n                    "OpenAI Lunaを選択中のNPCはいません。",\n                    12, AppUiTheme.APP_MUTED, false));\n        }''')

# Read-only stamina surfaces display local as unlimited, without evaluating a progress percentage.
patch(
    "app/src/main/java/com/sktpj/npcbrain/NpcAiUsageUiBridge.java",
    '''        String npcId = selectedNpcId(activity);\n        NpcAiStaminaStore.Snapshot snapshot = state.store.snapshot(npcId);''',
    '''        String npcId = selectedNpcId(activity);\n        if (!NpcInferenceAccess.usesOpenAi(activity, npcId)) {\n            state.value.setText("ローカル · 上限なし");\n            return;\n        }\n        NpcAiStaminaStore.Snapshot snapshot = state.store.snapshot(npcId);''')
patch(
    "app/src/main/java/com/sktpj/npcbrain/DungeonAiStaminaBridge.java",
    '''                    String npcId = selectedNpcId(activity);\n                    NpcAiStaminaStore.Snapshot snapshot = store.snapshot(npcId);''',
    '''                    String npcId = selectedNpcId(activity);\n                    if (!NpcInferenceAccess.usesOpenAi(activity, npcId)) {\n                        label.setText("AI STAMINA  ローカル · 上限なし");\n                        bar.setVisibility(View.GONE);\n                        stamina.setContentDescription(label.getText());\n                        stamina.postDelayed(this, REFRESH_MS);\n                        return;\n                    }\n                    bar.setVisibility(View.VISIBLE);\n                    NpcAiStaminaStore.Snapshot snapshot = store.snapshot(npcId);''')

# Keep local max_output_tokens bounded: choose the richest String overload rather than the 1-arg overload.
patch(
    "app/src/main/java/com/sktpj/npcbrain/LocalLlmRuntime.java",
    '''                if (types.length == 1) {\n                    candidate = method;\n                    break;\n                }\n                if (candidate == null || types.length > candidate.getParameterCount()) candidate = method;''',
    '''                if (candidate == null || types.length > candidate.getParameterCount()) candidate = method;''')

# Pure JVM tests for the canonical model/provider policy and fixed model identities.
test_path = ROOT / "app/src/test/java/com/sktpj/npcbrain/NpcInferenceModelTest.java"
test_path.write_text('''package com.sktpj.npcbrain;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic final class NpcInferenceModelTest {\n    @Test\n    public void defaultAndUnknownNormalizeToLocalLight() {\n        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize(null));\n        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize(""));\n        assertEquals(NpcInferenceModel.LOCAL_LIGHT, NpcInferenceModel.normalize("unknown"));\n    }\n\n    @Test\n    public void onlyOpenAiLunaUsesOpenAi() {\n        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_LIGHT));\n        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_MEDIUM));\n        assertFalse(NpcInferenceModel.usesOpenAi(NpcInferenceModel.LOCAL_HEAVY));\n        assertTrue(NpcInferenceModel.usesOpenAi(NpcInferenceModel.OPENAI_LUNA));\n    }\n\n    @Test\n    public void localModelSpecsArePinned() {\n        LocalModelRepository.ModelSpec light = LocalModelRepository.spec(NpcInferenceModel.LOCAL_LIGHT);\n        assertEquals("litert-community/Gemma3-1B-IT", light.repository);\n        assertEquals("42d538a932e8d5b12e6b3b455f5572560bd60b2c", light.revision);\n        assertEquals("gemma3-1b-it-int4.litertlm", light.fileName);\n\n        LocalModelRepository.ModelSpec medium = LocalModelRepository.spec(NpcInferenceModel.LOCAL_MEDIUM);\n        assertEquals("litert-community/Qwen2.5-1.5B-Instruct", medium.repository);\n        assertEquals("19edb84c69a0212f29a6ef17ba0d6f278b6a1614", medium.revision);\n        assertEquals("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", medium.fileName);\n\n        LocalModelRepository.ModelSpec heavy = LocalModelRepository.spec(NpcInferenceModel.LOCAL_HEAVY);\n        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", heavy.repository);\n        assertEquals("6e5c4f1e395deb959c494953478fa5cec4b8008f", heavy.revision);\n        assertEquals("gemma-4-E2B-it.litertlm", heavy.fileName);\n    }\n}\n''', encoding="utf-8")

# Source-level contract assertions: fail the patch if an intended boundary is absent.
checks = {
    "app/src/main/java/com/sktpj/npcbrain/OpenAiClient.java": [
        "requestLocalIfSelected(prompt, maxOutputTokens, tool)",
        "requestLocalIfSelected(fullPrompt, maxOutputTokens, tool)",
        "new LocalLlmRuntime(appContext).requestJson",
    ],
    "app/src/main/java/com/sktpj/npcbrain/SettingsActivity.java": [
        "OpenAI Lunaを選択中のNPCはいません。",
        "NpcInferenceAccess.usesOpenAi(this, npcId)",
    ],
    "app/src/main/java/com/sktpj/npcbrain/NpcManagerActivity.java": [
        "モデルを変更",
        "NpcInferenceModel.supportedValues()",
    ],
}
for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"post-patch assertion failed: {needle!r} in {rel}")

print("v0.4.49 patch applied")
