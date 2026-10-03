package com.sktpj.npcbrain;

import android.content.Context;

final class NpcInferenceAccess {
    private NpcInferenceAccess() {
    }

    static String selectedModel(Context context, String npcId) {
        return new NpcModelStore(context, npcId).selectedModel();
    }

    static boolean usesOpenAi(Context context, String npcId) {
        NpcModelStore store = new NpcModelStore(context, npcId);
        boolean specialistLlmActive =
                !new SpecialistInferenceSettingsStore(context).usesDecisionModel();
        return NpcInferenceModel.usesOpenAi(store.effectiveGlobalModel())
                || (specialistLlmActive
                && NpcInferenceModel.usesOpenAi(store.effectiveSpecialistModel()));
    }

    static boolean specialistUsesDecisionModel(Context context) {
        return new SpecialistInferenceSettingsStore(context).usesDecisionModel();
    }

    static boolean hasRequiredApiKey(Context context, String npcId, String apiKey) {
        if (!usesOpenAi(context, npcId)) return true;
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    static boolean hasOpenAiBudget(Context context, String npcId) {
        if (!usesOpenAi(context, npcId)) return true;
        return !new NpcAiStaminaStore(context).snapshot(npcId).exhausted();
    }

    static boolean canRun(Context context, String npcId, String apiKey) {
        return hasRequiredApiKey(context, npcId, apiKey) && hasOpenAiBudget(context, npcId);
    }
}
