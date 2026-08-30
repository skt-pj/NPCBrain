package com.sktpj.npcbrain;

import android.content.Intent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class PrimaryNavigationPolicy {
    static final String CONVERSATION = "conversation";
    static final String STATUS = "status";
    static final String DUNGEON = "dungeon";
    static final String CODEX = "codex";
    static final String SETTINGS = "settings";
    static final String MANAGER = "manager";

    private static final List<String> RELEASE_IDS = Collections.unmodifiableList(Arrays.asList(
            CONVERSATION, STATUS, DUNGEON, CODEX, SETTINGS));
    private static final List<String> DEBUG_IDS = Collections.unmodifiableList(Arrays.asList(
            CONVERSATION, STATUS, DUNGEON, CODEX, SETTINGS, MANAGER));
    private static final List<String> ALL_IDS = Collections.unmodifiableList(Arrays.asList(
            CONVERSATION, STATUS, DUNGEON, CODEX, SETTINGS, MANAGER));
    private static final List<String> LABELS = Collections.unmodifiableList(Arrays.asList(
            "会話", "NPC状況", "ダンジョン", "図鑑", "設定", "NPC管理"));

    private PrimaryNavigationPolicy() {}

    static List<String> destinationIds() {
        return destinationIds(NPCBrainApplication.isDebugBuild());
    }

    static List<String> destinationIds(boolean debugBuild) {
        return debugBuild ? DEBUG_IDS : RELEASE_IDS;
    }

    static List<String> labels() {
        return LABELS;
    }

    static String labelFor(String id) {
        int index = ALL_IDS.indexOf(id);
        return index < 0 ? "" : LABELS.get(index);
    }

    static boolean isDestination(String id) {
        return ALL_IDS.contains(id);
    }

    static int intentFlags() {
        return Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION;
    }
}
