package com.sktpj.npcbrain;

import org.json.JSONObject;

final class DungeonPlan {
    static final String SOURCE_BRAIN = "brain";
    static final String SOURCE_LOCAL = "local";

    final double riskTolerance;
    final double combatPreference;
    final double explorationPreference;
    final double persistence;
    final String summary;
    final String source;
    final String objectiveType;
    final int targetFloor;
    final int createdFloor;
    final int createdTurn;

    DungeonPlan(
            double riskTolerance,
            double combatPreference,
            double explorationPreference,
            double persistence,
            String summary,
            String source,
            String objectiveType,
            int targetFloor,
            int createdFloor,
            int createdTurn
    ) {
        this.riskTolerance = clamp01(riskTolerance);
        this.combatPreference = clamp01(combatPreference);
        this.explorationPreference = clamp01(explorationPreference);
        this.persistence = clamp01(persistence);
        this.summary = safe(summary);
        this.source = SOURCE_BRAIN.equals(source) ? SOURCE_BRAIN : SOURCE_LOCAL;
        this.objectiveType = DungeonObjective.REACH_TOP.equals(objectiveType)
                ? DungeonObjective.REACH_TOP : DungeonObjective.NONE;
        this.targetFloor = DungeonObjective.REACH_TOP.equals(this.objectiveType)
                ? DungeonObjective.TOP_FLOOR : 0;
        this.createdFloor = Math.max(1, createdFloor);
        this.createdTurn = Math.max(0, createdTurn);
    }

    static DungeonPlan local(
            DungeonObjective objective,
            DungeonPersonalityPolicy.Traits traits,
            DungeonState state,
            String reason
    ) {
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        DungeonPersonalityPolicy.Traits safeTraits = traits == null
                ? new DungeonPersonalityPolicy.Traits(50, 50, 50, 50, 50) : traits;
        double e = safeTraits.extraversion / 100.0;
        double n = safeTraits.neuroticism / 100.0;
        double a = safeTraits.agreeableness / 100.0;
        double c = safeTraits.conscientiousness / 100.0;
        double o = safeTraits.openness / 100.0;
        double risk = clamp01(0.48 + 0.34 * e - 0.42 * n);
        double combat = clamp01(0.30 + 0.52 * e - 0.28 * a - 0.18 * n);
        double explore = clamp01(0.32 + 0.60 * o);
        double persist = clamp01(0.40 + 0.55 * c);
        if (goal.isActive()) persist = Math.max(0.65, persist);
        String summary = goal.isActive()
                ? "ローカル計画: 生存しながら探索し、階段を見つけて" + goal.targetFloor + "Fへ進む"
                : "ローカル計画: 周囲を探索する";
        if (reason != null && !reason.trim().isEmpty()) summary += " · " + reason.trim();
        return new DungeonPlan(
                risk,
                combat,
                explore,
                persist,
                summary,
                SOURCE_LOCAL,
                goal.type,
                goal.targetFloor,
                state == null ? 1 : state.floor,
                state == null ? 0 : state.turn);
    }

    static DungeonPlan fromBrain(
            DungeonObjective objective,
            DungeonPersonalityPolicy.Traits traits,
            DungeonState state,
            DungeonIntent intent,
            String publicSummary
    ) {
        DungeonPlan base = local(objective, traits, state, "");
        DungeonIntent resolved = intent == null
                ? DungeonIntent.localFallback(state, traits, "Brain plan fallback") : intent;
        double targetRisk = 0.48;
        double targetCombat = 0.35;
        double targetExplore = 0.60;
        double targetPersistence = 0.82;
        switch (resolved.mode) {
            case DungeonIntent.ENGAGE:
                targetRisk = 0.82;
                targetCombat = 0.92;
                targetExplore = 0.35;
                targetPersistence = 0.76;
                break;
            case DungeonIntent.EVADE:
                targetRisk = 0.16;
                targetCombat = 0.18;
                targetExplore = 0.42;
                targetPersistence = 0.74;
                break;
            case DungeonIntent.SEEK_STAIRS:
                targetRisk = 0.44;
                targetCombat = 0.30;
                targetExplore = 0.32;
                targetPersistence = 0.98;
                break;
            case DungeonIntent.HOLD:
                targetRisk = 0.24;
                targetCombat = 0.22;
                targetExplore = 0.34;
                targetPersistence = 0.58;
                break;
            default:
                targetRisk = 0.48;
                targetCombat = 0.32;
                targetExplore = 0.96;
                targetPersistence = 0.84;
                break;
        }
        double blend = 0.35 + 0.55 * resolved.confidence;
        String summary = safe(publicSummary);
        if (summary.isEmpty()) {
            summary = "Brain計画: " + DungeonIntent.modeLabel(resolved.mode)
                    + "を軸に最上階を目指す";
        }
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        return new DungeonPlan(
                lerp(base.riskTolerance, targetRisk, blend),
                lerp(base.combatPreference, targetCombat, blend),
                lerp(base.explorationPreference, targetExplore, blend),
                lerp(base.persistence, targetPersistence, blend),
                summary,
                SOURCE_BRAIN,
                goal.type,
                goal.targetFloor,
                state == null ? 1 : state.floor,
                state == null ? 0 : state.turn);
    }

    boolean matches(DungeonObjective objective) {
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        return objectiveType.equals(goal.type) && targetFloor == goal.targetFloor;
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("risk_tolerance", riskTolerance);
            object.put("combat_preference", combatPreference);
            object.put("exploration_preference", explorationPreference);
            object.put("persistence", persistence);
            object.put("summary", summary);
            object.put("source", source);
            object.put("objective_type", objectiveType);
            object.put("target_floor", targetFloor);
            object.put("created_floor", createdFloor);
            object.put("created_turn", createdTurn);
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonPlan fromJson(JSONObject object) {
        if (object == null) return null;
        String objectiveType = object.optString("objective_type", DungeonObjective.NONE);
        if (!DungeonObjective.REACH_TOP.equals(objectiveType)
                && !DungeonObjective.NONE.equals(objectiveType)) return null;
        return new DungeonPlan(
                object.optDouble("risk_tolerance", 0.5),
                object.optDouble("combat_preference", 0.4),
                object.optDouble("exploration_preference", 0.6),
                object.optDouble("persistence", 0.7),
                object.optString("summary", ""),
                object.optString("source", SOURCE_LOCAL),
                objectiveType,
                object.optInt("target_floor", 0),
                object.optInt("created_floor", 1),
                object.optInt("created_turn", 0));
    }

    private static double lerp(double a, double b, double t) {
        double clamped = clamp01(t);
        return a + (b - a) * clamped;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.5;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
