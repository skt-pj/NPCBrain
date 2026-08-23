package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;

final class AppUiStateStore {
    private static final String PREFS = "npcbrain_ui_state_v1";
    private static final String FOCUSED_NPC = "focused_npc_id";
    private static final String DUNGEON_NPC = "dungeon_selected_npc_id";
    private static final String ROOM = "conversation_room_id";
    private static final String CODEX_NPC = "codex_selected_npc_id";

    private final SharedPreferences prefs;

    AppUiStateStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String focusedNpcId() { return prefs.getString(FOCUSED_NPC, ""); }
    String dungeonNpcId() { return prefs.getString(DUNGEON_NPC, ""); }
    String conversationRoomId() { return prefs.getString(ROOM, ""); }
    String codexNpcId() { return prefs.getString(CODEX_NPC, ""); }

    void saveFocusedNpcId(String npcId) { put(FOCUSED_NPC, normalize(npcId)); }
    void saveDungeonNpcId(String npcId) { put(DUNGEON_NPC, normalize(npcId)); }
    void saveConversationRoomId(String roomId) { put(ROOM, roomId == null ? "" : roomId.trim()); }
    void saveCodexNpcId(String npcId) { put(CODEX_NPC, normalize(npcId)); }

    private void put(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    private static String normalize(String npcId) {
        if (npcId == null || npcId.trim().isEmpty()) return "";
        try {
            return NpcId.of(npcId).value();
        } catch (Exception ignored) {
            return "";
        }
    }
}
