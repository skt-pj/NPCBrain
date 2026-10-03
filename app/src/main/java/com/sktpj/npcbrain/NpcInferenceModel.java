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
    static final String OPENAI_GPT56_LUNA = "openai_gpt56_luna";
    static final String OPENAI_GPT6_LUNA = "openai_gpt6_luna";

    private static final List<String> LOCAL = Arrays.asList(
            LOCAL_LIGHT,
            LOCAL_MEDIUM,
            LOCAL_HEAVY);
    private static final List<String> CLOUD = Arrays.asList(
            OPENAI_GPT6_LUNA,
            OPENAI_GPT56_LUNA);
    private static final List<String> SUPPORTED = Arrays.asList(
            LOCAL_LIGHT,
            LOCAL_MEDIUM,
            LOCAL_HEAVY,
            OPENAI_GPT56_LUNA,
            OPENAI_GPT6_LUNA);

    private NpcInferenceModel() {
    }

    static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (OPENAI_LUNA.equals(normalized)) return OPENAI_GPT56_LUNA;
        return SUPPORTED.contains(normalized) ? normalized : LOCAL_LIGHT;
    }

    static boolean usesOpenAi(String value) {
        String normalized = normalize(value);
        return OPENAI_GPT56_LUNA.equals(normalized)
                || OPENAI_GPT6_LUNA.equals(normalized);
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
            case OPENAI_GPT6_LUNA:
                return "GPT-6 Luna";
            case OPENAI_GPT56_LUNA:
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

    static String openAiApiModel(String value) {
        switch (normalize(value)) {
            case OPENAI_GPT6_LUNA:
                return "gpt-6-luna";
            case OPENAI_GPT56_LUNA:
            default:
                return "gpt-5.6-luna";
        }
    }

    static String loadLabel(String value) {
        switch (normalize(value)) {
            case LOCAL_MEDIUM:
                return "中量";
            case LOCAL_HEAVY:
                return "高負荷";
            case OPENAI_GPT6_LUNA:
            case OPENAI_GPT56_LUNA:
                return "クラウド";
            case LOCAL_LIGHT:
            default:
                return "軽量";
        }
    }
}
