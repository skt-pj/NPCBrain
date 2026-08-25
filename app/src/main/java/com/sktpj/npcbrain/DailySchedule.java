package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class DailySchedule {
    private final NpcId npcId;
    private final ScheduleSlot[] slots;

    DailySchedule(NpcId npcId, ScheduleSlot... slots) {
        this.npcId = npcId;
        this.slots = slots == null ? new ScheduleSlot[0] : slots.clone();
    }

    static DailySchedule defaultFor(NpcId npcId) {
        if (NpcId.NPC2.equals(npcId)) {
            return schoolSchedule(npcId);
        }
        return workSchedule(npcId, "work");
    }

    static DailySchedule profileFor(NpcId npcId, String age, String occupation) {
        String job = normalized(occupation);
        if (containsAny(job, "冒険者", "adventurer")) {
            return adventurerSchedule(npcId);
        }
        if (containsAny(job, "学生", "生徒", "student") || isChildAge(age)) {
            return schoolSchedule(npcId);
        }
        if (isUnsetOccupation(job)) {
            return neutralSchedule(npcId);
        }
        return workSchedule(npcId, occupation == null ? "work" : occupation.trim());
    }

    static DailySchedule fromJson(NpcId npcId, JSONObject json) {
        if (npcId == null || json == null) return null;
        String encodedNpcId = json.optString("npc_id", "").trim();
        if (!encodedNpcId.isEmpty() && !npcId.value().equals(encodedNpcId)) return null;
        JSONArray entries = json.optJSONArray("entries");
        if (entries == null || entries.length() == 0) return null;
        ScheduleSlot[] parsed = new ScheduleSlot[entries.length()];
        for (int i = 0; i < entries.length(); i++) {
            parsed[i] = ScheduleSlot.fromJson(entries.optJSONObject(i));
            if (parsed[i] == null) return null;
        }
        DailySchedule schedule = new DailySchedule(npcId, parsed);
        return schedule.isValid() ? schedule : null;
    }

    DailySchedule replaceSlot(ScheduleSlot replacement) {
        if (replacement == null) throw new IllegalArgumentException("replacement is required");
        ScheduleSlot[] updated = slots.clone();
        boolean found = false;
        for (int i = 0; i < updated.length; i++) {
            ScheduleSlot current = updated[i];
            if (current != null && current.entryId().equals(replacement.entryId())) {
                updated[i] = replacement;
                found = true;
                break;
            }
        }
        if (!found) throw new IllegalArgumentException("Unknown schedule entry: " + replacement.entryId());
        DailySchedule schedule = new DailySchedule(npcId, updated);
        if (!schedule.isValid()) {
            throw new IllegalArgumentException("Replacement breaks full-day schedule coverage");
        }
        return schedule;
    }

    boolean isValid() {
        if (slots.length == 0) return false;
        int expectedStart = 0;
        Set<String> ids = new HashSet<>();
        for (ScheduleSlot slot : slots) {
            if (slot == null) return false;
            if (!ids.add(slot.entryId())) return false;
            if (slot.startMinute() != expectedStart) return false;
            if (slot.endMinute() <= slot.startMinute()) return false;
            expectedStart = slot.endMinute();
        }
        return expectedStart == 1440;
    }

    ScheduleSlot slotAt(long worldTimeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(worldTimeMs);
        int minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        return slotAtMinute(minute);
    }

    ScheduleSlot slotAtMinute(int minuteOfDay) {
        int minute = Math.max(0, Math.min(1439, minuteOfDay));
        for (ScheduleSlot slot : slots) {
            if (slot != null && slot.containsMinute(minute)) return slot;
        }
        return slot("fallback", 0, 1440, "free_time", "unknown", "", "unscheduled time");
    }

    long slotStartTimeMs(long worldTimeMs, ScheduleSlot slot) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(worldTimeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis() + slot.startMinute() * 60_000L;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        JSONArray entries = new JSONArray();
        for (ScheduleSlot slot : slots) {
            if (slot != null) entries.put(slot.toJson());
        }
        try {
            json.put("npc_id", npcId.value());
            json.put("timezone", Calendar.getInstance().getTimeZone().getID());
            json.put("entries", entries);
        } catch (Exception ignored) {
        }
        return json;
    }

    private static DailySchedule schoolSchedule(NpcId npcId) {
        return new DailySchedule(
                npcId,
                slot("sleep_1", 0, 420, "sleep", "home", "rest", "sleeping"),
                slot("wake", 420, 450, "wake", "home", "start_day", "morning routine"),
                slot("breakfast", 450, 480, "meal", "home", "eat_breakfast", "breakfast"),
                slot("commute_out", 480, 540, "move", "in_transit", "go_to_school", "commuting"),
                slot("school_am", 540, 720, "school", "school", "attend_school", "classes and study"),
                slot("lunch", 720, 780, "meal", "school", "eat_lunch", "lunch break"),
                slot("school_pm", 780, 960, "school", "school", "attend_school", "classes and study"),
                slot("commute_home", 960, 1020, "move", "in_transit", "go_home", "commuting"),
                slot("free_evening", 1020, 1140, "free_time", "home", "personal_time", "free time"),
                slot("dinner", 1140, 1200, "meal", "home", "eat_dinner", "dinner"),
                slot("planned_evening", 1200, 1380, "planned_activity", "home", "personal_plan", "planned evening activity"),
                slot("sleep_2", 1380, 1440, "sleep", "home", "rest", "sleeping")
        );
    }

    private static DailySchedule workSchedule(NpcId npcId, String occupation) {
        String context = normalized(occupation).isEmpty() ? "working" : occupation.trim() + " duties";
        return new DailySchedule(
                npcId,
                slot("sleep_1", 0, 390, "sleep", "home", "rest", "sleeping"),
                slot("wake", 390, 420, "wake", "home", "start_day", "morning routine"),
                slot("breakfast", 420, 450, "meal", "home", "eat_breakfast", "breakfast"),
                slot("commute_out", 450, 510, "move", "in_transit", "go_to_work", "commuting"),
                slot("work_am", 510, 720, "work", "workplace", "work", context),
                slot("lunch", 720, 780, "meal", "workplace", "eat_lunch", "lunch break"),
                slot("work_pm", 780, 1050, "work", "workplace", "work", context),
                slot("commute_home", 1050, 1110, "move", "in_transit", "go_home", "commuting"),
                slot("dinner", 1110, 1170, "meal", "home", "eat_dinner", "dinner"),
                slot("free_evening", 1170, 1380, "free_time", "home", "personal_time", "free time"),
                slot("sleep_2", 1380, 1440, "sleep", "home", "rest", "sleeping")
        );
    }

    private static DailySchedule adventurerSchedule(NpcId npcId) {
        return new DailySchedule(
                npcId,
                slot("sleep_1", 0, 390, "sleep", "home", "rest", "sleeping"),
                slot("wake", 390, 420, "wake", "home", "start_day", "morning routine"),
                slot("breakfast", 420, 450, "meal", "home", "eat_breakfast", "breakfast"),
                slot("prepare", 450, 510, "planned_activity", "home", "prepare_adventure", "equipment and route preparation"),
                slot("adventure_am", 510, 720, "adventure", "adventure_area", "adventure", "adventurer activity"),
                slot("lunch", 720, 780, "meal", "adventure_area", "eat_lunch", "lunch break"),
                slot("adventure_pm", 780, 1050, "adventure", "adventure_area", "adventure", "adventurer activity"),
                slot("return_home", 1050, 1110, "move", "in_transit", "go_home", "returning from adventure"),
                slot("dinner", 1110, 1170, "meal", "home", "eat_dinner", "dinner"),
                slot("free_evening", 1170, 1380, "free_time", "home", "personal_time", "free time"),
                slot("sleep_2", 1380, 1440, "sleep", "home", "rest", "sleeping")
        );
    }

    private static DailySchedule neutralSchedule(NpcId npcId) {
        return new DailySchedule(
                npcId,
                slot("sleep_1", 0, 420, "sleep", "home", "rest", "sleeping"),
                slot("wake", 420, 450, "wake", "home", "start_day", "morning routine"),
                slot("breakfast", 450, 480, "meal", "home", "eat_breakfast", "breakfast"),
                slot("morning", 480, 720, "planned_activity", "home", "personal_plan", "profile-neutral morning activity"),
                slot("lunch", 720, 780, "meal", "home", "eat_lunch", "lunch"),
                slot("afternoon", 780, 1080, "planned_activity", "home", "personal_plan", "profile-neutral afternoon activity"),
                slot("free_evening", 1080, 1140, "free_time", "home", "personal_time", "free time"),
                slot("dinner", 1140, 1200, "meal", "home", "eat_dinner", "dinner"),
                slot("planned_evening", 1200, 1380, "planned_activity", "home", "personal_plan", "planned evening activity"),
                slot("sleep_2", 1380, 1440, "sleep", "home", "rest", "sleeping")
        );
    }

    private static boolean isChildAge(String age) {
        String value = normalized(age);
        if (containsAny(value, "子ども", "こども", "子供", "児童", "小学生", "中学生", "高校生", "child")) {
            return true;
        }
        int parsed = firstNumber(value);
        return parsed >= 0 && parsed < 18;
    }

    private static boolean isUnsetOccupation(String occupation) {
        return occupation.isEmpty() || containsAny(
                occupation,
                "未設定",
                "不明",
                "無職",
                "なし",
                "none",
                "unemployed",
                "retired",
                "退職",
                "隠居"
        );
    }

    private static int firstNumber(String value) {
        int number = -1;
        int digits = 0;
        for (int i = 0; i < value.length() && digits < 3; i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                if (number < 0) number = 0;
                number = number * 10 + (c - '0');
                digits++;
            } else if (digits > 0) {
                break;
            }
        }
        return number;
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) return false;
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && value.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static ScheduleSlot slot(
            String id,
            int startMinute,
            int endMinute,
            String activity,
            String location,
            String goal,
            String context
    ) {
        return new ScheduleSlot(id, startMinute, endMinute, activity, location, goal, context);
    }
}
