package com.sktpj.npcbrain;

import org.json.JSONObject;

final class DungeonPlan {
    static final String SOURCE_BRAIN = "brain";
    static final String SOURCE_LOCAL = "local";

    static final String STRATEGY_ADVANCE = "advance";
    static final String STRATEGY_HUNT = "hunt";
    static final String STRATEGY_EXPLORE = "explore";
    static final String STRATEGY_SURVIVE = "survive";
    static final String STRATEGY_BALANCED = "balanced";

    private static final String BRAIN_PAYLOAD_PREFIX = "__NPCBRAIN_DUNGEON_PLAN_V1__";

    final double riskTolerance;
    final double combatPreference;
    final double explorationPreference;
    final double progressPreference;
    final double persistence;
    final double confidence;
    final String interpretation;
    final String strategy;
    final String summary;
    final String source;
    final String objectiveType;
    final String objectiveIdentity;
    final String objectiveText;
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
        this(
                riskTolerance,
                combatPreference,
                explorationPreference,
                DungeonObjective.REACH_TOP.equals(objectiveType) ? 0.90 : 0.60,
                persistence,
                0.5,
                "",
                DungeonObjective.REACH_TOP.equals(objectiveType)
                        ? STRATEGY_ADVANCE : STRATEGY_BALANCED,
                summary,
                source,
                objectiveType,
                objectiveType,
                "",
                targetFloor,
                createdFloor,
                createdTurn);
    }

    DungeonPlan(
            double riskTolerance,
            double combatPreference,
            double explorationPreference,
            double progressPreference,
            double persistence,
            double confidence,
            String interpretation,
            String strategy,
            String summary,
            String source,
            String objectiveType,
            String objectiveIdentity,
            String objectiveText,
            int targetFloor,
            int createdFloor,
            int createdTurn
    ) {
        this.riskTolerance = clamp01(riskTolerance);
        this.combatPreference = clamp01(combatPreference);
        this.explorationPreference = clamp01(explorationPreference);
        this.progressPreference = clamp01(progressPreference);
        this.persistence = clamp01(persistence);
        this.confidence = clamp01(confidence);
        this.interpretation = limitText(interpretation, 280);
        this.strategy = normalizeStrategy(strategy);
        this.summary = limitText(summary, 280);
        this.source = SOURCE_BRAIN.equals(source) ? SOURCE_BRAIN : SOURCE_LOCAL;
        this.objectiveType = normalizeObjectiveType(objectiveType);
        this.objectiveIdentity = safe(objectiveIdentity);
        this.objectiveText = DungeonObjective.normalizeUserText(objectiveText);
        this.targetFloor = normalizeTargetFloor(this.objectiveType, targetFloor);
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
        double progress = clamp01(0.46 + 0.46 * c);
        double persist = clamp01(0.40 + 0.55 * c);
        String strategy = STRATEGY_BALANCED;
        if (goal.isActive() && goal.targetFloor > 0) {
            progress = Math.max(0.72, progress);
            persist = Math.max(0.65, persist);
            strategy = STRATEGY_ADVANCE;
        } else if (goal.isCustom()) {
            if (n >= 0.72) strategy = STRATEGY_SURVIVE;
            else if (e >= 0.72 && a <= 0.45) strategy = STRATEGY_HUNT;
            else if (o >= 0.68) strategy = STRATEGY_EXPLORE;
        }
        String interpretation;
        String summary;
        if (goal.isCustom()) {
            interpretation = "Brainの解釈待ち。目的原文を保持し、人格ベースで安全に行動します。";
            summary = goal.targetFloor > 0
                    ? "ローカル計画: 人格に沿って探索し、" + goal.targetFloor + "Fを目安に進む"
                    : "ローカル計画: 人格に沿って探索を継続する";
        } else if (goal.isActive()) {
            interpretation = "最上階へ進む目的として扱います。";
            summary = "ローカル計画: 生存しながら探索し、階段を見つけて" + goal.targetFloor + "Fへ進む";
        } else {
            interpretation = "目的は設定されていません。";
            summary = "ローカル計画: 周囲を探索する";
        }
        if (reason != null && !reason.trim().isEmpty()) summary += " · " + reason.trim();
        return new DungeonPlan(
                risk,
                combat,
                explore,
                progress,
                persist,
                0.35,
                interpretation,
                strategy,
                summary,
                SOURCE_LOCAL,
                goal.kind(),
                goal.type,
                goal.rawUserText(),
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
        BrainPayload payload = decodeBrainPayload(publicSummary);
        if (payload != null) {
            return fromStructuredBrain(
                    objective,
                    traits,
                    state,
                    intent,
                    payload.plan,
                    payload.publicSummary);
        }
        return fromIntentFallback(objective, traits, state, intent, publicSummary);
    }

    static DungeonPlan fromStructuredBrain(
            DungeonObjective objective,
            DungeonPersonalityPolicy.Traits traits,
            DungeonState state,
            DungeonIntent intent,
            JSONObject structured,
            String publicSummary
    ) {
        if (structured == null || !structured.optBoolean("applicable", false)) {
            return fromIntentFallback(objective, traits, state, intent, publicSummary);
        }
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        DungeonPlan base = local(goal, traits, state, "");
        double planConfidence = clamp01(structured.optDouble("confidence", 0.65));
        double blend = 0.65 + 0.30 * planConfidence;
        double risk = lerp(base.riskTolerance,
                structured.optDouble("risk_tolerance", base.riskTolerance), blend);
        double combat = lerp(base.combatPreference,
                structured.optDouble("combat_preference", base.combatPreference), blend);
        double explore = lerp(base.explorationPreference,
                structured.optDouble("exploration_preference", base.explorationPreference), blend);
        double progress = lerp(base.progressPreference,
                structured.optDouble("progress_preference", base.progressPreference), blend);
        double persist = lerp(base.persistence,
                structured.optDouble("persistence", base.persistence), blend);
        String interpretation = limitText(
                structured.optString("objective_interpretation", ""), 280);
        if (interpretation.isEmpty()) {
            interpretation = goal.isCustom()
                    ? "「" + goal.rawUserText() + "」を人格と現在状況に沿って実行します。"
                    : "最上階を目指す方針として解釈しました。";
        }
        String strategy = normalizeStrategy(structured.optString("strategy", STRATEGY_BALANCED));
        int target = clampTargetFloor(structured.optInt("target_floor", 0));
        if (DungeonObjective.REACH_TOP.equals(goal.kind())) target = DungeonObjective.TOP_FLOOR;
        if (target <= 0 && goal.targetFloor > 0) target = goal.targetFloor;
        String planSummary = limitText(structured.optString("plan_summary", ""), 280);
        if (planSummary.isEmpty()) planSummary = limitText(publicSummary, 280);
        if (planSummary.isEmpty()) {
            planSummary = "Brain計画: " + strategyLabel(strategy)
                    + (target > 0 ? "で" + target + "Fを目指す" : "を継続する");
        }
        return new DungeonPlan(
                risk,
                combat,
                explore,
                progress,
                persist,
                planConfidence,
                interpretation,
                strategy,
                planSummary,
                SOURCE_BRAIN,
                goal.kind(),
                goal.type,
                goal.rawUserText(),
                target,
                state == null ? 1 : state.floor,
                state == null ? 0 : state.turn);
    }

    private static DungeonPlan fromIntentFallback(
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
        double targetProgress = 0.72;
        double targetPersistence = 0.82;
        String strategy = STRATEGY_BALANCED;
        switch (resolved.mode) {
            case DungeonIntent.ENGAGE:
                targetRisk = 0.82;
                targetCombat = 0.92;
                targetExplore = 0.42;
                targetProgress = 0.42;
                targetPersistence = 0.76;
                strategy = STRATEGY_HUNT;
                break;
            case DungeonIntent.EVADE:
                targetRisk = 0.16;
                targetCombat = 0.18;
                targetExplore = 0.42;
                targetProgress = 0.48;
                targetPersistence = 0.74;
                strategy = STRATEGY_SURVIVE;
                break;
            case DungeonIntent.SEEK_STAIRS:
                targetRisk = 0.44;
                targetCombat = 0.30;
                targetExplore = 0.32;
                targetProgress = 0.98;
                targetPersistence = 0.98;
                strategy = STRATEGY_ADVANCE;
                break;
            case DungeonIntent.HOLD:
                targetRisk = 0.24;
                targetCombat = 0.22;
                targetExplore = 0.34;
                targetProgress = 0.40;
                targetPersistence = 0.58;
                strategy = STRATEGY_SURVIVE;
                break;
            default:
                targetRisk = 0.48;
                targetCombat = 0.32;
                targetExplore = 0.96;
                targetProgress = 0.62;
                targetPersistence = 0.84;
                strategy = STRATEGY_EXPLORE;
                break;
        }
        double blend = 0.35 + 0.55 * resolved.confidence;
        String summary = safe(publicSummary);
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        if (summary.isEmpty()) {
            summary = "Brain計画: " + DungeonIntent.modeLabel(resolved.mode)
                    + (goal.targetFloor > 0 ? "を軸に" + goal.targetFloor + "Fを目指す" : "を軸に行動する");
        }
        return new DungeonPlan(
                lerp(base.riskTolerance, targetRisk, blend),
                lerp(base.combatPreference, targetCombat, blend),
                lerp(base.explorationPreference, targetExplore, blend),
                lerp(base.progressPreference, targetProgress, blend),
                lerp(base.persistence, targetPersistence, blend),
                resolved.confidence,
                goal.isCustom()
                        ? "目的をBrainの現在判断と人格に沿って実行します。"
                        : "最上階へ進む目的として解釈しました。",
                strategy,
                summary,
                SOURCE_BRAIN,
                goal.kind(),
                goal.type,
                goal.rawUserText(),
                goal.targetFloor,
                state == null ? 1 : state.floor,
                state == null ? 0 : state.turn);
    }

    boolean matches(DungeonObjective objective) {
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        if (!objectiveIdentity.isEmpty()) {
            return objectiveIdentity.equals(goal.type)
                    && objectiveText.equals(goal.rawUserText());
        }
        return objectiveType.equals(goal.kind())
                && (targetFloor == goal.targetFloor
                || DungeonObjective.CUSTOM.equals(objectiveType));
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("risk_tolerance", riskTolerance);
            object.put("combat_preference", combatPreference);
            object.put("exploration_preference", explorationPreference);
            object.put("progress_preference", progressPreference);
            object.put("persistence", persistence);
            object.put("confidence", confidence);
            object.put("interpretation", interpretation);
            object.put("strategy", strategy);
            object.put("summary", summary);
            object.put("source", source);
            object.put("objective_type", objectiveType);
            object.put("objective_identity", objectiveIdentity);
            object.put("objective_text", objectiveText);
            object.put("target_floor", targetFloor);
            object.put("created_floor", createdFloor);
            object.put("created_turn", createdTurn);
        } catch (Exception ignored) {
        }
        return object;
    }

    static DungeonPlan fromJson(JSONObject object) {
        if (object == null) return null;
        String objectiveType = normalizeObjectiveType(
                object.optString("objective_type", DungeonObjective.NONE));
        if (DungeonObjective.NONE.equals(objectiveType)
                && !DungeonObjective.NONE.equals(object.optString("objective_type", DungeonObjective.NONE))) {
            return null;
        }
        return new DungeonPlan(
                object.optDouble("risk_tolerance", 0.5),
                object.optDouble("combat_preference", 0.4),
                object.optDouble("exploration_preference", 0.6),
                object.optDouble("progress_preference",
                        DungeonObjective.REACH_TOP.equals(objectiveType) ? 0.90 : 0.60),
                object.optDouble("persistence", 0.7),
                object.optDouble("confidence", 0.5),
                object.optString("interpretation", ""),
                object.optString("strategy",
                        DungeonObjective.REACH_TOP.equals(objectiveType)
                                ? STRATEGY_ADVANCE : STRATEGY_BALANCED),
                object.optString("summary", ""),
                object.optString("source", SOURCE_LOCAL),
                objectiveType,
                object.optString("objective_identity", object.optString("objective_type", "")),
                object.optString("objective_text", ""),
                object.optInt("target_floor", 0),
                object.optInt("created_floor", 1),
                object.optInt("created_turn", 0));
    }

    static String encodeBrainPayload(JSONObject structuredPlan, String publicSummary) {
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("plan", structuredPlan == null
                    ? new JSONObject() : new JSONObject(structuredPlan.toString()));
            wrapper.put("public_summary", publicSummary == null ? "" : publicSummary.trim());
            return BRAIN_PAYLOAD_PREFIX + wrapper.toString();
        } catch (Exception ignored) {
            return publicSummary == null ? "" : publicSummary.trim();
        }
    }

    static String normalizeStrategy(String value) {
        if (STRATEGY_ADVANCE.equals(value)
                || STRATEGY_HUNT.equals(value)
                || STRATEGY_EXPLORE.equals(value)
                || STRATEGY_SURVIVE.equals(value)
                || STRATEGY_BALANCED.equals(value)) return value;
        return STRATEGY_BALANCED;
    }

    static String strategyLabel(String strategy) {
        switch (normalizeStrategy(strategy)) {
            case STRATEGY_ADVANCE:
                return "進行優先";
            case STRATEGY_HUNT:
                return "交戦優先";
            case STRATEGY_EXPLORE:
                return "探索優先";
            case STRATEGY_SURVIVE:
                return "生存優先";
            default:
                return "均衡";
        }
    }

    private static BrainPayload decodeBrainPayload(String value) {
        if (value == null || !value.startsWith(BRAIN_PAYLOAD_PREFIX)) return null;
        try {
            JSONObject wrapper = new JSONObject(value.substring(BRAIN_PAYLOAD_PREFIX.length()));
            return new BrainPayload(
                    wrapper.optJSONObject("plan"),
                    wrapper.optString("public_summary", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeObjectiveType(String value) {
        if (DungeonObjective.REACH_TOP.equals(value)) return DungeonObjective.REACH_TOP;
        if (DungeonObjective.CUSTOM.equals(value)
                || (value != null && value.startsWith(DungeonObjective.CUSTOM + ":"))) {
            return DungeonObjective.CUSTOM;
        }
        return DungeonObjective.NONE;
    }

    private static int normalizeTargetFloor(String objectiveType, int targetFloor) {
        if (DungeonObjective.REACH_TOP.equals(objectiveType)) return DungeonObjective.TOP_FLOOR;
        return clampTargetFloor(targetFloor);
    }

    private static int clampTargetFloor(int value) {
        if (value <= 0) return 0;
        return Math.min(DungeonObjective.TOP_FLOOR, value);
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

    private static String limitText(String value, int max) {
        String text = safe(value);
        if (text.length() <= max) return text;
        return text.substring(0, max).trim();
    }

    private static final class BrainPayload {
        final JSONObject plan;
        final String publicSummary;

        BrainPayload(JSONObject plan, String publicSummary) {
            this.plan = plan == null ? new JSONObject() : plan;
            this.publicSummary = publicSummary == null ? "" : publicSummary.trim();
        }
    }
}
