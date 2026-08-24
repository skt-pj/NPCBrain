package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

final class ReplyTimerToolSession implements OpenAiClient.FunctionTool {
    static final String TOOL_NAME = "schedule_reply_timer";

    private final ReplyTimerStore store;
    private final ReplyTimerBinding binding;
    private volatile ReplyTimerTask scheduledTask;

    ReplyTimerToolSession(android.content.Context context, ReplyTimerBinding binding) {
        this.store = new ReplyTimerStore(context);
        this.binding = binding;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public JSONObject definition() {
        JSONObject tool = new JSONObject();
        try {
            JSONObject properties = new JSONObject();
            properties.put("wake_at_ms", new JSONObject()
                    .put("type", "integer")
                    .put("description", "Absolute Unix epoch milliseconds when this same NPC should re-evaluate the same source message/event."));
            properties.put("reason", new JSONObject()
                    .put("type", "string")
                    .put("description", "Short public reason why later re-evaluation is better than replying now."));
            JSONObject parameters = new JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", new JSONArray().put("wake_at_ms").put("reason"))
                    .put("additionalProperties", false);
            tool.put("type", "function");
            tool.put("name", TOOL_NAME);
            tool.put("description",
                    "Schedule one future re-evaluation of the CURRENT NPC and CURRENT source message/event. "
                            + "Call this only when a grounded temporary condition makes no reply appropriate now but a later re-evaluation is genuinely intended, such as finishing the current meal, class, work task, commute, or sleep. "
                            + "Use reply_timer_grounding.now_ms and current_activity_ends_at_ms when relevant. "
                            + "Do NOT call this when the character simply does not want to reply, has nothing to say, is permanently declining, or would naturally leave the message unanswered. "
                            + "The app binds NPC, room and source; this function only chooses the future time and public reason.");
            tool.put("parameters", parameters);
            tool.put("strict", true);
        } catch (Exception ignored) {
        }
        return tool;
    }

    @Override
    public JSONObject invoke(JSONObject arguments) {
        JSONObject output = new JSONObject();
        long requested = arguments == null ? 0L : arguments.optLong("wake_at_ms", 0L);
        String reason = safe(arguments == null ? "" : arguments.optString("reason", ""), 240);
        long now = Math.max(System.currentTimeMillis(), binding == null ? 0L : binding.decisionNowMs);
        try {
            if (binding == null || !binding.isValid()) {
                return output.put("scheduled", false).put("error", "invalid_bound_source");
            }
            if (!ReplyTimerPolicy.isValidWake(now, requested)) {
                return output.put("scheduled", false)
                        .put("error", "wake_at_ms_out_of_range")
                        .put("now_ms", now)
                        .put("min_delay_ms", ReplyTimerPolicy.MIN_DELAY_MS)
                        .put("max_delay_ms", ReplyTimerPolicy.MAX_DELAY_MS);
            }
            ReplyTimerTask task = store.schedule(binding, requested, reason);
            if (task == null) {
                return output.put("scheduled", false).put("error", "schedule_failed");
            }
            scheduledTask = task;
            output.put("scheduled", true);
            output.put("wake_at_ms", task.wakeAtMs);
            output.put("reason", task.reason);
            output.put("bound_npc_id", task.npcId);
            output.put("bound_mode", task.mode);
            return output;
        } catch (Exception ignored) {
            try {
                return output.put("scheduled", false).put("error", "schedule_failed");
            } catch (Exception ignoredAgain) {
                return new JSONObject();
            }
        }
    }

    boolean scheduledThisCycle() {
        return scheduledTask != null;
    }

    ReplyTimerTask scheduledTask() {
        return scheduledTask;
    }

    private static String safe(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
