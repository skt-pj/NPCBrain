package com.sktpj.npcbrain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Canonical inference provider/model selection used by shared defaults and NPC overrides. */
final class NpcInferenceModel {
    static final String LOCAL_LIGHT = "local_light";
    static final String LOCAL_MEDIUM = "local_medium";
    static final String LOCAL_HEAVY = "local_heavy";
    static final String OPENAI_LUNA = "openai_luna";

    private static final List<String> LOCAL = Arrays.asList(
            LOCAL_LIGHT,
            LOCAL_MEDIUM,
            LOCAL_HEAVY);
    private static final List<String> CLOUD = Arrays.asList(OPENAI_LUNA);
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

    static String[] localValues() {
        return LOCAL.toArray(new String[0]);
    }

    static String[] cloudValues() {
        return CLOUD.toArray(new String[0]);
    }

    static String displayLabel(String value) {
        switch (normalize(value)) {
            case LOCAL_MEDIUM:
                return "Qwen2.5 1.5B Instruct";
            case LOCAL_HEAVY:
                return "Gemma 4 E2B";
            case OPENAI_LUNA:
                return "GPT-5.6 Luna";
            case LOCAL_LIGHT:
            default:
                return "Qwen2 0.5B Instruct";
        }
    }

    static String executionLocationLabel(String value) {
        return isLocal(value) ? "ローカル" : "クラウド";
    }

    static String providerLabel(String value) {
        return isLocal(value) ? "端末内 LiteRT-LM" : "OpenAI";
    }

    static String loadLabel(String value) {
        switch (normalize(value)) {
            case LOCAL_MEDIUM:
                return "中量";
            case LOCAL_HEAVY:
                return "高負荷";
            case OPENAI_LUNA:
                return "クラウド";
            case LOCAL_LIGHT:
            default:
                return "軽量";
        }
    }
}
