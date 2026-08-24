package com.sktpj.npcbrain;

import java.util.Locale;

final class NpcInnerLifePolicy {
    static final long HEARTBEAT_MS = 60_000L;
    static final long LOCAL_STREAM_INTERVAL_MS = 15L * 60L * 1000L;
    static final long MIN_AMBIENT_INTERVAL_MS = 45L * 60L * 1000L;
    static final long MAX_AMBIENT_INTERVAL_MS = 90L * 60L * 1000L;
    static final long REFLECTION_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    static final int REFLECTION_THOUGHT_COUNT = 8;

    static final class AdvanceResult {
        final NpcInnerLifeState state;
        final boolean appendLocalThought;

        AdvanceResult(NpcInnerLifeState state, boolean appendLocalThought) {
            this.state = state;
            this.appendLocalThought = appendLocalThought;
        }
    }

    private NpcInnerLifePolicy() {
    }

    static AdvanceResult advance(
            NpcInnerLifeState current,
            long nowMs,
            String activity,
            String goal,
            double extraversion,
            double neuroticism,
            double conscientiousness,
            double openness,
            double valence,
            double stress
    ) {
        long now = Math.max(0L, nowMs);
        NpcInnerLifeState base = current == null
                ? NpcInnerLifeState.initial(now, extraversion, neuroticism, openness)
                : current;

        long elapsedMs = Math.max(0L, now - base.updatedAtMs);
        double hours = Math.min(12.0, elapsedMs / 3_600_000.0);
        String a = normalize(activity);

        boolean sleeping = containsAny(a, "sleep", "bed", "睡眠", "就寝");
        boolean resting = sleeping || containsAny(a, "rest", "break", "relax", "休憩", "休む");
        boolean eating = containsAny(a, "eat", "meal", "breakfast", "lunch", "dinner", "食事", "朝食", "昼食", "夕食");
        boolean social = containsAny(a, "chat", "talk", "social", "friend", "会話", "雑談", "友人", "交流");
        boolean demanding = containsAny(a, "work", "study", "school", "travel", "commute", "仕事", "勉強", "学校", "通勤", "移動");
        boolean leisure = containsAny(a, "leisure", "game", "hobby", "walk", "free", "趣味", "散歩", "自由", "娯楽");
        boolean danger = containsAny(a, "combat", "battle", "danger", "dungeon", "戦闘", "危険", "ダンジョン");

        double energy = base.energy;
        if (sleeping) energy += 0.18 * hours;
        else if (resting) energy += 0.065 * hours;
        else energy -= (demanding ? 0.055 : 0.035) * hours;

        double hunger = base.hunger + 0.055 * hours;
        if (eating) hunger -= 0.32 * Math.max(0.25, hours);

        double socialNeed = base.socialNeed
                + (0.020 + 0.018 * clamp01(extraversion)) * hours;
        if (social) socialNeed -= 0.24 * Math.max(0.25, hours);

        double boredom = base.boredom
                + (0.020 + 0.020 * (1.0 - clamp01(conscientiousness))) * hours;
        if (leisure || social) boredom -= 0.14 * hours;
        if (demanding && clamp01(openness) > 0.65) boredom -= 0.02 * hours;

        double curiosityTarget = 0.24 + 0.62 * clamp01(openness);
        double curiosity = base.curiosity
                + (curiosityTarget - base.curiosity) * Math.min(1.0, hours * 0.10)
                + clamp01(boredom) * 0.025 * hours;

        double safetyTarget = 0.08
                + 0.24 * clamp01(neuroticism)
                + 0.38 * clamp01(stress);
        if (danger) safetyTarget = Math.max(safetyTarget, 0.78);
        double safetyConcern = base.safetyConcern
                + (safetyTarget - base.safetyConcern) * Math.min(1.0, hours * 0.30);

        energy = clamp01(energy);
        hunger = clamp01(hunger);
        socialNeed = clamp01(socialNeed);
        boredom = clamp01(boredom);
        curiosity = clamp01(curiosity);
        safetyConcern = clamp01(safetyConcern);

        String focus = focusFor(energy, hunger, socialNeed, boredom, curiosity, safetyConcern, goal, activity);
        String mood = moodFor(energy, valence, stress, safetyConcern);
        String intention = intentionFor(focus, activity, goal);

        boolean firstStream = base.lastStreamAtMs <= 0L;
        boolean focusChanged = !safe(base.focus).equals(safe(focus));
        boolean streamDue = firstStream
                || focusChanged
                || now - base.lastStreamAtMs >= LOCAL_STREAM_INTERVAL_MS;

        NpcInnerLifeState updated = base.withLocalState(
                now,
                energy,
                hunger,
                socialNeed,
                boredom,
                curiosity,
                safetyConcern,
                mood,
                focus,
                intention,
                streamDue
        );
        return new AdvanceResult(updated, streamDue);
    }

