package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DungeonAutonomyPolicyTest {
    @Test
    public void explicitRefusalAndWithdrawalAreNeverOverridden() {
        DungeonPersonalityPolicy.Traits adventurous = new DungeonPersonalityPolicy.Traits(
                95, 5, 60, 85, 95);
        DungeonParticipationState refused = new DungeonParticipationState(
                DungeonParticipationState.REFUSE, 1.0, 0.0, 1.0, "行かない", 1L);
        DungeonParticipationState withdrawn = new DungeonParticipationState(
                DungeonParticipationState.WITHDRAW, 1.0, 0.0, 1.0, "撤退する", 1L);
        assertFalse(DungeonAutonomyPolicy.shouldSelfJoin(
                refused, adventurous, true, 0, "npc1", 10L));
        assertFalse(DungeonAutonomyPolicy.shouldSelfJoin(
                withdrawn, adventurous, true, 0, "npc1", 10L));
    }

    @Test
    public void strongAdventureDispositionCanSelfInitiateWithoutUserInvite() {
        DungeonPersonalityPolicy.Traits adventurous = new DungeonPersonalityPolicy.Traits(
                90, 10, 60, 80, 90);
        assertTrue(DungeonAutonomyPolicy.shouldSelfJoin(
                DungeonParticipationState.initial(), adventurous, true, 0, "npc7", 42L));
    }

    @Test
    public void cautiousDispositionCanChooseNotToGo() {
        DungeonPersonalityPolicy.Traits cautious = new DungeonPersonalityPolicy.Traits(
                10, 90, 60, 10, 10);
        assertFalse(DungeonAutonomyPolicy.shouldSelfJoin(
                DungeonParticipationState.initial(), cautious, true, 0, "npc7", 42L));
    }

    @Test
    public void partyCapacityAndDeathRemainHardExecutionConstraints() {
        DungeonPersonalityPolicy.Traits adventurous = new DungeonPersonalityPolicy.Traits(
                95, 5, 60, 85, 95);
        assertFalse(DungeonAutonomyPolicy.shouldSelfJoin(
                DungeonParticipationState.initial(), adventurous, false, 0, "npc1", 10L));
        assertFalse(DungeonAutonomyPolicy.shouldSelfJoin(
                DungeonParticipationState.initial(), adventurous, true,
                DungeonRosterPolicy.MAX_ACTIVE, "npc1", 10L));
    }
}
