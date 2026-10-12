package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * P1-01 current-main re-audit gate.
 *
 * This test intentionally re-evaluates the current source against the P0 canonical specification
 * instead of treating historical v2.1.x CI as conformance evidence.
 */
public class P1CanonicalKernelReauditTest {
    @Test
    public void canonicalWorldHasSingleWriterAndOneNpcAggregate() throws Exception {
        String kernel = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        String aggregate = read("src/main/java/com/sktpj/npcbrain/CanonicalNpcStateV210.java");
        String runtime = read("src/main/java/com/sktpj/npcbrain/WorldRuntimeV040.java");

        assertTrue(kernel.contains("WorldCommitResultV210 commit("));
        assertTrue(kernel.contains("WorldCommandV210.APPLY_BRAIN_DECISION"));
        assertTrue(kernel.contains("WorldCommandV210.SET_DUNGEON_PRESENCE"));
        assertTrue(kernel.contains("database.upsertNpc"));

        assertTrue(aggregate.contains("\"npc_id\""));
        assertTrue(aggregate.contains("\"state_version\""));
        assertTrue(aggregate.contains("\"life_state\""));
        assertTrue(aggregate.contains("\"dynamic_state\""));
        assertTrue(aggregate.contains("\"inner_life\""));
        assertTrue(aggregate.contains("\"intention\""));
        assertTrue(aggregate.contains("\"relationships\""));
        assertTrue(aggregate.contains("\"dungeon_present\""));
        assertTrue(aggregate.contains("\"dungeon_actor\""));

        assertTrue(runtime.contains("WorldKernelV210"));
        assertTrue(runtime.contains("WorldQueryServiceV210"));
        assertFalse(runtime.contains("new WorldClock"));
        assertFalse(runtime.contains("new WorldStateStore"));
    }

    @Test
    public void oneBrainOwnerUsesFrozenCanonicalSnapshotAndSameNpcIdentity() throws Exception {
        String coordinator = read("src/main/java/com/sktpj/npcbrain/NpcBrainCoordinator.java");
        String scope = read("src/main/java/com/sktpj/npcbrain/BrainContextScopeV210.java");
        String character = read("src/main/java/com/sktpj/npcbrain/CharacterStateStore.java");
        String factory = read("src/main/java/com/sktpj/npcbrain/NpcBrainSessionFactory.java");
        String demoRuntime = read("src/main/java/com/sktpj/npcbrain/DemoRuntimeV032.java");
        String dungeonAutonomy = read("src/main/java/com/sktpj/npcbrain/CanonicalDungeonAutonomyV211.java");

        assertTrue(coordinator.contains("sessionFactory.worldSnapshot(request.npcId)"));
        assertTrue(coordinator.contains("long basisRevision = frozen.optLong(\"revision\""));
        assertTrue(coordinator.contains("long basisStateVersion"));
        assertTrue(coordinator.contains("BrainContextScopeV210.enter(request.npcId, frozen)"));
        assertTrue(coordinator.contains("engine.thinkDecision("));
        assertTrue(scope.contains("ThreadLocal<FrozenContext>"));
        assertTrue(scope.contains("frozenNpc(String npcId)"));
        assertTrue(scope.contains("basisRevision(String npcId)"));
        assertTrue(character.contains("BrainContextScopeV210.frozenNpc(npcId)"));
        assertTrue(factory.contains("WorldQueryServiceV210"));
        assertFalse(factory.contains("NpcWorldStateCoordinator"));

        assertTrue(demoRuntime.contains("NpcBrainCoordinator brainCoordinator"));
        assertTrue(demoRuntime.contains("brainCoordinator.request("));
        assertTrue(dungeonAutonomy.contains("NpcBrainCoordinator"));
        assertFalse(dungeonAutonomy.contains("NpcBrainSessionFactory"));
    }

    @Test
    public void staleBrainResultsCannotOverwriteNewerCanonicalNpcState() throws Exception {
        String character = read("src/main/java/com/sktpj/npcbrain/CharacterStateStore.java");
        String kernel = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");

        assertTrue(character.contains("brainBasisStateVersion"));
        assertTrue(character.contains("basis_state_version"));
        assertTrue(character.contains("basis_revision"));
        assertTrue(character.contains("WorldCommandV210.APPLY_BRAIN_DECISION"));
        assertTrue(character.contains("stale_brain_result"));

        assertTrue(kernel.contains("basis_state_version"));
        assertTrue(kernel.contains("basis_revision"));
        assertTrue(kernel.contains("stale_brain_result"));
        assertTrue(kernel.contains("current.stateVersion() != basisStateVersion"));
    }

    @Test
    public void oneSimulationOwnerAndUiObserversDoNotOwnWorldProgress() throws Exception {
        String runtime = read("src/main/java/com/sktpj/npcbrain/NpcWorldRuntimeV200.java");
        String dungeonUi = read("src/main/java/com/sktpj/npcbrain/DungeonActivity.java");

        assertTrue(runtime.contains("WorldSimulationDriverV210 driver"));
        assertFalse(runtime.contains("WorldRuntimeV040 lifeRuntime"));
        assertFalse(runtime.contains("DungeonWorldProgressRuntime dungeonProgress"));
        assertFalse(runtime.contains("DungeonAutonomyRuntime dungeonAutonomy"));

        assertTrue(dungeonUi.contains("WorldQueryServiceV210"));
        assertFalse(dungeonUi.contains("DungeonEngine"));
        assertFalse(dungeonUi.contains("advanceTurn"));
        assertFalse(dungeonUi.contains("WorldKernelV210.commit"));
    }

    @Test
    public void legacyStoresAreProjectionOrCompatibilitySurfacesNotActiveCanonicalWriters() throws Exception {
        String conversation = read("src/main/java/com/sktpj/npcbrain/ConversationStore.java");
        String memory = read("src/main/java/com/sktpj/npcbrain/MemoryStore.java");
        String projector = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        String importer = read("src/main/java/com/sktpj/npcbrain/LegacyWorldImporterV210.java");

        assertTrue(conversation.contains("WorldConversationGatewayV210"));
        assertTrue(conversation.contains("WorldProjectionScopeV210.active"));
        assertTrue(memory.contains("WorldCommandV210.APPEND_WORLD_EVENT"));
        assertTrue(memory.contains("WorldProjectionScopeV210.active"));
        assertTrue(projector.contains("WorldProjectionScopeV210.enter"));
        assertTrue(importer.contains("migration"));
    }

    @Test
    public void diagnosticsExposeRevisionEventAndProjectionLagForP1Gate() throws Exception {
        String query = read("src/main/java/com/sktpj/npcbrain/WorldQueryServiceV210.java");
        String database = read("src/main/java/com/sktpj/npcbrain/WorldDatabaseV210.java");

        assertTrue(query.contains("diagnostics()"));
        assertTrue(query.contains("next_event_sequence"));
        assertTrue(query.contains("projection_checkpoint"));
        assertTrue(query.contains("projection_lag"));
        assertTrue(database.contains("META_REVISION"));
        assertTrue(database.contains("META_WORLD_TIME"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
