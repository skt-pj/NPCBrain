package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class SpontaneousMessagePolicy {
    static final int MAX_GROUP_NPC_MESSAGES = 4;

    private SpontaneousMessagePolicy() {
    }

    static boolean isTriggerEvent(String eventType, String actorId) {
        return "activity_started".equals(safe(eventType)) && isNpc(actorId);
    }

    static String[] allowedTargets(String actorId, List<String> activeNpcIds) {
        String actor = normalizedNpc(actorId);
        if (actor.isEmpty()) return new String[0];
        List<String> active = normalizedActive(activeNpcIds);
        if (!active.contains(actor)) return new String[0];

        List<String> targets = new ArrayList<>();
        targets.add("user");
        for (String npcId : active) {
            if (!actor.equals(npcId)) targets.add(npcId);
        }
        if (targets.size() > 1) targets.add("group");
        return targets.toArray(new String[0]);
    }

    static boolean isAllowedTarget(String actorId, String targetId, List<String> activeNpcIds) {
        String target = safe(targetId);
        for (String allowed : allowedTargets(actorId, activeNpcIds)) {
            if (allowed.equals(target)) return true;
        }
        return false;
    }

    static String routeRoom(String actorId, String targetId, List<String> activeNpcIds) {
        String actor = normalizedNpc(actorId);
        String target = safe(targetId);
        if (!isAllowedTarget(actor, target, activeNpcIds)) return "";
        if ("user".equals(target)) return "direct_" + actor;
        return DemoRuntimeV032.ROOM_GROUP;
    }

    static String firstRecipient(String actorId, String targetId, List<String> activeNpcIds) {
        String actor = normalizedNpc(actorId);
        String target = normalizedNpc(targetId);
        List<String> active = normalizedActive(activeNpcIds);
        if (!actor.isEmpty()
                && !target.isEmpty()
                && !actor.equals(target)
                && active.contains(actor)
                && active.contains(target)) {
            return target;
        }
        return nextNpc(actor, active);
    }

    static String nextNpc(String npcId, List<String> activeNpcIds) {
        String current = normalizedNpc(npcId);
        List<String> active = normalizedActive(activeNpcIds);
        if (current.isEmpty() || active.size() < 2 || !active.contains(current)) return "";
        int start = active.indexOf(current);
        for (int offset = 1; offset < active.size(); offset++) {
            String candidate = active.get((start + offset) % active.size());
            if (!current.equals(candidate)) return candidate;
        }
        return "";
    }

    static boolean isDeferredDue(long nextEligibleTimeMs, long nowMs) {
        return nextEligibleTimeMs > 0L && nextEligibleTimeMs <= nowMs;
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

    // v0.4.21 compatibility helpers retained for old unit tests/callers while runtime uses registry-driven overloads.
    static String[] allowedTargets(String actorId) {
        return allowedTargets(actorId, Arrays.asList("npc1", "npc2"));
    }

    static boolean isAllowedTarget(String actorId, String targetId) {
        return isAllowedTarget(actorId, targetId, Arrays.asList("npc1", "npc2"));
    }

    static String routeRoom(String actorId, String targetId) {
        return routeRoom(actorId, targetId, Arrays.asList("npc1", "npc2"));
    }

    static String otherNpc(String npcId) {
        return nextNpc(npcId, Arrays.asList("npc1", "npc2"));
    }

    private static List<String> normalizedActive(List<String> activeNpcIds) {
        if (activeNpcIds == null || activeNpcIds.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String raw : activeNpcIds) {
            String id = normalizedNpc(raw);
            if (!id.isEmpty() && !result.contains(id)) result.add(id);
        }
        return result;
    }

    private static boolean isNpc(String value) {
        return !normalizedNpc(value).isEmpty();
    }

    private static String normalizedNpc(String value) {
        try {
            String normalized = NpcId.of(value).value();
            return normalized.matches("npc\\d+") ? normalized : "";
        } catch (Exception ignored) {
            return "";
        }
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
