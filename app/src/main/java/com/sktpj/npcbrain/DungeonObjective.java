package com.sktpj.npcbrain;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DungeonObjective {
    static final String NONE = "none";
    static final String REACH_TOP = "reach_top";
    static final String CUSTOM = "custom";
    static final int TOP_FLOOR = 10;
    static final int MAX_USER_TEXT_LENGTH = 200;

    private static final Pattern FLOOR_PATTERN = Pattern.compile("(?i)(10|[1-9])\\s*(?:f|階)");

    final String type;
    final int targetFloor;
    final long createdTimeMs;
    final String userText;

    DungeonObjective(String type, int targetFloor, long createdTimeMs) {
        this(type, targetFloor, createdTimeMs, "");
    }

    DungeonObjective(String type, int targetFloor, long createdTimeMs, String userText) {
        String normalized = normalizeUserText(userText);
        if (REACH_TOP.equals(type)) {
            this.type = REACH_TOP;
            this.targetFloor = TOP_FLOOR;
            this.userText = "最上階を目指す";
        } else if (isCustomType(type)) {
            if (normalized.isEmpty()) {
                this.type = NONE;
                this.targetFloor = 0;
                this.userText = "";
            } else {
                this.type = customIdentity(normalized);
                int explicitTarget = clampTargetFloor(targetFloor);
                this.targetFloor = explicitTarget > 0
                        ? explicitTarget : inferExplicitTargetFloor(normalized);
                this.userText = normalized;
            }
        } else {
            this.type = NONE;
            this.targetFloor = 0;
            this.userText = "";
        }
        this.createdTimeMs = Math.max(0L, createdTimeMs);
    }

    static DungeonObjective none() {
        return new DungeonObjective(NONE, 0, 0L, "");
    }

    static DungeonObjective reachTop(long createdTimeMs) {
        return new DungeonObjective(REACH_TOP, TOP_FLOOR, createdTimeMs, "最上階を目指す");
    }

    static DungeonObjective custom(String userText, long createdTimeMs) {
        String normalized = normalizeUserText(userText);
        if (normalized.isEmpty()) return none();
        return new DungeonObjective(CUSTOM, inferExplicitTargetFloor(normalized), createdTimeMs, normalized);
    }

    static DungeonObjective customWithTarget(
            String userText,
            int targetFloor,
            long createdTimeMs
    ) {
        String normalized = normalizeUserText(userText);
        if (normalized.isEmpty()) return none();
        return new DungeonObjective(CUSTOM, targetFloor, createdTimeMs, normalized);
    }

    static DungeonObjective fromUserText(String userText, long createdTimeMs) {
        String normalized = normalizeUserText(userText);
        if (normalized.isEmpty()) return none();
        if ("最上階を目指す".equals(normalized)
                || "最上階へ".equals(normalized)
                || "最上階まで行く".equals(normalized)) {
            return reachTop(createdTimeMs);
        }
        return custom(normalized, createdTimeMs);
    }

    boolean isActive() {
        return REACH_TOP.equals(type) || isCustomType(type);
    }

    boolean isCustom() {
        return isCustomType(type);
    }

    String kind() {
        return isCustom() ? CUSTOM : type;
    }

    boolean isComplete(int floor) {
        return isActive() && targetFloor > 0 && floor >= targetFloor;
    }

    String label() {
        if (REACH_TOP.equals(type)) return "最上階へ到達 (" + targetFloor + "F)";
        if (isCustom()) return userText;
        return "未設定";
    }

    String rawUserText() {
        if (REACH_TOP.equals(type)) return "最上階を目指す";
        return userText;
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("type", kind());
            object.put("identity_key", type);
            object.put("target_floor", targetFloor);
            object.put("created_time_ms", createdTimeMs);
            object.put("user_text", rawUserText());
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonObjective fromJson(JSONObject object) {
        if (object == null) return none();
        String kind = object.optString("type", NONE);
        if (REACH_TOP.equals(kind)) {
            return reachTop(object.optLong("created_time_ms", 0L));
        }
        if (CUSTOM.equals(kind) || isCustomType(kind)) {
            String text = normalizeUserText(object.optString("user_text", ""));
            if (text.isEmpty()) return none();
            return new DungeonObjective(
                    CUSTOM,
                    object.optInt("target_floor", 0),
                    object.optLong("created_time_ms", 0L),
                    text);
        }
        return none();
    }

    static String normalizeUserText(String value) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_USER_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_USER_TEXT_LENGTH).trim();
        }
        return normalized;
    }

    static int inferExplicitTargetFloor(String value) {
        String text = normalizeUserText(value);
        if (text.isEmpty()) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("最上階") || lower.contains("てっぺん") || lower.contains("最上層")) {
            return TOP_FLOOR;
        }
        Matcher matcher = FLOOR_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return clampTargetFloor(Integer.parseInt(matcher.group(1)));
            } catch (Exception ignored) {
            }
        }
        String[] kanji = {"一階", "二階", "三階", "四階", "五階", "六階", "七階", "八階", "九階", "十階"};
        for (int i = 0; i < kanji.length; i++) {
            if (text.contains(kanji[i])) return i + 1;
        }
        return 0;
    }

    static boolean sameGoal(DungeonObjective a, DungeonObjective b) {
        DungeonObjective left = a == null ? none() : a;
        DungeonObjective right = b == null ? none() : b;
        if (!left.type.equals(right.type)) return false;
        if (!left.rawUserText().equals(right.rawUserText())) return false;
        if (left.isCustom() && right.isCustom()) return true;
        return left.targetFloor == right.targetFloor;
    }

    private static int clampTargetFloor(int value) {
        if (value <= 0) return 0;
        return Math.min(TOP_FLOOR, value);
    }

    private static boolean isCustomType(String value) {
        return value != null && (CUSTOM.equals(value) || value.startsWith(CUSTOM + ":"));
    }

    private static String customIdentity(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(CUSTOM).append(':');
            for (int i = 0; i < 6; i++) {
                out.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
            }
            return out.toString();
        } catch (Exception ignored) {
            return CUSTOM + ":" + Integer.toHexString(normalized.hashCode());
        }
    }
}
