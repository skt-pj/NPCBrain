package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DungeonAreaNeutralitySourceTest {
    @Test
    public void applicationDoesNotInstallConsentOrParticipationChatBridges() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/NPCBrainApplication.java");
        assertFalse(source.contains("DungeonConsentBridge.install"));
        assertFalse(source.contains("DungeonParticipationChatBridge.install"));
        assertTrue(source.contains("ConversationSendQueueBridge.install"));
    }

    @Test
    public void generalCharacterSnapshotDoesNotInjectDungeonConsentContext() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/CharacterStateStore.java");
        assertFalse(source.contains("root.put(\"dungeon_participation\""));
        assertFalse(source.contains("root.put(\"dungeon_invitation_context\""));
    }

    @Test
    public void rosterRuntimeAndScreenDoNotReadParticipationState() throws Exception {
        String bridge = read("src/main/java/com/sktpj/npcbrain/DungeonRosterBridge.java");
        String activity = read("src/main/java/com/sktpj/npcbrain/DungeonRosterActivity.java");
        assertFalse(bridge.contains("DungeonParticipation"));
        assertFalse(activity.contains("DungeonParticipation"));
        assertFalse(bridge.contains("参加意思"));
        assertFalse(activity.contains("参加意思"));
        assertFalse(activity.contains("未相談"));
        assertFalse(activity.contains("迷い"));
        assertFalse(activity.contains("拒否"));
        assertFalse(activity.contains("撤回"));
    }

    @Test
    public void legacyConsentBridgeCannotPauseDungeon() throws Exception {
        String source = read("src/main/java/com/sktpj/npcbrain/DungeonConsentBridge.java");
        assertFalse(source.contains("setBooleanField"));
        assertFalse(source.contains("参加意思:"));
        assertTrue(source.contains("return false;"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
