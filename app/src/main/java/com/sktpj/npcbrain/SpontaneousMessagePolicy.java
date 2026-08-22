package com.sktpj.npcbrain;

final class SpontaneousMessagePolicy {
    static final int MAX_GROUP_NPC_MESSAGES = 4;

    private SpontaneousMessagePolicy() {
    }

    static boolean isTriggerEvent(String eventType, String actorId) {
        return "activity_started".equals(safe(eventType)) && isNpc(actorId);
    }

    static String[] allowedTargets(String actorId) {
        String actor = safe(actorId);
        if ("npc1".equals(actor)) return new String[]{"user", "npc2", "group"};
        if ("npc2".equals(actor)) return new String[]{"user", "npc1", "group"};
        return new String[0];
    }

    static boolean isAllowedTarget(String actorId, String targetId) {
        String target = safe(targetId);
        for (String allowed : allowedTargets(actorId)) {
            if (allowed.equals(target)) return true;
        }
        return false;
    }

    static String routeRoom(String actorId, String targetId) {
        String actor = safe(actorId);
        String target = safe(targetId);
        if (!isAllowedTarget(actor, target)) return "";
        if ("user".equals(target)) {
            if ("npc1".equals(actor)) return DemoRuntimeV032.ROOM_NPC1;
            if ("npc2".equals(actor)) return DemoRuntimeV032.ROOM_NPC2;
            return "";
        }
        return DemoRuntimeV032.ROOM_GROUP;
    }

    static boolean isDeferredDue(long nextEligibleTimeMs, long nowMs) {
        return nextEligibleTimeMs > 0L && nextEligibleTimeMs <= nowMs;
    }

    static String otherNpc(String npcId) {
        String actor = safe(npcId);
        if ("npc1".equals(actor)) return "npc2";
        if ("npc2".equals(actor)) return "npc1";
        return "";
    }

    static boolean canContinueGroupChain(int generatedNpcMessages) {
        return generatedNpcMessages >= 0 && generatedNpcMessages < MAX_GROUP_NPC_MESSAGES;
    }

    static String initialMessageId(String sourceEventId) {
        String source = safeId(sourceEventId);
        return source.isEmpty() ? "" : "spontaneous_" + source;
    }

    static String groupTurnMessageId(String sourceEventId, int turn, String npcId) {
        String source = safeId(sourceEventId);
        String npc = safeId(npcId);
        if (source.isEmpty() || npc.isEmpty() || turn < 1) return "";
        return "spontaneous_" + source + "_turn" + turn + "_" + npc;
    }

    private static boolean isNpc(String value) {
        String normalized = safe(value);
        return "npc1".equals(normalized) || "npc2".equals(normalized);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
    }

    private static String safeId(String value) {
        String normalized = safe(value).replaceAll("[^a-z0-9_-]", "_");
        while (normalized.contains("__")) normalized = normalized.replace("__", "_");
        return normalized;
    }
}
