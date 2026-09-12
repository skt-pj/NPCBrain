package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Prevents the UI from drifting back into independent legacy screens. */
public class UnifiedWorldUiV212SourceTest {
    @Test public void launcherIsTheUnifiedWorldShell() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        int shell = manifest.indexOf("android:name=\".WorldShellActivityV212\"");
        int main = manifest.indexOf("android.intent.action.MAIN", shell);
        int launcher = manifest.indexOf("android.intent.category.LAUNCHER", shell);
        assertTrue(shell >= 0 && main > shell && launcher > shell);
        int legacy = manifest.indexOf("android:name=\".DemoActivityV032\"");
        int nextActivity = manifest.indexOf("<activity", legacy + 10);
        String legacyBlock = nextActivity > legacy
                ? manifest.substring(legacy, nextActivity)
                : manifest.substring(legacy);
        assertFalse(legacyBlock.contains("android.intent.category.LAUNCHER"));
    }

    @Test public void allPrimaryTabsShareWorldChromeAndFocusedNpc() throws Exception {
        String shell = read("src/main/java/com/sktpj/npcbrain/WorldShellActivityV212.java");
        String focus = read("src/main/java/com/sktpj/npcbrain/WorldFocusStoreV212.java");
        assertTrue(shell.contains("NPCBRAIN · ONE WORLD"));
        assertTrue(shell.contains("WORLD REV"));
        assertTrue(shell.contains("focusStore.focusedNpcId"));
        assertTrue(shell.contains("CONVERSATION(\"会話\")"));
        assertTrue(shell.contains("STATUS(\"NPC状況\")"));
        assertTrue(shell.contains("DUNGEON(\"ダンジョン\")"));
        assertTrue(shell.contains("CODEX(\"図鑑\")"));
        assertTrue(shell.contains("SETTINGS(\"設定\")"));
        assertTrue(focus.contains("UI-only observation focus"));
    }

    @Test public void primaryShellAndEightNpcDungeonAreObserversOnly() throws Exception {
        String shell = read("src/main/java/com/sktpj/npcbrain/WorldShellActivityV212.java");
        String monitor = read("src/main/java/com/sktpj/npcbrain/IndividualDungeonActivity.java");
        assertTrue(shell.contains("WorldQueryServiceV210"));
        assertTrue(monitor.contains("WorldQueryServiceV210"));
        assertFalse(shell.contains("DungeonStore"));
        assertFalse(shell.contains("DungeonEngine"));
        assertFalse(shell.contains("DungeonGenerator"));
        assertFalse(monitor.contains("DungeonPresenceStore"));
        assertFalse(monitor.contains("DungeonRosterStore"));
        assertFalse(monitor.contains("DungeonStore"));
        assertFalse(monitor.contains("DungeonEngine"));
    }

    @Test public void peerConversationsAreActuallyVisibleNotOnlyStored() throws Exception {
        String bridge = read("src/main/java/com/sktpj/npcbrain/WorldShellPeerConversationBridgeV212.java");
        String app = read("src/main/java/com/sktpj/npcbrain/NPCBrainApplication.java");
        assertTrue(bridge.contains("NpcPeerRoomPolicy.roomId"));
        assertTrue(bridge.contains("conversations.messageCount(peerRoom)"));
        assertTrue(bridge.contains("NPC同士の会話 · 観測のみ"));
        assertTrue(bridge.contains("あなたはこの会話の参加者ではありません"));
        assertTrue(bridge.contains("removePlaceholderPeerSection"));
        assertTrue(app.contains("WorldShellPeerConversationBridgeV212.install"));
    }

    @Test public void liveRefreshDoesNotRebuildChatOrSettingsOnEveryWorldTick() throws Exception {
        String coordinator = read("src/main/java/com/sktpj/npcbrain/WorldShellRefreshCoordinatorV212.java");
        String app = read("src/main/java/com/sktpj/npcbrain/NPCBrainApplication.java");
        assertTrue(coordinator.contains("removeLegacyRefresh"));
        assertTrue(coordinator.contains("conversationFingerprint"));
        assertTrue(coordinator.contains("conversations.messageCount(room)"));
        assertTrue(coordinator.contains("Settings is user-driven"));
        assertTrue(app.contains("WorldShellRefreshCoordinatorV212.onResumed"));
        assertTrue(app.contains("WorldShellRefreshCoordinatorV212.onPaused"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
