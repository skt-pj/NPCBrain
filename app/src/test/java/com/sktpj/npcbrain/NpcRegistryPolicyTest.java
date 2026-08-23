package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NpcRegistryPolicyTest {
    @Test
    public void normalizeKeepsLegacyNpcsAndAddedNpcs() {
        List<String> ids = NpcRegistryStore.normalize(Arrays.asList("npc4", "NPC3", "npc1", "npc4"));
        assertEquals(Arrays.asList("npc1", "npc2", "npc4", "npc3"), ids);
    }

    @Test
    public void nextNpcIdUsesSmallestUnusedAddedSlot() {
        assertEquals("npc3", NpcRegistryStore.nextNpcId(Arrays.asList("npc1", "npc2")));
        assertEquals("npc4", NpcRegistryStore.nextNpcId(Arrays.asList("npc1", "npc2", "npc3")));
        assertEquals("npc3", NpcRegistryStore.nextNpcId(Arrays.asList("npc1", "npc2", "npc4")));
    }

    @Test
    public void dungeonKeysPreserveLegacyAndIsolateAddedNpc() {
        assertEquals("npc1_state", DungeonStore.key("npc1"));
        assertEquals("npc2_state", DungeonStore.key("npc2"));
        assertEquals("npc3_state", DungeonStore.key("npc3"));
        assertEquals("npc3_mind", DungeonMindStore.key("npc3"));
        assertEquals("npc3_objective", DungeonObjectiveStore.key("npc3"));
        assertTrue(!DungeonStore.key("npc3").equals(DungeonStore.key("npc1")));
    }
}
