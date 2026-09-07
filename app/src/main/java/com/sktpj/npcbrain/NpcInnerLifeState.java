package com.sktpj.npcbrain;

import org.json.JSONObject;

final class NpcInnerLifeState {
    final long initializedAtMs;
    final long updatedAtMs;
    final long lastAmbientAtMs;
    final long lastReflectionAtMs;
    final long lastStreamAtMs;
    final double energy;
    final double hunger;
    final double sleepPressure;
    final double reproductiveDrive;
    final double hungerSensitivity;
    final double sleepSensitivity;
    final double reproductiveSetPoint;
    final double socialNeed;
    final double boredom;
    final double curiosity;
    final double safetyConcern;
    final String mood;
    final String focus;
    final String intention;
    final int aiThoughtCount;

    NpcInnerLifeState(
            long initializedAtMs,
            long updatedAtMs,
            long lastAmbientAtMs,
            long lastReflectionAtMs,
            long lastStreamAtMs,
            double energy,
            double hunger,
            double socialNeed,
            double boredom,
            double curiosity,
            double safetyConcern,
            String mood,
            String focus,
            String intention,
            int aiThoughtCount
    ) {
        this(
                initializedAtMs,
                updatedAtMs,
                lastAmbientAtMs,
                lastReflectionAtMs,
                lastStreamAtMs,
                energy,
                hunger,
                0.28,
                0.375,
                1.0,
                1.0,
                0.375,
                socialNeed,
                boredom,
                curiosity,
                safetyConcern,
                mood,
                focus,
                intention,
                aiThoughtCount
        );
    }

    NpcInnerLifeState(
            long initializedAtMs,
            long updatedAtMs,
            long lastAmbientAtMs,
            long lastReflectionAtMs,
            long lastStreamAtMs,
            double energy,
            double hunger,
            double sleepPressure,
            double reproductiveDrive,
            double hungerSensitivity,
            double sleepSensitivity,
            double reproductiveSetPoint,
            double socialNeed,
            double boredom,
            double curiosity,
            double safetyConcern,
            String mood,
            String focus,
            String intention,
            int aiThoughtCount
    ) {
        this.initializedAtMs = Math.max(0L, initializedAtMs);
        this.updatedAtMs = Math.max(this.initializedAtMs, updatedAtMs);
        this.lastAmbientAtMs = Math.max(0L, lastAmbientAtMs);
        this.lastReflectionAtMs = Math.max(0L, lastReflectionAtMs);
        this.lastStreamAtMs = Math.max(0L, lastStreamAtMs);
        this.energy = clamp01(energy);
        this.hunger = clamp01(hunger);
        this.sleepPressure = clamp01(sleepPressure);
        this.reproductiveDrive = clamp01(reproductiveDrive);
        this.hungerSensitivity = clampSensitivity(hungerSensitivity);
        this.sleepSensitivity = clampSensitivity(sleepSensitivity);
        this.reproductiveSetPoint = clamp01(reproductiveSetPoint);
        this.socialNeed = clamp01(socialNeed);
        this.boredom = clamp01(boredom);
        this.curiosity = clamp01(curiosity);
        this.safetyConcern = clamp01(safetyConcern);
        this.mood = safe(mood, "落ち着いている");
        this.focus = safe(focus, "今していること");
        this.intention = safe(intention, "今の流れを続ける");
        this.aiThoughtCount = Math.max(0, aiThoughtCount);
    }

    static NpcInnerLifeState initial(
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness
    ) {
        return initial(nowMs, extraversion, neuroticism, openness, "");
    }

