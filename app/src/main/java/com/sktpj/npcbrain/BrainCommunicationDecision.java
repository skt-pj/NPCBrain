package com.sktpj.npcbrain;

import org.json.JSONObject;

final class BrainCommunicationDecision {
    static final String NONE = "none";
    static final String SEND = "send";
    static final String DEFER = "defer";
    static final String SKIP = "skip";

    private final boolean valid;
    private final String decision;
    private final String targetId;
    private final long deferUntilMs;

    private BrainCommunicationDecision(
            boolean valid,
            String decision,
            String targetId,
            long deferUntilMs
    ) {
        this.valid = valid;
        this.decision = decision;
        this.targetId = targetId;
        this.deferUntilMs = deferUntilMs;
    }

    static BrainCommunicationDecision none() {
        return new BrainCommunicationDecision(true, NONE, "", 0L);
    }

    static BrainCommunicationDecision fromJson(JSONObject root) {
        if (root == null) return none();
        JSONObject communication = root.optJSONObject("communication");
        if (communication == null) return none();

        String rawDecision = normalize(communication.optString("decision", NONE));
        if (!isKnownDecision(rawDecision)) {
            return new BrainCommunicationDecision(false, rawDecision, "", 0L);
        }
        String targetId = normalize(communication.optString("target_id", ""));
        long deferUntilMs = communication.optLong("defer_until_ms", 0L);
        return new BrainCommunicationDecision(true, rawDecision, targetId, deferUntilMs);
    }

    boolean valid() {
        return valid;
    }

    String decision() {
        return decision;
    }

    String targetId() {
        return targetId;
    }

    long deferUntilMs() {
        return deferUntilMs;
    }

    boolean isNone() {
        return NONE.equals(decision);
    }

    boolean isSend() {
        return SEND.equals(decision);
    }

    boolean isDefer() {
        return DEFER.equals(decision);
    }

    boolean isSkip() {
        return SKIP.equals(decision);
    }

    private static boolean isKnownDecision(String value) {
        return NONE.equals(value) || SEND.equals(value) || DEFER.equals(value) || SKIP.equals(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
    }
}
