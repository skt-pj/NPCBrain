package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NpcInnerLifePolicyTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    public void stateRoundTripPreservesBoundedInnerLife() {
        NpcInnerLifeState source = new NpcInnerLifeState(
                100L, 200L, 150L, 120L, 180L,
                0.7, 0.4, 0.6, 0.3, 0.8, 0.2,
                "落ち着いている", "読書", "続きを読む", 4);
        NpcInnerLifeState restored = NpcInnerLifeState.fromJson(
                source.toJson(), 999L, 0.5, 0.5, 0.5);
        assertEquals(source.initializedAtMs, restored.initializedAtMs);
        assertEquals(source.updatedAtMs, restored.updatedAtMs);
        assertEquals(source.lastAmbientAtMs, restored.lastAmbientAtMs);
        assertEquals(source.lastReflectionAtMs, restored.lastReflectionAtMs);
        assertEquals(source.lastStreamAtMs, restored.lastStreamAtMs);
        assertEquals(source.energy, restored.energy, 0.0001);
        assertEquals(source.hunger, restored.hunger, 0.0001);
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
    public void activityKeywordsDoNotChooseDifferentPsychologicalState() {
        NpcInnerLifeState base = new NpcInnerLifeState(
                0L, 0L, 0L, 0L, 0L,
                0.40, 0.30, 0.30, 0.30, 0.50, 0.10,
                "少し迷っている", "昨日の会話", "もう少し考える", 0);
        NpcInnerLifeState sleepWord = NpcInnerLifePolicy.advance(
                base, HOUR, "sleep", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        NpcInnerLifeState workWord = NpcInnerLifePolicy.advance(
                base, HOUR, "work", "", 0.5, 0.5, 0.5, 0.5, 0.0, 0.2).state;
        assertEquals(sleepWord.energy, workWord.energy, 0.0001);
        assertEquals(sleepWord.hunger, workWord.hunger, 0.0001);
        assertEquals(base.mood, sleepWord.mood);
        assertEquals(base.focus, sleepWord.focus);
        assertEquals(base.intention, sleepWord.intention);
        assertEquals(base.mood, workWord.mood);
        assertEquals(base.focus, workWord.focus);
        assertEquals(base.intention, workWord.intention);
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
        assertTrue(later.socialNeed >= 0.0 && later.socialNeed <= 1.0);
        assertTrue(later.boredom >= 0.0 && later.boredom <= 1.0);
        assertTrue(later.curiosity >= 0.0 && later.curiosity <= 1.0);
        assertTrue(later.safetyConcern >= 0.0 && later.safetyConcern <= 1.0);
        assertTrue(later.hunger >= base.hunger);
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
