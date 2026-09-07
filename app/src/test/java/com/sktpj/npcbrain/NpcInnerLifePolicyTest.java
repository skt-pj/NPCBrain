package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class NpcInnerLifePolicyTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    public void stateRoundTripPreservesBoundedInnerLife() {
        NpcInnerLifeState source = new NpcInnerLifeState(
                100L, 200L, 150L, 120L, 180L,
                0.7, 0.4, 0.6, 0.35, 1.10, 0.90, 0.40,
                0.6, 0.3, 0.8, 0.2,
                "落ち着いている", "読書", "続きを読む", 4);
        NpcInnerLifeState restored = NpcInnerLifeState.fromJson(
                source.toJson(), 999L, 0.5, 0.5, 0.5, "npc4");
        assertEquals(source.initializedAtMs, restored.initializedAtMs);
        assertEquals(source.updatedAtMs, restored.updatedAtMs);
        assertEquals(source.lastAmbientAtMs, restored.lastAmbientAtMs);
        assertEquals(source.lastReflectionAtMs, restored.lastReflectionAtMs);
        assertEquals(source.lastStreamAtMs, restored.lastStreamAtMs);
        assertEquals(source.energy, restored.energy, 0.0001);
        assertEquals(source.hunger, restored.hunger, 0.0001);
        assertEquals(source.sleepPressure, restored.sleepPressure, 0.0001);
        assertEquals(source.reproductiveDrive, restored.reproductiveDrive, 0.0001);
        assertEquals(source.hungerSensitivity, restored.hungerSensitivity, 0.0001);
        assertEquals(source.sleepSensitivity, restored.sleepSensitivity, 0.0001);
        assertEquals(source.reproductiveSetPoint, restored.reproductiveSetPoint, 0.0001);
        assertEquals(source.socialNeed, restored.socialNeed, 0.0001);
        assertEquals(source.boredom, restored.boredom, 0.0001);
        assertEquals(source.curiosity, restored.curiosity, 0.0001);
        assertEquals(source.safetyConcern, restored.safetyConcern, 0.0001);
        assertEquals(source.mood, restored.mood);
        assertEquals(source.focus, restored.focus);
        assertEquals(source.intention, restored.intention);
        assertEquals(source.aiThoughtCount, restored.aiThoughtCount);
    }

    @Test
    public void canonicalSleepAndMealChangeOnlyPhysiologicalSignals() {
        NpcInnerLifeState base = new NpcInnerLifeState(
                0L, 0L, 0L, 0L, 0L,
                0.40, 0.30, 0.50, 0.30, 1.0, 1.0, 0.40,
                0.30, 0.30, 0.50, 0.10,
                "少し迷っている", "昨日の会話", "もう少し考える", 0);
        NpcInnerLifeState sleep = NpcInnerLifePolicy.advance(
                base, HOUR, "sleep", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        NpcInnerLifeState awake = NpcInnerLifePolicy.advance(
                base, HOUR, "work", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        NpcInnerLifeState meal = NpcInnerLifePolicy.advance(
                base, HOUR, "meal", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        assertTrue(sleep.energy > awake.energy);
        assertTrue(sleep.sleepPressure < awake.sleepPressure);
        assertTrue(meal.hunger < awake.hunger);
        assertEquals(base.mood, sleep.mood);
        assertEquals(base.focus, sleep.focus);
        assertEquals(base.intention, sleep.intention);
        assertEquals(base.mood, meal.mood);
        assertEquals(base.focus, meal.focus);
        assertEquals(base.intention, meal.intention);
    }

    @Test
    public void arbitraryActivityTextDoesNotSatisfyPrimaryDrives() {
        NpcInnerLifeState base = state(0L, 0.40, 0.30, 0.30, 0.30, 0.50, 0.10);
        NpcInnerLifeState phrase = NpcInnerLifePolicy.advance(
                base, HOUR, "sleep meal", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        NpcInnerLifeState sleepingWord = NpcInnerLifePolicy.advance(
                base, HOUR, "sleeping", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        NpcInnerLifeState eatWord = NpcInnerLifePolicy.advance(
                base, HOUR, "eat", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        NpcInnerLifeState exactSleep = NpcInnerLifePolicy.advance(
                base, HOUR, "sleep", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        NpcInnerLifeState exactMeal = NpcInnerLifePolicy.advance(
                base, HOUR, "meal", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        assertEquals(phrase.energy, sleepingWord.energy, 0.0001);
        assertEquals(phrase.hunger, eatWord.hunger, 0.0001);
        assertTrue(exactSleep.energy > phrase.energy);
        assertTrue(exactSleep.sleepPressure < phrase.sleepPressure);
        assertTrue(exactMeal.hunger < phrase.hunger);
    }

    @Test
    public void needThresholdsDoNotReplaceAiGeneratedFocusOrIntention() {
        NpcInnerLifeState base = new NpcInnerLifeState(
                0L, 0L, 0L, 0L, 0L,
                0.05, 0.95, 0.95, 0.95, 0.90, 0.95,
                "複雑な気分", "ダンジョンに行くか考えている", "自分で決める", 0);
        NpcInnerLifeState result = NpcInnerLifePolicy.advance(
                base, 1L, "dangerous dungeon meal work sleep", "締切",
                0.5, 1.0, 0.5, 0.5, -0.8, 1.0).state;
        assertEquals("複雑な気分", result.mood);
        assertEquals("ダンジョンに行くか考えている", result.focus);
        assertEquals("自分で決める", result.intention);
    }

    @Test
    public void localSignalsRemainBoundedAndCanEvolve() {
        NpcInnerLifeState base = state(0L, 0.70, 0.70, 0.75, 0.30, 0.50, 0.10);
        NpcInnerLifeState later = NpcInnerLifePolicy.advance(
                base, 12L * HOUR, "anything", "", 1.0, 1.0, 0.0, 1.0, 0.0, 1.0).state;
        assertTrue(later.energy >= 0.0 && later.energy <= 1.0);
        assertTrue(later.hunger >= 0.0 && later.hunger <= 1.0);
        assertTrue(later.sleepPressure >= 0.0 && later.sleepPressure <= 1.0);
        assertTrue(later.reproductiveDrive >= 0.0 && later.reproductiveDrive <= 1.0);
        assertTrue(later.socialNeed >= 0.0 && later.socialNeed <= 1.0);
        assertTrue(later.boredom >= 0.0 && later.boredom <= 1.0);
        assertTrue(later.curiosity >= 0.0 && later.curiosity <= 1.0);
        assertTrue(later.safetyConcern >= 0.0 && later.safetyConcern <= 1.0);
        assertTrue(later.hunger >= base.hunger);
        assertTrue(later.sleepPressure >= base.sleepPressure);
    }

    @Test
    public void primaryDriveIndividualDifferencesAreStablePerNpc() {
        NpcInnerLifeState a1 = NpcInnerLifeState.initial(1000L, 0.5, 0.5, 0.5, "npc3");
        NpcInnerLifeState a2 = NpcInnerLifeState.initial(1000L, 0.5, 0.5, 0.5, "npc3");
        NpcInnerLifeState b = NpcInnerLifeState.initial(1000L, 0.5, 0.5, 0.5, "npc4");
        assertEquals(a1.hunger, a2.hunger, 0.0000001);
        assertEquals(a1.sleepPressure, a2.sleepPressure, 0.0000001);
        assertEquals(a1.reproductiveDrive, a2.reproductiveDrive, 0.0000001);
        assertEquals(a1.hungerSensitivity, a2.hungerSensitivity, 0.0000001);
        assertEquals(a1.sleepSensitivity, a2.sleepSensitivity, 0.0000001);
        assertTrue(Math.abs(a1.hunger - b.hunger) > 0.0000001
                || Math.abs(a1.sleepPressure - b.sleepPressure) > 0.0000001
                || Math.abs(a1.reproductiveDrive - b.reproductiveDrive) > 0.0000001
                || Math.abs(a1.hungerSensitivity - b.hungerSensitivity) > 0.0000001
                || Math.abs(a1.sleepSensitivity - b.sleepSensitivity) > 0.0000001);
    }

    @Test
    public void legacyJsonKeepsOldFieldsAndBackfillsNewSignals() throws Exception {
        JSONObject legacy = new JSONObject()
                .put("initialized_at_ms", 100L)
                .put("updated_at_ms", 200L)
                .put("last_ambient_at_ms", 150L)
                .put("last_reflection_at_ms", 120L)
                .put("last_stream_at_ms", 180L)
                .put("energy", 0.61)
                .put("hunger", 0.42)
                .put("social_need", 0.33)
                .put("boredom", 0.27)
                .put("curiosity", 0.74)
                .put("safety_concern", 0.19)
                .put("mood", "旧気分")
                .put("focus", "旧注目")
                .put("intention", "旧意図")
                .put("ai_thought_count", 5);
        NpcInnerLifeState restored = NpcInnerLifeState.fromJson(
                legacy, 999L, 0.5, 0.5, 0.5, "npc8");
        assertEquals(0.61, restored.energy, 0.0001);
        assertEquals(0.42, restored.hunger, 0.0001);
        assertEquals(0.33, restored.socialNeed, 0.0001);
        assertEquals(0.27, restored.boredom, 0.0001);
        assertEquals(0.74, restored.curiosity, 0.0001);
        assertEquals(0.19, restored.safetyConcern, 0.0001);
        assertEquals("旧気分", restored.mood);
        assertEquals("旧注目", restored.focus);
        assertEquals("旧意図", restored.intention);
        assertEquals(5, restored.aiThoughtCount);
        assertTrue(restored.sleepPressure >= 0.0 && restored.sleepPressure <= 1.0);
        assertTrue(restored.reproductiveDrive >= 0.0 && restored.reproductiveDrive <= 1.0);
        assertTrue(restored.hungerSensitivity >= 0.85 && restored.hungerSensitivity <= 1.15);
        assertTrue(restored.sleepSensitivity >= 0.85 && restored.sleepSensitivity <= 1.15);
    }

    @Test
    public void opennessRaisesInitialCuriositySignalOnly() {
        NpcInnerLifeState closed = NpcInnerLifeState.initial(1000L, 0.5, 0.5, 0.0);
        NpcInnerLifeState open = NpcInnerLifeState.initial(1000L, 0.5, 0.5, 1.0);
        assertTrue(open.curiosity > closed.curiosity);
        assertEquals(closed.focus, open.focus);
        assertEquals(closed.intention, open.intention);
    }

    @Test
    public void ambientIntervalsStayBetweenFortyFiveAndNinetyMinutes() {
        long min = NpcInnerLifePolicy.MIN_AMBIENT_INTERVAL_MS;
        long max = NpcInnerLifePolicy.MAX_AMBIENT_INTERVAL_MS;
        double[] values = {0.0, 0.25, 0.5, 0.75, 1.0};
        for (double e : values) {
            for (double n : values) {
                for (double o : values) {
                    long interval = NpcInnerLifePolicy.ambientIntervalMs(e, n, o);
                    assertTrue(interval >= min);
                    assertTrue(interval <= max);
                    assertEquals(interval, NpcInnerLifePolicy.ambientIntervalMs(e, n, o));
                }
            }
        }
    }

    @Test
    public void ambientDoesNotBecomeDueBeforeItsInterval() {
        long start = 10_000L;
        NpcInnerLifeState state = NpcInnerLifeState.initial(start, 0.5, 0.5, 0.5);
        long interval = NpcInnerLifePolicy.ambientIntervalMs(0.5, 0.5, 0.5);
        assertFalse(NpcInnerLifePolicy.isAmbientDue(
                state, start + interval - 1L, 0.5, 0.5, 0.5));
        assertTrue(NpcInnerLifePolicy.isAmbientDue(
                state, start + interval, 0.5, 0.5, 0.5));
    }

    @Test
    public void reflectionIsDueByTimeOrThoughtCount() {
        NpcInnerLifeState byTime = state(0L, 0.7, 0.3, 0.3, 0.2, 0.5, 0.1);
        assertFalse(NpcInnerLifePolicy.reflectionDue(
                byTime, NpcInnerLifePolicy.REFLECTION_INTERVAL_MS - 1L));
        assertTrue(NpcInnerLifePolicy.reflectionDue(
                byTime, NpcInnerLifePolicy.REFLECTION_INTERVAL_MS));

        NpcInnerLifeState byCount = new NpcInnerLifeState(
                1000L, 1000L, 1000L, 1000L, 1000L,
                0.7, 0.3, 0.3, 0.2, 0.5, 0.1,
                "平静", "読書", "続ける", NpcInnerLifePolicy.REFLECTION_THOUGHT_COUNT);
        assertTrue(NpcInnerLifePolicy.reflectionDue(byCount, 1000L));
        NpcInnerLifeState reflected = byCount.withAmbient(2000L, "平静", "読書", "続ける", true);
        assertEquals(0, reflected.aiThoughtCount);
        assertFalse(NpcInnerLifePolicy.reflectionDue(reflected, 2000L));
    }

    @Test
    public void clockRollbackDoesNotRewindOrMutateNeeds() {
        NpcInnerLifeState base = state(10_000L, 0.60, 0.40, 0.35, 0.30, 0.50, 0.15);
        NpcInnerLifeState result = NpcInnerLifePolicy.advance(
                base, 5_000L, "work", "goal", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        assertEquals(base.updatedAtMs, result.updatedAtMs);
        assertEquals(base.energy, result.energy, 0.0001);
        assertEquals(base.hunger, result.hunger, 0.0001);
        assertEquals(base.sleepPressure, result.sleepPressure, 0.0001);
        assertEquals(base.reproductiveDrive, result.reproductiveDrive, 0.0001);
        assertEquals(base.socialNeed, result.socialNeed, 0.0001);
    }

    @Test
    public void ambientPromptCharacterIdCanBeAttributedToDynamicNpc() {
        String prompt = "Runtime JSON:\n{\"mode\":\"ambient_inner_life\",\"character_id\":\"npc3\"}";
        assertEquals("npc3", OpenAiClient.attributedNpcId(prompt));
    }

    @Test
    public void ambientFallbackDoesNotPretendAnAiThoughtOccurred() {
        NpcInnerLifeState base = new NpcInnerLifeState(
                0L, 0L, 0L, 0L, 0L,
                0.7, 0.3, 0.3, 0.2, 0.5, 0.1,
                "平静", "読書", "続ける", 3);
        NpcInnerLifeState fallback = base.withAmbientFallback(10_000L);
        assertEquals(3, fallback.aiThoughtCount);
        assertEquals(10_000L, fallback.lastAmbientAtMs);
    }

    @Test
    public void streamBoundIsExplicit() {
        assertEquals(120, NpcInnerLifeStore.maxStreamEntries());
    }

    private static NpcInnerLifeState state(
            long updated,
            double energy,
            double hunger,
            double socialNeed,
            double boredom,
            double curiosity,
            double safetyConcern
    ) {
        return new NpcInnerLifeState(
                0L, updated, 0L, 0L, 0L,
                energy, hunger, socialNeed, boredom, curiosity, safetyConcern,
                "落ち着いている", "今していること", "続ける", 0);
    }
}