    static long ambientIntervalMs(
            double extraversion,
            double neuroticism,
            double openness
    ) {
        double drive = 0.40 * clamp01(openness)
                + 0.35 * clamp01(extraversion)
                + 0.25 * clamp01(neuroticism);
        long span = MAX_AMBIENT_INTERVAL_MS - MIN_AMBIENT_INTERVAL_MS;
        long interval = MAX_AMBIENT_INTERVAL_MS - Math.round(span * drive);
        return Math.max(MIN_AMBIENT_INTERVAL_MS, Math.min(MAX_AMBIENT_INTERVAL_MS, interval));
    }

    static boolean isAmbientDue(
            NpcInnerLifeState state,
            long nowMs,
            double extraversion,
            double neuroticism,
            double openness
    ) {
        if (state == null) return false;
        long now = Math.max(0L, nowMs);
        long interval = ambientIntervalMs(extraversion, neuroticism, openness);
        return now >= state.lastAmbientAtMs
                && now - state.lastAmbientAtMs >= interval;
    }

    static boolean reflectionDue(NpcInnerLifeState state, long nowMs) {
        if (state == null) return false;
        if (state.aiThoughtCount >= REFLECTION_THOUGHT_COUNT) return true;
        long anchor = state.lastReflectionAtMs > 0L
                ? state.lastReflectionAtMs
                : state.initializedAtMs;
        long now = Math.max(anchor, nowMs);
        return now - anchor >= REFLECTION_INTERVAL_MS;
    }

    static String localThought(NpcInnerLifeState state, String activity, String goal) {
        if (state == null) return "今の状態を確かめている。";
        String focus = safe(state.focus);
        if ("休息".equals(focus)) return "少し疲れている。無理をせず休みたい。";
        if ("食事".equals(focus)) return "空腹が気になってきた。何か食べたい。";
        if ("安全".equals(focus)) return "今は安全を優先したい。周囲に少し警戒している。";
        if ("誰かとのつながり".equals(focus)) return "少し誰かと話したい気分になっている。";
        if ("刺激".equals(focus)) return "少し退屈してきた。何か変化がほしい。";
        if ("新しいこと".equals(focus)) return "何か新しいことが気になっている。";
        if (goal != null && !goal.trim().isEmpty()) {
            return "今は「" + limit(goal.trim(), 64) + "」を気にしている。";
        }
        String a = activity == null ? "" : activity.trim();
        if (!a.isEmpty()) return "今は" + limit(a, 64) + "をしながら考えを巡らせている。";
        return "特に急ぐことはなく、ぼんやり今のことを考えている。";
    }

    static String compactNeeds(NpcInnerLifeState state) {
        if (state == null) return "";
        return "ENERGY " + percent(state.energy)
                + "% · HUNGER " + percent(state.hunger)
                + "% · SOCIAL " + percent(state.socialNeed)
                + "% · BOREDOM " + percent(state.boredom)
                + "% · CURIOSITY " + percent(state.curiosity)
                + "% · SAFETY " + percent(state.safetyConcern) + "%";
    }

    private static String focusFor(
            double energy,
            double hunger,
            double socialNeed,
            double boredom,
            double curiosity,
            double safetyConcern,
            String goal,
            String activity
    ) {
        if (energy < 0.28) return "休息";
        if (hunger > 0.72) return "食事";
        if (safetyConcern > 0.62) return "安全";
        if (socialNeed > 0.68) return "誰かとのつながり";
        if (boredom > 0.66) return "刺激";
        if (curiosity > 0.76) return "新しいこと";
        if (goal != null && !goal.trim().isEmpty()) return limit(goal.trim(), 90);
        if (activity != null && !activity.trim().isEmpty()) return limit(activity.trim(), 90);
        return "今していること";
    }

    private static String intentionFor(String focus, String activity, String goal) {
        if ("休息".equals(focus)) return "区切りがついたら休む";
        if ("食事".equals(focus)) return "食事の機会を探す";
        if ("安全".equals(focus)) return "無理をせず安全を確かめる";
        if ("誰かとのつながり".equals(focus)) return "話しかける理由があれば誰かと話す";
        if ("刺激".equals(focus)) return "気分を変えられることを探す";
        if ("新しいこと".equals(focus)) return "気になるものを少し調べる";
        if (goal != null && !goal.trim().isEmpty()) return limit(goal.trim(), 100);
        if (activity != null && !activity.trim().isEmpty()) return limit(activity.trim(), 100) + "を続ける";
        return "今の流れを続ける";
    }

    private static String moodFor(
            double energy,
            double valence,
            double stress,
            double safetyConcern
    ) {
        if (stress > 0.72 || safetyConcern > 0.72) return "緊張している";
        if (energy < 0.26) return "かなり疲れている";
        if (valence < -0.45) return "少し沈んでいる";
        if (valence > 0.50 && stress < 0.40) return "気分がいい";
        if (stress > 0.48) return "少し落ち着かない";
        return "落ち着いている";
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static int percent(double value) {
        return (int) Math.round(clamp01(value) * 100.0);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
