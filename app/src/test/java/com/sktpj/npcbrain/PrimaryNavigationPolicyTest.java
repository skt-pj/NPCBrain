package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

import java.util.Arrays;

public final class PrimaryNavigationPolicyTest {
    @Test
    public void releaseDestinationsIncludeSettingsAndHideDebugOnlyDestinations() {
        assertEquals(Arrays.asList(
                "conversation", "status", "dungeon", "codex", "settings"),
                PrimaryNavigationPolicy.destinationIds(false));
    }

    @Test
    public void debugDestinationsIncludeManagerAndQueue() {
        assertEquals(Arrays.asList(
                "conversation", "status", "dungeon", "codex", "settings", "manager", "queue"),
                PrimaryNavigationPolicy.destinationIds(true));
        assertEquals(Arrays.asList(
                "会話", "NPC状況", "ダンジョン", "図鑑", "設定", "NPC管理", "キュー"),
                PrimaryNavigationPolicy.labels());
        assertEquals("キュー", PrimaryNavigationPolicy.labelFor(PrimaryNavigationPolicy.QUEUE));
    }

    @Test
    public void everyDebugDestinationHasStableLabel() {
        for (String id : PrimaryNavigationPolicy.destinationIds(true)) {
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
