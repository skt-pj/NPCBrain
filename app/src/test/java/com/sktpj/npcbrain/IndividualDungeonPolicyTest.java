package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class IndividualDungeonPolicyTest {
    @Test
    public void monitorShowsAtMostEightUniqueActors() {
        List<String> ids = IndividualDungeonPolicy.visibleNpcIds(Arrays.asList(
                "npc1", "npc2", "npc3", "npc4", "npc5",
                "npc6", "npc7", "npc8", "npc9", "npc1"));
        assertEquals(Arrays.asList(
                "npc1", "npc2", "npc3", "npc4",
                "npc5", "npc6", "npc7", "npc8"), ids);
    }

    @Test
    public void emptyPresenceProducesNoOccupiedSlots() {
        assertEquals(Collections.emptyList(),
                IndividualDungeonPolicy.visibleNpcIds(Collections.emptyList()));
    }
}
