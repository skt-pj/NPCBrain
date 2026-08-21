package com.sktpj.npcbrain;

import org.json.JSONObject;

final class ScheduleSlot {
    private final String entryId;
    private final int startMinute;
    private final int endMinute;
    private final String activity;
    private final String location;
    private final String goal;
    private final String context;

    ScheduleSlot(
            String entryId,
            int startMinute,
            int endMinute,
            String activity,
            String location,
            String goal,
            String context
    ) {
        this.entryId = safe(entryId, "slot");
        this.startMinute = clampMinute(startMinute);
        this.endMinute = Math.max(this.startMinute + 1, Math.min(1440, endMinute));
        this.activity = safe(activity, "free_time");
        this.location = safe(location, "unknown");
        this.goal = safe(goal, "");
        this.context = safe(context, "");
    }

    String entryId() {
        return entryId;
    }

    int startMinute() {
        return startMinute;
    }

    int endMinute() {
        return endMinute;
    }

    String activity() {
        return activity;
    }

    String location() {
        return location;
    }

    String goal() {
        return goal;
    }

    String context() {
        return context;
    }

    boolean containsMinute(int minuteOfDay) {
        return minuteOfDay >= startMinute && minuteOfDay < endMinute;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("entry_id", entryId);
            json.put("start_minute", startMinute);
            json.put("end_minute", endMinute);
            json.put("start_local", localTime(startMinute));
            json.put("end_local", localTime(endMinute));
            json.put("activity", activity);
            json.put("location", location);
            json.put("goal", goal);
            json.put("context", context);
            json.put("planned", true);
        } catch (Exception ignored) {
        }
        return json;
    }

    private static int clampMinute(int value) {
        return Math.max(0, Math.min(1439, value));
    }

    private static String localTime(int minute) {
        int normalized = Math.max(0, Math.min(1440, minute));
        if (normalized == 1440) return "24:00";
        int hour = normalized / 60;
        int min = normalized % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hour, min);
    }

    private static String safe(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
