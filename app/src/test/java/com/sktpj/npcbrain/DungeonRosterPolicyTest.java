package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DungeonRosterPolicyTest {
    @Test
    public void normalizeKeepsRegisteredUniqueAndAtMostThree() {
        List<String> result = DungeonRosterPolicy.normalize(
                Arrays.asList("npc3", "npc3", "npc9", "npc2", "npc1", "npc4"),
                Arrays.asList("npc1", "npc2", "npc3", "npc4"));
        assertEquals(Arrays.asList("npc3", "npc2", "npc1"), result);
    }

    @Test
    public void initialUsesFirstThreeActiveRegistryNpcs() {
        assertEquals(
                Arrays.asList("npc1", "npc2", "npc3"),
                DungeonRosterPolicy.initial(Arrays.asList("npc1", "npc2", "npc3", "npc4")));
    }

    @Test
    public void toggleDoesNotSilentlyDropExistingMemberAtLimit() {
        List<String> full = Arrays.asList("npc1", "npc2", "npc3");
        assertEquals(
                full,
                DungeonRosterPolicy.toggle(
                        full,
                        "npc4",
                        Arrays.asList("npc1", "npc2", "npc3", "npc4")));
    }

    @Test
    public void toggleCanRemoveAndThenAddAnotherMember() {
        List<String> registry = Arrays.asList("npc1", "npc2", "npc3", "npc4");
        List<String> removed = DungeonRosterPolicy.toggle(
                Arrays.asList("npc1", "npc2", "npc3"), "npc2", registry);
        assertEquals(Arrays.asList("npc1", "npc3"), removed);
        assertEquals(
                Arrays.asList("npc1", "npc3", "npc4"),
                DungeonRosterPolicy.toggle(removed, "npc4", registry));
    }
}
