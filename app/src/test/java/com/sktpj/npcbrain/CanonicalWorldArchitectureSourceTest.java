package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Architecture gates for the real canonical-world cutover. */
public class CanonicalWorldArchitectureSourceTest {
    @Test
    public void applicationHasOneWorldRuntimeAndInstallsNoLegacyDungeonExecutionBridge() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/NPCBrainApplication.java");
        assertTrue(source.contains("NpcWorldRuntimeV200 worldRuntime"));
        assertFalse(source.contains("NpcInnerLifeRuntime innerLifeRuntime"));
        assertFalse(source.contains("DungeonObserverModeV210.install"));
        assertFalse(source.contains("DungeonGoalInputBridge.install"));
        assertFalse(source.contains("DungeonRosterBridge.install"));
        assertFalse(source.contains("DungeonSoloProgressBridge.install"));
    }

    @Test
    public void compatibilityRuntimeDelegatesToOneSimulationDriver() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/NpcWorldRuntimeV200.java");
        assertTrue(source.contains("WorldSimulationDriverV210 driver"));
        assertFalse(source.contains("WorldRuntimeV040 lifeRuntime"));
        assertFalse(source.contains("DungeonWorldProgressRuntime dungeonProgress"));
        assertFalse(source.contains("DungeonAutonomyRuntime dungeonAutonomy"));
    }

    @Test
    public void conversationCompatibilityWorldRuntimeOwnsNoLegacyClockOrWorldStore() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldRuntimeV040.java");
        assertTrue(source.contains("WorldQueryServiceV210"));
        assertTrue(source.contains("WorldKernelV210"));
        assertFalse(source.contains("new WorldClock"));
        assertFalse(source.contains("new WorldStateStore"));
        assertFalse(source.contains("stateStore.appendEvent"));
        assertFalse(source.contains("clock.advanceTo"));
    }

    @Test
    public void dungeonActivityIsReadOnlyCanonicalObserverNotSimulationOwner() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/DungeonActivity.java");
        assertTrue(source.contains("WorldQueryServiceV210"));
        assertTrue(source.contains("query.snapshot(selectedNpcId)"));
        assertFalse(source.contains("DungeonStore"));
        assertFalse(source.contains("DungeonGenerator"));
        assertFalse(source.contains("DungeonEngine"));
        assertFalse(source.contains("advanceTurn"));
        assertFalse(source.contains("WorldKernelV210.commit"));
        assertFalse(source.contains("DungeonPresenceStore"));
    }

    @Test
    public void canonicalDungeonPathDoesNotCommitLegacyDungeonStore() throws Exception {
        String adapter = read("src/main/java/com/sktpj/npcbrain/DungeonDomainAdapterV210.java");
        String engine = read("src/main/java/com/sktpj/npcbrain/DungeonEngine.java");
        assertTrue(adapter.contains("stepDetailedCanonical"));
        int start = engine.indexOf("static DungeonStepResult stepDetailedCanonical");
        int end = engine.indexOf("static DungeonPersonalityPolicy.Direction legalBrainDirection", start);
        assertTrue(start >= 0 && end > start);
        String canonicalMethod = engine.substring(start, end);
        assertFalse(canonicalMethod.contains("refreshSharedWorldForTurn"));
        assertFalse(canonicalMethod.contains("commitSharedTurn"));
        assertFalse(canonicalMethod.contains("commitFloorTransition"));
    }

    @Test
    public void canonicalAutonomousDungeonEntryDoesNotWriteLegacyStores() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/CanonicalDungeonAutonomyV211.java");
        assertTrue(source.contains("NpcBrainCoordinator"));
        assertTrue(source.contains("WorldCommandV210.SET_DUNGEON_PRESENCE"));
        assertFalse(source.contains("new DungeonStore("));
        assertFalse(source.contains("new DungeonPresenceStore("));
        assertFalse(source.contains("DungeonStore dungeon"));
        assertFalse(source.contains("DungeonPresenceStore presence"));
        assertFalse(source.contains("NpcBrainSessionFactory"));
    }

    @Test
    public void conversationAndMemoryWritesEnterKernelBeforeProjection() throws Exception {
        String conversation = read("src/main/java/com/sktpj/npcbrain/ConversationStore.java");
        String memory = read("src/main/java/com/sktpj/npcbrain/MemoryStore.java");
        String projection = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        assertTrue(conversation.contains("WorldConversationGatewayV210"));
        assertTrue(conversation.contains("WorldProjectionScopeV210.active"));
        assertTrue(memory.contains("memory_candidate_created"));
        assertTrue(memory.contains("WorldCommandV210.APPEND_WORLD_EVENT"));
        assertTrue(projection.contains("WorldProjectionScopeV210.enter"));
    }

    @Test
    public void characterBrainStateCommitsAgainstCanonicalBasisVersion() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/CharacterStateStore.java");
        assertTrue(source.contains("brainBasisStateVersion"));
        assertTrue(source.contains("basis_state_version"));
        assertTrue(source.contains("WorldCommandV210.APPLY_BRAIN_DECISION"));
        assertTrue(source.contains("stale_brain_result"));
        assertTrue(source.contains("canonical.dynamicState"));
        assertTrue(source.contains("canonical.innerLife"));
    }

    @Test
    public void dungeonPeerDamageIsAppliedInsideKernelTransaction() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(source.contains("peer_actor_updates"));
        assertTrue(source.contains("dungeon_peer_damaged"));
        assertTrue(source.contains("database.upsertNpc(db, peerNext, nextRevision)"));
    }

    @Test
    public void simulationUsesFinalClockCommitAsBoundaryCheckpoint() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldSimulationDriverV210.java");
        int life = source.indexOf("WorldCommandV210.UPSERT_LIFE_STATE");
        int dungeon = source.indexOf("WorldCommandV210.UPSERT_DUNGEON_STATE");
        int advance = source.lastIndexOf("WorldCommandV210.ADVANCE_TIME");
        assertTrue(life >= 0 && advance > life);
        assertTrue(dungeon >= 0 && advance > dungeon);
        assertTrue(source.contains("defer_projection"));
        assertTrue(source.contains("simulation_boundary"));
    }

    @Test
    public void autonomousNpcSocialUsesPeerRoomsWithoutImplicitUser() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/PeriodicNpcSocialRuntime.java");
        assertTrue(source.contains("NpcPeerRoomPolicy.roomId"));
        assertFalse(source.contains("GROUP_ROOM"));
        assertTrue(source.contains("user is only an observer"));
    }

    @Test
    public void socialOpportunityCadenceIsNotCoupledToMemoryMaintenance() throws Exception {
        String policy = read("src/main/java/com/sktpj/npcbrain/PeriodicSocialPolicy.java");
        String scheduler = read("src/main/java/com/sktpj/npcbrain/NpcSocialMemoryScheduler.java");
        assertTrue(policy.contains("SOCIAL_OPPORTUNITY_INTERVAL_MS"));
        assertFalse(policy.contains("/ HumanMemoryPolicy.MAINTENANCE_INTERVAL_MS"));
        assertTrue(scheduler.contains("JOB_CHECK_INTERVAL_MS"));
    }

    @Test
    public void peerConversationsAreObservableFromChatTab() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/NpcPeerConversationUiBridge.java");
        String app = read("src/main/java/com/sktpj/npcbrain/NPCBrainApplication.java");
        assertTrue(source.contains("NPC同士の会話（観測）"));
        assertTrue(source.contains("NpcPeerRoomPolicy.roomId"));
        assertTrue(app.contains("NpcPeerConversationUiBridge.install"));
    }

    @Test
    public void delayedRepliesAdvanceSameCanonicalWorldFirst() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/ReplyTimerJobService.java");
        int advance = source.indexOf("runBackgroundOpportunity");
        int reply = source.indexOf("runtime.processReplyTimer");
        assertTrue(advance >= 0 && reply > advance);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
