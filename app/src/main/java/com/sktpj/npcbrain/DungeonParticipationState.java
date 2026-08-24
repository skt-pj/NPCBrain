package com.sktpj.npcbrain;

import org.json.JSONObject;

final class DungeonParticipationState {
    static final String NOT_ASKED = "not_asked";
    static final String REFUSE = "refuse";
    static final String HESITATE = "hesitate";
    static final String ACCEPT = "accept";
    static final String WITHDRAW = "withdraw";

    final String stance;
    final double willingness;
    final double fear;
    final double resolve;
    final String personalReason;
    final long updatedTimeMs;

    DungeonParticipationState(
            String stance,
            double willingness,
            double fear,
            double resolve,
            String personalReason,
            long updatedTimeMs
    ) {
        this.stance = normalizeStance(stance);
        this.willingness = clamp01(willingness);
        this.fear = clamp01(fear);
        this.resolve = clamp01(resolve);
        this.personalReason = limit(personalReason, 220);
        this.updatedTimeMs = Math.max(0L, updatedTimeMs);
    }

    static DungeonParticipationState initial() {
        return new DungeonParticipationState(
                NOT_ASKED,
                0.50,
                0.50,
                0.50,
                "",
                0L);
    }

    boolean isAccepted() {
        return ACCEPT.equals(stance);
    }

    String label() {
        switch (stance) {
            case REFUSE:
                return "拒否";
            case HESITATE:
                return "迷っている";
            case ACCEPT:
                return "参加を決意";
            case WITHDRAW:
                return "撤回・これ以上進みたくない";
            default:
                return "未相談";
        }
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("stance", stance);
            object.put("willingness", willingness);
            object.put("fear", fear);
            object.put("resolve", resolve);
            object.put("personal_reason", personalReason);
            object.put("updated_time_ms", updatedTimeMs);
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonParticipationState fromJson(JSONObject object) {
        if (object == null) return initial();
        String rawStance = object.optString("stance", NOT_ASKED);
        if (!isKnownStance(rawStance)) return initial();
        return new DungeonParticipationState(
                rawStance,
                object.optDouble("willingness", 0.50),
                object.optDouble("fear", 0.50),
                object.optDouble("resolve", 0.50),
                object.optString("personal_reason", ""),
                object.optLong("updated_time_ms", 0L));
    }

    private static boolean isKnownStance(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        return NOT_ASKED.equals(normalized)
                || REFUSE.equals(normalized)
                || HESITATE.equals(normalized)
                || ACCEPT.equals(normalized)
                || WITHDRAW.equals(normalized);
    }

    private static String normalizeStance(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        return isKnownStance(normalized) ? normalized : NOT_ASKED;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value.replace('\n', ' ').trim().replaceAll("\\s+", " ");
        return text.length() <= max ? text : text.substring(0, max);
    }
}
