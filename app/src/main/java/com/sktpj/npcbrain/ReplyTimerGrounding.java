package com.sktpj.npcbrain;

import org.json.JSONObject;

import java.util.Calendar;

final class ReplyTimerGrounding {
    private ReplyTimerGrounding() {}

    static long currentActivityEndsAtMs(LifeState state, long nowMs) {
        if (state == null || nowMs <= 0L) return 0L;
        try {
            DailySchedule schedule = DailySchedule.fromJson(state.npcId(), state.dailySchedule());
            if (schedule == null) return 0L;
            ScheduleSlot slot = schedule.slotAt(nowMs);
            if (slot == null) return 0L;
            Calendar midnight = Calendar.getInstance();
            midnight.setTimeInMillis(nowMs);
            midnight.set(Calendar.HOUR_OF_DAY, 0);
            midnight.set(Calendar.MINUTE, 0);
            midnight.set(Calendar.SECOND, 0);
            midnight.set(Calendar.MILLISECOND, 0);
            long end = midnight.getTimeInMillis() + slot.endMinute() * 60_000L;
            return end > nowMs ? end : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    static JSONObject toJson(LifeState state, long nowMs) {
        JSONObject json = new JSONObject();
        try {
            json.put("now_ms", nowMs);
            json.put("current_activity_ends_at_ms", currentActivityEndsAtMs(state, nowMs));
            json.put("schedule_reply_timer_available", true);
        } catch (Exception ignored) {
        }
        return json;
    }
}
