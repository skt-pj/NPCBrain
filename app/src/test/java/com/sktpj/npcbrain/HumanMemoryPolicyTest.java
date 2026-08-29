package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HumanMemoryPolicyTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;

    @Test
    public void recentMemoryKeepsDetailForSeventyTwoHours() {
        assertEquals(
                HumanMemoryPolicy.RetentionAction.KEEP_DETAIL,
                HumanMemoryPolicy.localEnvelope(
                        71L * 60L * 60L * 1000L,
                        0.0, 0.0, 0.0, 0.0, 0));
    }

    @Test
    public void oldLowSignalMemoryCanCompressThenForget() {
        assertEquals(
                HumanMemoryPolicy.RetentionAction.KEEP_GIST,
                HumanMemoryPolicy.localEnvelope(8L * DAY, 0.05, 0.0, 0.0, 0.0, 0));
        assertEquals(
                HumanMemoryPolicy.RetentionAction.FORGET,
                HumanMemoryPolicy.localEnvelope(45L * DAY, 0.01, 0.0, 0.0, 0.0, 0));
    }

    @Test
    public void salienceAndRetrievalIncreaseRetentionScore() {
        double weak = HumanMemoryPolicy.retentionScore(0.05, 0.0, 0.0, 0.0, 0, 45L * DAY);
        double reinforced = HumanMemoryPolicy.retentionScore(0.9, 0.8, 0.8, 0.8, 5, 45L * DAY);
        assertTrue(reinforced > weak);
        assertEquals(
                HumanMemoryPolicy.RetentionAction.KEEP_DETAIL,
                HumanMemoryPolicy.localEnvelope(45L * DAY, 0.9, 0.8, 0.8, 0.8, 5));
    }

    @Test
    public void llmCannotDeleteEarlierThanLocalEnvelope() {
        assertEquals(
                HumanMemoryPolicy.RetentionAction.KEEP_DETAIL,
                HumanMemoryPolicy.conservativeMerge(
                        HumanMemoryPolicy.RetentionAction.KEEP_DETAIL, "forget"));
        assertEquals(
                HumanMemoryPolicy.RetentionAction.KEEP_GIST,
                HumanMemoryPolicy.conservativeMerge(
                        HumanMemoryPolicy.RetentionAction.KEEP_GIST, "forget"));
    }

    @Test
    public void relationshipDeltasAreBounded() {
        assertEquals(0.15, HumanMemoryPolicy.clampRelationshipDelta(0.9), 0.00001);
        assertEquals(-0.15, HumanMemoryPolicy.clampRelationshipDelta(-0.9), 0.00001);
    }
}
