package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public final class PrimaryNavigationPolicyTest {
    @Test
    public void destinationsAreFixedFiveInCanonicalOrder() {
        assertEquals(Arrays.asList(
                "conversation", "status", "dungeon", "codex", "manager"),
                PrimaryNavigationPolicy.destinationIds());
        assertEquals(Arrays.asList(
                "会話", "NPC状況", "ダンジョン", "図鑑", "NPC管理"),
                PrimaryNavigationPolicy.labels());
    }

    @Test
    public void everyDestinationHasStableLabel() {
        for (String id : PrimaryNavigationPolicy.destinationIds()) {
            assertTrue(PrimaryNavigationPolicy.isDestination(id));
            assertTrue(!PrimaryNavigationPolicy.labelFor(id).isEmpty());
        }
    }
}
