package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcAiBudgetEnforcementSourceTest {
    @Test
    public void openAiClientReservesBudgetBeforeWritingRequestBytes() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/OpenAiClient.java");
        int reserve = source.indexOf("budgetStore.tryReserve");
        int send = source.indexOf("connection.getOutputStream()");
        assertTrue(reserve >= 0);
        assertTrue(send >= 0);
        assertTrue(reserve < send);
        assertTrue(source.contains("budgetStore.releaseReservation(reservation)"));
    }

    @Test
    public void reservationsAreSharedAcrossStoreInstancesAndThreads() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/NpcAiStaminaStore.java");
        assertTrue(source.contains("static final Object GLOBAL_BUDGET_LOCK"));
        assertTrue(source.contains("static final Map<String, Double> RESERVED_JPY"));
        assertTrue(source.contains("NpcAiBudgetPolicy.canReserve"));
    }

    @Test
    public void dungeonUsesCentralAttributionAndDoesNotDoubleRecordUsage() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/DungeonBrainRuntime.java");
        assertTrue(source.contains("runtimeJson.put(\"character_id\""));
        assertTrue(source.contains("null,\n                DungeonBrainRuntime::outputLimitForOrdinal"));
        assertFalse(source.contains("usage -> staminaStore.recordUsage"));
    }

    @Test
    public void npcDefaultRequestsUseBoundedOutputBeforeReservation() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/OpenAiClient.java");
        assertTrue(source.contains("NpcAiBudgetPolicy.npcDefaultMaxOutputTokens(prompt)"));
        assertTrue(source.contains("NpcAiBudgetPolicy.reservationJpy"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
