from pathlib import Path

path = Path('app/src/main/java/com/sktpj/npcbrain/OpenAiClient.java')
text = path.read_text(encoding='utf-8')
old = '''    private String beginDiagnosticLlmRequest(String fullPrompt) {
    try {
        String npcId = attributedNpcId(fullPrompt);
        if (npcId.isEmpty()) return "";
        String selectedModel = new NpcModelStore(appContext, npcId).selectedModel();
        String stage = isGlobalWorkspacePrompt(fullPrompt) ? "Global Workspace" : "specialist / task";
        return ProcessingQueueRegistry.startRunning(
                "llm_request",
                npcId,
                selectedModel + " · " + stage,
                System.currentTimeMillis());
    } catch (Exception ignored) {
        return "";
    }
}
'''
new = '''    private String beginDiagnosticLlmRequest(String fullPrompt) {
        try {
            String npcId = attributedNpcId(fullPrompt);
            if (npcId.isEmpty()) return "";
            String selectedModel = new NpcModelStore(appContext, npcId).selectedModel();
            String stage = diagnosticBrainStage(fullPrompt);
            return ProcessingQueueRegistry.startRunning(
                    "llm_request",
                    npcId,
                    selectedModel + " · brain_stage=" + stage,
                    System.currentTimeMillis());
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
        return "specialist";
    }
'''
if text.count(old) != 1:
    raise SystemExit(f'expected diagnostic block exactly once, found {text.count(old)}')
path.write_text(text.replace(old, new), encoding='utf-8')
