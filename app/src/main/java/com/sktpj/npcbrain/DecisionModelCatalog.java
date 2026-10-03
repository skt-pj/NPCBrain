package com.sktpj.npcbrain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class DecisionModelCatalog {
    static final String LOCAL_CLEF_FLASH_Q4_K_M = "local_clef_flash_q4_k_m";
    static final String CLOUD_CLEF_FLASH = "cloud_clef_flash";
    static final String CLOUD_CLEF = "cloud_clef";

    private static final List<String> LOCAL = Arrays.asList(
            LOCAL_CLEF_FLASH_Q4_K_M);
    private static final List<String> CLOUD = Arrays.asList(
            CLOUD_CLEF_FLASH,
            CLOUD_CLEF);
    private static final List<String> ALL = Arrays.asList(
            LOCAL_CLEF_FLASH_Q4_K_M,
            CLOUD_CLEF_FLASH,
            CLOUD_CLEF);

    private DecisionModelCatalog() {
    }

    static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return ALL.contains(normalized) ? normalized : LOCAL_CLEF_FLASH_Q4_K_M;
    }

    static boolean isLocal(String value) {
        return LOCAL.contains(normalize(value));
    }

    static boolean isCloud(String value) {
        return CLOUD.contains(normalize(value));
    }

    static String[] localValues() {
        return LOCAL.toArray(new String[0]);
    }

    static String[] cloudValues() {
        return CLOUD.toArray(new String[0]);
    }

    static String displayLabel(String value) {
        switch (normalize(value)) {
            case CLOUD_CLEF:
                return "Clef 27B";
            case CLOUD_CLEF_FLASH:
                return "Clef-flash 9B";
            case LOCAL_CLEF_FLASH_Q4_K_M:
            default:
                return "Clef-flash 9B Q4_K_M";
        }
    }

    static String executionLocationLabel(String value) {
        return isLocal(value) ? "ローカル" : "クラウド";
    }

    static String providerLabel(String value) {
        return isLocal(value) ? "端末内 llama.cpp" : "Cloudflare Workers AI";
    }

    static String cloudflareModel(String value) {
        return CLOUD_CLEF.equals(normalize(value)) ? "clef" : "clef-flash";
    }

    static String cloudflareEndpointModel(String value) {
        return CLOUD_CLEF.equals(normalize(value))
                ? "@cf/cloudflare/clef"
                : "@cf/cloudflare/clef-flash";
    }
}