    static NpcInnerLifeState initial(
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness,
            String npcId
    ) {
        long now = Math.max(0L, nowMs);
        double hungerSensitivity = 0.85 + 0.30 * stableUnit(npcId, "hunger_sensitivity");
        double sleepSensitivity = 0.85 + 0.30 * stableUnit(npcId, "sleep_sensitivity");
        double reproductiveSetPoint = 0.15 + 0.45 * stableUnit(npcId, "reproductive_set_point");
        double initialHunger = 0.16 + 0.16 * stableUnit(npcId, "initial_hunger");
        double initialSleepPressure = 0.18 + 0.20 * stableUnit(npcId, "initial_sleep_pressure");
        return new NpcInnerLifeState(
                now,
                now,
                now,
                0L,
                0L,
                0.72,
                initialHunger,
                initialSleepPressure,
                reproductiveSetPoint,
                hungerSensitivity,
                sleepSensitivity,
                reproductiveSetPoint,
                0.28 + clamp01(extraversion) * 0.24,
                0.22,
                0.25 + clamp01(openness) * 0.60,
                0.08 + clamp01(neuroticism) * 0.22,
                "落ち着いている",
                "今していること",
                "今の流れを続ける",
                0
        );
    }

    static NpcInnerLifeState fromJson(
            JSONObject json,
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness
    ) {
        return fromJson(json, nowMs, extraversion, neuroticism, openness, "");
    }

    static NpcInnerLifeState fromJson(
            JSONObject json,
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness,
            String npcId
    ) {
        if (json == null || json.length() == 0) {
            return initial(nowMs, extraversion, neuroticism, openness, npcId);
        }
        NpcInnerLifeState fallback = initial(nowMs, extraversion, neuroticism, openness, npcId);
        long initialized = json.optLong("initialized_at_ms", fallback.initializedAtMs);
        long updated = json.optLong("updated_at_ms", initialized);
        return new NpcInnerLifeState(
                initialized,
                updated,
                json.optLong("last_ambient_at_ms", initialized),
                json.optLong("last_reflection_at_ms", 0L),
                json.optLong("last_stream_at_ms", 0L),
                json.optDouble("energy", fallback.energy),
                json.optDouble("hunger", fallback.hunger),
                json.optDouble("sleep_pressure", fallback.sleepPressure),
                json.optDouble("reproductive_drive", fallback.reproductiveDrive),
                json.optDouble("hunger_sensitivity", fallback.hungerSensitivity),
                json.optDouble("sleep_sensitivity", fallback.sleepSensitivity),
                json.optDouble("reproductive_set_point", fallback.reproductiveSetPoint),
                json.optDouble("social_need", fallback.socialNeed),
                json.optDouble("boredom", fallback.boredom),
                json.optDouble("curiosity", fallback.curiosity),
                json.optDouble("safety_concern", fallback.safetyConcern),
                json.optString("mood", fallback.mood),
                json.optString("focus", fallback.focus),
                json.optString("intention", fallback.intention),
                json.optInt("ai_thought_count", 0)
        );
    }

    NpcInnerLifeState withLocalState(
            long nowMs,
            double energy,
            double hunger,
            double socialNeed,
            double boredom,
            double curiosity,
            double safetyConcern,
            String mood,
            String focus,
            String intention,
            boolean streamWritten
    ) {
        return withLocalState(
                nowMs,
                energy,
                hunger,
                sleepPressure,
                reproductiveDrive,
                socialNeed,
                boredom,
                curiosity,
                safetyConcern,
                mood,
                focus,
                intention,
                streamWritten
        );
    }

    NpcInnerLifeState withLocalState(
            long nowMs,
            double energy,
            double hunger,
            double sleepPressure,
            double reproductiveDrive,
            double socialNeed,
            double boredom,
            double curiosity,
            double safetyConcern,
            String mood,
            String focus,
            String intention,
            boolean streamWritten
    ) {
        long now = Math.max(updatedAtMs, nowMs);
        return new NpcInnerLifeState(
                initializedAtMs,
                now,
                lastAmbientAtMs,
                lastReflectionAtMs,
                streamWritten ? now : lastStreamAtMs,
                energy,
                hunger,
                sleepPressure,
                reproductiveDrive,
                hungerSensitivity,
                sleepSensitivity,
                reproductiveSetPoint,
                socialNeed,
                boredom,
                curiosity,
                safetyConcern,
                mood,
                focus,
                intention,
                aiThoughtCount
        );
    }

