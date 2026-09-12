package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** One-to-one gates for T-WK-210-001..026. */
public class CanonicalWorldTraceabilityV210Test {
    @Test public void tWk210001CanonicalSsotAndSingleWriter() throws Exception {
        String db = read("src/main/java/com/sktpj/npcbrain/WorldDatabaseV210.java");
        String kernel = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(db.contains("CREATE TABLE npc_runtime"));
        assertTrue(db.contains("CREATE TABLE world_event"));
        assertTrue(db.contains("CREATE TABLE processed_command"));
        assertTrue(kernel.contains("Sole production commit authority"));
    }

    @Test public void tWk210002AtomicRevisionAndJournal() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(source.contains("db.beginTransaction()"));
        assertTrue(source.contains("long nextRevision = currentRevision + 1L"));
        assertTrue(source.contains("database.insertEvent(db, committed)"));
        assertTrue(source.contains("db.setTransactionSuccessful()"));
    }

    @Test public void tWk210003Idempotency() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(source.contains("database.processedResult(db, command.idempotencyKey)"));
        assertTrue(source.contains("database.recordProcessed"));
    }

    @Test public void tWk210004BoundedSimulation() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldSimulationDriverV210.java");
        assertTrue(source.contains("MAX_CATCH_UP_STEPS"));
        assertTrue(source.contains("target <= cursor"));
        assertTrue(source.contains("steps < MAX_CATCH_UP_STEPS"));
    }

    @Test public void tWk210005SerializedConcurrency() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(source.contains("commitLock"));
        assertTrue(source.contains("synchronized (commitLock)"));
    }

    @Test public void tWk210006AllProductionBrainPathsUseSharedCoordinator() throws Exception {
        String demo = read("src/main/java/com/sktpj/npcbrain/DemoRuntimeV032.java");
        String social = read("src/main/java/com/sktpj/npcbrain/PeriodicNpcSocialRuntime.java");
        String dungeon = read("src/main/java/com/sktpj/npcbrain/DungeonBrainRuntime.java");
        String autonomy = read("src/main/java/com/sktpj/npcbrain/CanonicalDungeonAutonomyV211.java");
        assertTrue(demo.contains("brainCoordinator.request("));
        assertFalse(demo.contains("new BrainEngine("));
        assertTrue(social.contains("brainCoordinator.request("));
        assertTrue(dungeon.contains("brainCoordinator.request("));
        assertTrue(autonomy.contains("brain.request("));
    }

    @Test public void tWk210007StaleBrainRejected() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(source.contains("basis_state_version"));
        assertTrue(source.contains("stale_brain_result"));
    }

    @Test public void tWk210008KernelHasNoPsychologicalThresholdAuthority() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertFalse(source.contains("agreeableness"));
        assertFalse(source.contains("neuroticism"));
        assertFalse(source.contains("fear_threshold"));
    }

    @Test public void tWk210009DeterministicPeerRooms() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/NpcPeerRoomPolicy.java");
        assertTrue(source.contains("PREFIX = \"peer_\""));
        assertTrue(source.contains("a.compareTo(b) > 0"));
        assertTrue(source.contains("a.equals(b)"));
    }

    @Test public void tWk210010ConversationCommitsThroughKernel() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldConversationGatewayV210.java");
        assertTrue(source.contains("WorldCommandV210.NPC_POST_MESSAGE"));
        assertTrue(source.contains("kernel.commit"));
    }

    @Test public void tWk210011MemoryProjectionReplay() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        assertTrue(source.contains("projectMemory()"));
        assertTrue(source.contains("alreadyRemembered"));
        assertTrue(source.contains("setProjectionCheckpoint(MEMORY"));
    }

    @Test public void tWk210012RelationshipIsCanonicalAndProjected() throws Exception {
        String relationships = read("src/main/java/com/sktpj/npcbrain/SocialRelationshipStore.java");
        String projection = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        assertTrue(relationships.contains("WorldCommandV210.APPLY_RELATIONSHIP"));
        assertTrue(projection.contains("RELATIONSHIP = \"relationship_v210\""));
        assertTrue(projection.contains("projectRelationship()"));
    }

    @Test public void tWk210013DungeonUiCannotOwnSimulationOrPersistence() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/DungeonActivity.java");
        assertTrue(source.contains("WorldQueryServiceV210"));
        assertTrue(source.contains("query.snapshot(selectedNpcId)"));
        assertFalse(source.contains("DungeonStore"));
        assertFalse(source.contains("DungeonGenerator"));
        assertFalse(source.contains("DungeonEngine"));
        assertFalse(source.contains("advanceTurn"));
        assertFalse(source.contains("WorldKernelV210.commit"));
    }

    @Test public void tWk210014PartyAndSoloUseCanonicalSimulationDriver() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldSimulationDriverV210.java");
        assertTrue(source.contains("afterNeeds.dungeonPresent()"));
        assertTrue(source.contains("dungeon.reduceOneTurn"));
        assertFalse(source.contains("IndividualDungeonActivity"));
        assertFalse(source.contains("DungeonRosterBridge"));
    }

    @Test public void tWk210015ObserverAndCompatibilityReadsCannotAdvanceWorld() throws Exception {
        String query = read("src/main/java/com/sktpj/npcbrain/WorldQueryServiceV210.java");
        String compatibility = read("src/main/java/com/sktpj/npcbrain/WorldRuntimeV040.java");
        String observer = read("src/main/java/com/sktpj/npcbrain/DungeonActivity.java");
        assertFalse(query.contains("advanceTo("));
        assertFalse(compatibility.contains("new WorldClock"));
        assertFalse(compatibility.contains("new WorldStateStore"));
        assertFalse(observer.contains("advanceTo("));
        assertTrue(Files.exists(Paths.get("src/test/java/com/sktpj/npcbrain/CanonicalObserverRuntimeV211Test.java")));
    }

    @Test public void tWk210016PeerConversationProjectsToConversationAndMemory() throws Exception {
        String social = read("src/main/java/com/sktpj/npcbrain/PeriodicNpcSocialRuntime.java");
        String projection = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        String spontaneous = read("src/main/java/com/sktpj/npcbrain/SpontaneousMessagePolicy.java");
        assertTrue(social.contains("NpcPeerRoomPolicy.roomId"));
        assertTrue(spontaneous.contains("NpcPeerRoomPolicy.roomId"));
        assertTrue(projection.contains("message_posted"));
        assertTrue(projection.contains("projectMemoryEvent"));
    }

    @Test public void tWk210017ForegroundAndJobUseOneDriver() throws Exception {
        String runtime = read("src/main/java/com/sktpj/npcbrain/NpcWorldRuntimeV200.java");
        String job = read("src/main/java/com/sktpj/npcbrain/NpcSocialMemoryJobService.java");
        assertTrue(runtime.contains("driver.advanceTo(nowMs)"));
        assertTrue(job.contains("runBackgroundOpportunity"));
        assertFalse(runtime.contains("new WorldRuntimeV040"));
        assertFalse(runtime.contains("new DungeonWorldProgressRuntime"));
    }

    @Test public void tWk210018MigrationPreservesLegacyData() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/LegacyWorldImporterV210.java");
        assertTrue(source.contains("historical_conversation_projection_preserved"));
        assertTrue(source.contains("historical_memory_projection_preserved"));
        assertTrue(source.contains("source_version_code\", 84"));
    }

    @Test public void tWk210019MigrationIsTransactionalAndRetryable() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/LegacyWorldImporterV210.java");
        assertTrue(source.contains("db.beginTransaction()"));
        assertTrue(source.contains("META_MIGRATION_STATE"));
        assertTrue(source.contains("\"COMPLETED\""));
    }

    @Test public void tWk210020DiagnosticsAreReadOnlyAndCoverProjectionLag() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldQueryServiceV210.java");
        assertTrue(source.contains("relationship_projection_checkpoint"));
        assertTrue(source.contains("projection_lag"));
        assertFalse(source.toLowerCase().contains("api_key"));
    }

    @Test public void tWk210021CanonicalAndProjectionFailuresAreSeparate() throws Exception {
        String kernel = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        String projection = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        assertTrue(kernel.contains("db.endTransaction()"));
        assertTrue(projection.contains("projectionCheckpoint"));
    }

    @Test public void tWk210022ProjectionWorkIsCheckpointBounded() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        assertTrue(source.contains("BATCH = 64"));
        assertTrue(source.contains("database.eventsAfter(checkpoint, BATCH)"));
    }

    @Test public void tWk210023PeerThreadsObservableAndMessageWritesCanonical() throws Exception {
        String ui = read("src/main/java/com/sktpj/npcbrain/NpcPeerConversationUiBridge.java");
        String store = read("src/main/java/com/sktpj/npcbrain/ConversationStore.java");
        assertTrue(ui.contains("NpcPeerRoomPolicy.roomId"));
        assertTrue(store.contains("WorldConversationGatewayV210"));
    }

    @Test public void tWk210024DungeonScreenLifecycleHasNoLegacyTurnOwner() throws Exception {
        String dungeon = read("src/main/java/com/sktpj/npcbrain/DungeonActivity.java");
        String app = read("src/main/java/com/sktpj/npcbrain/NPCBrainApplication.java");
        assertFalse(dungeon.contains("turnTask"));
        assertFalse(dungeon.contains("scheduleNextTurn"));
        assertFalse(dungeon.contains("persistCurrent"));
        assertFalse(app.contains("DungeonObserverModeV210.install"));
        assertFalse(app.contains("DungeonSoloProgressBridge.install"));
    }

    @Test public void tWk210025VersionAndBuildContract() throws Exception {
        String version = read("../version.properties");
        String workflow = read("../.github/workflows/android.yml");
        assertTrue(version.contains("VERSION_NAME=2.1.1"));
        assertTrue(version.contains("VERSION_CODE=86"));
        assertTrue(workflow.contains(":app:testDebugUnitTest :app:assembleRelease :app:assembleDebug"));
        assertTrue(workflow.contains("apksigner"));
    }

    @Test public void tWk210026RegressionSuiteRemainsPresent() {
        assertTrue(Files.exists(Paths.get("src/test/java/com/sktpj/npcbrain/BrainParallelArchitectureTest.java")));
        assertTrue(Files.exists(Paths.get("src/test/java/com/sktpj/npcbrain/LocalLlmExecutionQueueTest.java")));
        assertTrue(Files.exists(Paths.get("src/test/java/com/sktpj/npcbrain/DungeonSharedWorldTest.java")));
        assertTrue(Files.exists(Paths.get("src/test/java/com/sktpj/npcbrain/HumanMemoryPolicyTest.java")));
        assertTrue(Files.exists(Paths.get("src/test/java/com/sktpj/npcbrain/CanonicalObserverRuntimeV211Test.java")));
        assertTrue(Files.exists(Paths.get("src/test/java/com/sktpj/npcbrain/SpontaneousMessagePolicyTest.java")));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
