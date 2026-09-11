package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Final source-level regression gates for the v2.1 single-world cutover. */
public class CanonicalWorldFinalAuditSourceTest {
    @Test
    public void deathCommandNormalizesIntoSupportedCanonicalDungeonTransaction() throws Exception {
        String command = read("src/main/java/com/sktpj/npcbrain/WorldCommandV210.java");
        String kernel = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(command.contains("MARK_NPC_DEAD.equals(normalizedType)"));
        assertTrue(command.contains("normalizedType = UPSERT_DUNGEON_STATE"));
        assertTrue(command.contains("actor.put(\"hp\", 0)"));
        assertTrue(kernel.contains("WorldCommandV210.UPSERT_DUNGEON_STATE.equals(type)"));
        assertTrue(kernel.contains("actorState.optInt(\"hp\", 1) <= 0"));
    }

    @Test
    public void allBrainSessionsUseCanonicalWorldQuery() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/NpcBrainSessionFactory.java");
        assertTrue(source.contains("WorldQueryServiceV210 worldQuery"));
        assertTrue(source.contains("worldQuery.snapshot(id)"));
        assertFalse(source.contains("NpcWorldStateCoordinator"));
    }

    @Test
    public void communicationKeepsObservedWallTimeSeparateFromWorldClock() throws Exception {
        String gateway = read("src/main/java/com/sktpj/npcbrain/WorldConversationGatewayV210.java");
        String projection = read("src/main/java/com/sktpj/npcbrain/WorldProjectionRunnerV210.java");
        String kernel = read("src/main/java/com/sktpj/npcbrain/WorldKernelV210.java");
        assertTrue(gateway.contains("observed_wall_time_ms"));
        assertTrue(projection.contains("observed_wall_time_ms"));
        assertTrue(kernel.contains("External commands never leap the simulation clock"));
        assertTrue(kernel.contains("if (advanceTime)"));
    }

    @Test
    public void canonicalLifeUsesProfileScheduleAndCanonicalDynamicState() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/CanonicalLifeReducerV210.java");
        assertTrue(source.contains("DailySchedule.profileFor"));
        assertTrue(source.contains("canonical.dynamicState()"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
