package com.sktpj.npcbrain;

final class LifeTransitionPolicy {
    private LifeTransitionPolicy() {
    }

    static String endEventType(boolean hasPreviousActivity, boolean interrupted) {
        if (!hasPreviousActivity) return "";
        return interrupted ? "activity_interrupted" : "activity_ended";
    }

    static long transitionTime(boolean interrupted, long observedTimeMs, long scheduledStartTimeMs) {
        return interrupted ? observedTimeMs : scheduledStartTimeMs;
    }

    static boolean sameState(
            String currentEntryId,
            String currentActivity,
            String currentLocation,
            ScheduleSlot next
    ) {
        return next != null
                && safe(currentEntryId).equals(next.entryId())
                && safe(currentActivity).equals(next.activity())
                && safe(currentLocation).equals(next.location());
    }

    static String primaryConversationCause(String triggerEventId, String activityEventId) {
        String trigger = safe(triggerEventId);
        return trigger.isEmpty() ? safe(activityEventId) : trigger;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
