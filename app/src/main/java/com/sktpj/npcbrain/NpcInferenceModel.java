package com.sktpj.npcbrain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Canonical per-NPC inference provider/model selection. */
final class NpcInferenceModel {
    static final String LOCAL_LIGHT = "local_light";
    static final String LOCAL_MEDIUM = "local_medium";
    static final String LOCAL_HEAVY = "local_heavy";
    static final String OPENAI_LUNA = "openai_luna";

    private static final List<String> SUPPORTED = Arrays.asList(
            LOCAL_LIGHT,
            LOCAL_MEDIUM,
            LOCAL_HEAVY,
            OPENAI_LUNA);

    private NpcInferenceModel() {
    }

    static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return SUPPORTED.contains(normalized) ? normalized : LOCAL_LIGHT;
    }

    static boolean usesOpenAi(String value) {
        return OPENAI_LUNA.equals(normalize(value));
    }

    static boolean isLocal(String value) {
        return !usesOpenAi(value);
    }

    static String[] supportedValues() {
        return SUPPORTED.toArray(new String[0]);
    }

    static String displayLabel(String value) {
        switch (normalize(value)) {
            case LOCAL_MEDIUM:
                return "中（ローカル）";
            case LOCAL_HEAVY:
                return "重い（ローカル）";
            case OPENAI_LUNA:
                return "OpenAI Luna";
            case LOCAL_LIGHT:
            default:
                return "軽い（ローカル）";
        }
    }
}
