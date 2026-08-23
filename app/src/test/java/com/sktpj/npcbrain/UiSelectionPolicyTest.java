package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class UiSelectionPolicyTest {
    @Test
    public void registeredNpcKeepsPersistedSelection() {
        assertEquals("npc3", UiSelectionPolicy.resolve(
                "", "npc3", Arrays.asList("npc1", "npc2", "npc3")));
    }

    @Test
    public void invalidRegisteredNpcFallsBackToFirst() {
        assertEquals("npc1", UiSelectionPolicy.resolve(
                "", "npc9", Arrays.asList("npc1", "npc2", "npc3")));
    }

    @Test
    public void dungeonKeepsSelectedWhileStillActive() {
        assertEquals("npc2", UiSelectionPolicy.resolveDungeon(
                "", "npc2", "npc3", Arrays.asList("npc1", "npc2", "npc3")));
    }

    @Test
    public void dungeonFallsBackOnlyAfterRemoval() {
        assertEquals("npc1", UiSelectionPolicy.resolveDungeon(
                "", "npc2", "npc9", Arrays.asList("npc1", "npc3")));
    }

    @Test
    public void roomRestoresOnlyWhenStillAvailable() {
        assertEquals("direct_npc3", UiSelectionPolicy.resolveRoom(
                "", "direct_npc3", Arrays.asList("direct_npc1", "direct_npc3")));
        assertEquals("", UiSelectionPolicy.resolveRoom(
                "", "direct_npc3", Collections.singletonList("direct_npc1")));
    }
}
