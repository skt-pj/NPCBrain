package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

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

    @Test
    public void primaryDestinationSwitchReusesActivityWithoutAnimation() {
        int flags = PrimaryNavigationPolicy.intentFlags();
        assertTrue((flags & Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0);
        assertTrue((flags & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertTrue((flags & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0);
    }
}
