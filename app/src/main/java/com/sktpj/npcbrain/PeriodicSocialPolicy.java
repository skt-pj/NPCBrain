package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.List;

final class PeriodicSocialPolicy {
    private PeriodicSocialPolicy() {
    }

    static long window(long nowMs) {
        return Math.max(0L, nowMs) / HumanMemoryPolicy.MAINTENANCE_INTERVAL_MS;
    }

    static String initiator(List<String> activeNpcIds, long nowMs) {
        List<String> ids = canonical(activeNpcIds);
        if (ids.size() < 2) return "";
        int index = (int) Math.floorMod(window(nowMs), (long) ids.size());
        return ids.get(index);
    }

    static List<String> peerTargets(String actorId, List<String> activeNpcIds) {
        List<String> result = new ArrayList<>();
        String actor;
        try {
            actor = NpcId.of(actorId).value();
        } catch (Exception ignored) {
            return result;
        }
        for (String id : canonical(activeNpcIds)) {
            if (!actor.equals(id)) result.add(id);
        }
        if (!result.isEmpty()) result.add("group");
        return result;
    }

    static boolean isAllowedTarget(String actorId, String targetId, List<String> activeNpcIds) {
        String target = targetId == null ? "" : targetId.trim().toLowerCase(java.util.Locale.US);
        for (String allowed : peerTargets(actorId, activeNpcIds)) {
            if (allowed.equals(target)) return true;
        }
        return false;
    }

    static String messageId(long nowMs, String actorId) {
        String actor = NpcId.of(actorId).value();
        return "periodic_social_" + window(nowMs) + "_" + actor;
    }

    static String replyMessageId(long nowMs, String actorId, String responderId) {
        return messageId(nowMs, actorId) + "_reply_" + NpcId.of(responderId).value();
    }

    static String firstResponder(String actorId, String targetId, List<String> activeNpcIds) {
        String actor = NpcId.of(actorId).value();
        String target = targetId == null ? "" : targetId.trim().toLowerCase(java.util.Locale.US);
        List<String> ids = canonical(activeNpcIds);
        if (!"group".equals(target) && ids.contains(target) && !actor.equals(target)) return target;
        int start = ids.indexOf(actor);
        if (start < 0) return "";
        for (int offset = 1; offset < ids.size(); offset++) {
            String candidate = ids.get((start + offset) % ids.size());
            if (!actor.equals(candidate)) return candidate;
        }
        return "";
    }

    private static List<String> canonical(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String raw : values) {
            try {
                String id = NpcId.of(raw).value();
                if (!result.contains(id)) result.add(id);
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