    NpcInnerLifeState withAmbient(
            long nowMs,
            String mood,
            String focus,
            String intention,
            boolean reflected
    ) {
        long now = Math.max(updatedAtMs, nowMs);
        return new NpcInnerLifeState(
                initializedAtMs,
                now,
                now,
                reflected ? now : lastReflectionAtMs,
                now,
                energy,
                hunger,
                sleepPressure,
                reproductiveDrive,
                hungerSensitivity,
                sleepSensitivity,
                reproductiveSetPoint,
                socialNeed,
                boredom,
                curiosity,
                safetyConcern,
                mood,
                focus,
                intention,
                reflected ? 0 : aiThoughtCount + 1
        );
    }

    NpcInnerLifeState withAmbientFallback(long nowMs) {
        long now = Math.max(updatedAtMs, nowMs);
        return new NpcInnerLifeState(
                initializedAtMs,
                now,
                now,
                lastReflectionAtMs,
                now,
                energy,
                hunger,
                sleepPressure,
                reproductiveDrive,
                hungerSensitivity,
                sleepSensitivity,
                reproductiveSetPoint,
                socialNeed,
                boredom,
                curiosity,
                safetyConcern,
                mood,
                focus,
                intention,
                aiThoughtCount
        );
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("initialized_at_ms", initializedAtMs);
            json.put("updated_at_ms", updatedAtMs);
            json.put("last_ambient_at_ms", lastAmbientAtMs);
            json.put("last_reflection_at_ms", lastReflectionAtMs);
            json.put("last_stream_at_ms", lastStreamAtMs);
            json.put("energy", energy);
            json.put("hunger", hunger);
            json.put("sleep_pressure", sleepPressure);
            json.put("reproductive_drive", reproductiveDrive);
            json.put("hunger_sensitivity", hungerSensitivity);
            json.put("sleep_sensitivity", sleepSensitivity);
            json.put("reproductive_set_point", reproductiveSetPoint);
            json.put("social_need", socialNeed);
            json.put("boredom", boredom);
            json.put("curiosity", curiosity);
            json.put("safety_concern", safetyConcern);
            json.put("mood", mood);
            json.put("focus", focus);
            json.put("intention", intention);
            json.put("ai_thought_count", aiThoughtCount);
        } catch (Exception ignored) {
        }
        return json;
    }

    JSONObject snapshotForBrain() {
        JSONObject json = new JSONObject();
        try {
            json.put("energy", energy);
            json.put("hunger", hunger);
            json.put("sleep_pressure", sleepPressure);
            json.put("reproductive_drive", reproductiveDrive);
            json.put("social_need", socialNeed);
            json.put("boredom", boredom);
            json.put("curiosity", curiosity);
            json.put("safety_concern", safetyConcern);
            json.put("mood_summary", mood);
            json.put("current_focus", focus);
            json.put("tentative_intention", intention);
            json.put("updated_at_ms", updatedAtMs);
            json.put("policy",
                    "These are bounded internal-life and physiological signals, not facts about the external world. "
                            + "They may bias attention and motivation but may not override grounded evidence, hard rules, consent, "
                            + "relationships, age/profile context, or the existing Global Workspace decision. reproductive_drive is "
                            + "only an internal motivation signal and does not itself establish romantic or sexual action or consent.");
        } catch (Exception ignored) {
        }
        return json;
    }

    static double stableUnit(String npcId, String key) {
        String id = npcId == null ? "" : npcId.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty()) return 0.5;
        String salt = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        long unsigned = Integer.toUnsignedLong((id + "|" + salt).hashCode());
        return unsigned / 4294967295.0;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampSensitivity(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 1.0;
        return Math.max(0.85, Math.min(1.15, value));
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String trimmed = value.trim();
        return trimmed.length() > 180 ? trimmed.substring(0, 180) : trimmed;
    }
}
