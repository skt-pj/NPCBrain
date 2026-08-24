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
                DungeonObjective.REACH_TOP.equals(objectiveType) ? 0.75 : 0.50,
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

    /**
     * Offline/local plan is a neutral compiler fallback. It represents the explicit objective,
     * not an Android-side guess about courage, fear, aggression or personality.
     */
    static DungeonPlan local(
            DungeonObjective objective,
            DungeonPersonalityPolicy.Traits traits,
            DungeonState state,
            String reason
    ) {
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        boolean hasFloorGoal = goal.isActive() && goal.targetFloor > 0;
        String strategy = hasFloorGoal ? STRATEGY_ADVANCE : STRATEGY_BALANCED;
        String interpretation = goal.isActive()
                ? "明示された目的を中立ローカル実行へ変換します。"
                : "目的は設定されていません。";
        String summary = hasFloorGoal
                ? "中立ローカル計画: 既知情報だけで" + goal.targetFloor + "Fへ進む"
                : "中立ローカル計画: 既知情報だけで探索する";
        if (reason != null && !reason.trim().isEmpty()) summary += " · " + reason.trim();
        return new DungeonPlan(
                0.50,
                0.50,
                0.50,
                hasFloorGoal ? 0.75 : 0.50,
                0.50,
                0.0,
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
        return fromIntentFallback(objective, state, intent, publicSummary);
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
            return fromIntentFallback(objective, state, intent, publicSummary);
        }
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        double planConfidence = clamp01(structured.optDouble("confidence", 0.5));
        double risk = clamp01(structured.optDouble("risk_tolerance", 0.5));
        double combat = clamp01(structured.optDouble("combat_preference", 0.5));
        double explore = clamp01(structured.optDouble("exploration_preference", 0.5));
        double progress = clamp01(structured.optDouble("progress_preference", 0.5));
        double persist = clamp01(structured.optDouble("persistence", 0.5));
        String interpretation = limitText(
                structured.optString("objective_interpretation", ""), 280);
        if (interpretation.isEmpty()) {
            interpretation = goal.isCustom()
                    ? "「" + goal.rawUserText() + "」をBrainが解釈した方針です。"
                    : "Brainが現在の目的を解釈した方針です。";
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

    /**
     * Compatibility translation for an older Brain result that has an intent but no dungeon_plan.
     * The intent mode itself is authoritative; Android maps only the categorical strategy needed
     * by the executor and keeps all preference numbers neutral instead of inventing psychology.
     */
    private static DungeonPlan fromIntentFallback(
            DungeonObjective objective,
            DungeonState state,
            DungeonIntent intent,
            String publicSummary
    ) {
        DungeonObjective goal = objective == null ? DungeonObjective.none() : objective;
        DungeonIntent resolved = intent == null
                ? DungeonIntent.localFallback(state, null, "Brain plan fallback") : intent;
        String strategy;
        switch (resolved.mode) {
            case DungeonIntent.ENGAGE:
                strategy = STRATEGY_HUNT;
                break;
            case DungeonIntent.EVADE:
                strategy = STRATEGY_SURVIVE;
                break;
            case DungeonIntent.SEEK_STAIRS:
                strategy = STRATEGY_ADVANCE;
                break;
            case DungeonIntent.HOLD:
                strategy = STRATEGY_BALANCED;
                break;
            default:
                strategy = STRATEGY_EXPLORE;
                break;
        }
        String summary = safe(publicSummary);
        if (summary.isEmpty()) {
            summary = "Brain intent変換: " + DungeonIntent.modeLabel(resolved.mode);
        }
        return new DungeonPlan(
                0.50,
                0.50,
                0.50,
                0.50,
                0.50,
                resolved.confidence,
                "Brainが選んだintentをカテゴリとしてpersistent planへ変換しました。",
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
                object.optDouble("combat_preference", 0.5),
                object.optDouble("exploration_preference", 0.5),
                object.optDouble("progress_preference", 0.5),
                object.optDouble("persistence", 0.5),
                object.optDouble("confidence", 0.5),
                object.optString("interpretation", ""),
                object.optString("strategy", STRATEGY_BALANCED),
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
