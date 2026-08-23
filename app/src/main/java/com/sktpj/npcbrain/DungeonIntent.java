package com.sktpj.npcbrain;

import org.json.JSONObject;

final class DungeonIntent {
    static final String EXPLORE = "explore";
    static final String SEEK_STAIRS = "seek_stairs";
    static final String ENGAGE = "engage";
    static final String EVADE = "evade";
    static final String HOLD = "hold";

    static final String SOURCE_BRAIN = "brain";
    static final String SOURCE_LOCAL = "local";

    final String mode;
    final DungeonPersonalityPolicy.Direction preferredDirection;
    final String targetId;
    final double confidence;
    final String summary;
    final String source;
    final int floor;
    final int turn;

    DungeonIntent(
            String mode,
            DungeonPersonalityPolicy.Direction preferredDirection,
            String targetId,
            double confidence,
            String summary,
            String source,
            int floor,
            int turn
    ) {
        this.mode = normalizeMode(mode);
        this.preferredDirection = preferredDirection == null
                ? DungeonPersonalityPolicy.Direction.WAIT : preferredDirection;
        this.targetId = safe(targetId);
        this.confidence = clamp01(confidence);
        this.summary = safe(summary);
        this.source = SOURCE_BRAIN.equals(source) ? SOURCE_BRAIN : SOURCE_LOCAL;
        this.floor = Math.max(1, floor);
        this.turn = Math.max(0, turn);
    }

    static DungeonIntent fromEnvironmentAction(
            JSONObject environmentAction,
            int floor,
            int turn,
            String publicSummary
    ) {
        if (environmentAction == null) return null;
        String type = safe(environmentAction.optString("type", "none")).toLowerCase();
        String mode = normalizeMode(environmentAction.optString("intent", HOLD));
        DungeonPersonalityPolicy.Direction direction = parseDirection(
                environmentAction.optString("direction", "none"));
        if (!("move".equals(type) || "attack".equals(type) || "wait".equals(type))) {
            return null;
        }
        if ("wait".equals(type)) direction = DungeonPersonalityPolicy.Direction.WAIT;
        if (("move".equals(type) || "attack".equals(type))
                && direction == DungeonPersonalityPolicy.Direction.WAIT) {
            return null;
        }
        return new DungeonIntent(
                mode,
                direction,
                environmentAction.optString("target_id", ""),
                environmentAction.optDouble("confidence", 0.5),
                publicSummary,
                SOURCE_BRAIN,
                floor,
                turn);
    }

    static DungeonIntent localFallback(
            DungeonState state,
            DungeonPersonalityPolicy.Traits traits,
            String reason
    ) {
        if (state == null) {
            return new DungeonIntent(HOLD, DungeonPersonalityPolicy.Direction.WAIT,
                    "", 1.0, "状態待機", SOURCE_LOCAL, 1, 0);
        }
        String mode = EXPLORE;
        int visibleEnemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        double hpRate = state.maxHp <= 0 ? 0.0 : state.hp / (double) state.maxHp;
        if (hpRate <= 0.30 && visibleEnemyDistance < 999) {
            mode = EVADE;
        } else if (visibleEnemyDistance == 1) {
            double caution = traits == null ? 0.5
                    : (traits.neuroticism + traits.agreeableness) / 200.0;
            mode = hpRate <= 0.45 && caution >= 0.65 ? EVADE : ENGAGE;
        } else if (DungeonPerception.stairsKnown(state)) {
            mode = SEEK_STAIRS;
        } else if (visibleEnemyDistance <= 3 && traits != null
                && traits.extraversion > traits.neuroticism + 15) {
            mode = ENGAGE;
        }
        String summary = "ローカル方策";
        if (reason != null && !reason.trim().isEmpty()) summary += " · " + reason.trim();
        return new DungeonIntent(
                mode,
                DungeonPersonalityPolicy.Direction.WAIT,
                "",
                1.0,
                summary,
                SOURCE_LOCAL,
                state.floor,
                state.turn);
    }

    boolean isBrain() {
        return SOURCE_BRAIN.equals(source);
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("mode", mode);
            object.put("preferred_direction", directionName(preferredDirection));
            object.put("target_id", targetId);
            object.put("confidence", confidence);
            object.put("summary", summary);
            object.put("source", source);
            object.put("floor", floor);
            object.put("turn", turn);
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonIntent fromJson(JSONObject object) {
        if (object == null) return null;
        return new DungeonIntent(
                object.optString("mode", HOLD),
                parseDirection(object.optString("preferred_direction", "none")),
                object.optString("target_id", ""),
                object.optDouble("confidence", 0.0),
                object.optString("summary", ""),
                object.optString("source", SOURCE_LOCAL),
                object.optInt("floor", 1),
                object.optInt("turn", 0));
    }

    static String modeLabel(String mode) {
        switch (normalizeMode(mode)) {
            case SEEK_STAIRS: return "階段を探す";
            case ENGAGE: return "交戦する";
            case EVADE: return "距離を取る";
            case HOLD: return "警戒する";
            default: return "探索する";
        }
    }

    static String normalizeMode(String raw) {
        String value = safe(raw).toLowerCase();
        if (SEEK_STAIRS.equals(value)) return SEEK_STAIRS;
        if (ENGAGE.equals(value)) return ENGAGE;
        if (EVADE.equals(value)) return EVADE;
        if (HOLD.equals(value)) return HOLD;
        return EXPLORE;
    }

    static DungeonPersonalityPolicy.Direction parseDirection(String raw) {
        String value = safe(raw).toLowerCase();
        if ("up".equals(value)) return DungeonPersonalityPolicy.Direction.UP;
        if ("down".equals(value)) return DungeonPersonalityPolicy.Direction.DOWN;
        if ("left".equals(value)) return DungeonPersonalityPolicy.Direction.LEFT;
        if ("right".equals(value)) return DungeonPersonalityPolicy.Direction.RIGHT;
        return DungeonPersonalityPolicy.Direction.WAIT;
    }

    static String directionName(DungeonPersonalityPolicy.Direction direction) {
        if (direction == null) return "none";
        switch (direction) {
            case UP: return "up";
            case DOWN: return "down";
            case LEFT: return "left";
            case RIGHT: return "right";
            default: return "none";
        }
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
