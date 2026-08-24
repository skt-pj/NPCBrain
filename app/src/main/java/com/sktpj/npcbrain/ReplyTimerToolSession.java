package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

final class ReplyTimerToolSession implements OpenAiClient.FunctionTool {
    static final String TOOL_NAME = "npc_runtime_decision";
    static final String OP_REPLY_TIMER = "schedule_reply_timer";
    static final String OP_DUNGEON_PARTICIPATION = "set_dungeon_participation";

    private final android.content.Context appContext;
    private final ReplyTimerStore store;
    private final ReplyTimerBinding binding;
    private volatile ReplyTimerTask scheduledTask;

    ReplyTimerToolSession(android.content.Context context, ReplyTimerBinding binding) {
        this.appContext = context.getApplicationContext();
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
            properties.put("operation", new JSONObject()
                    .put("type", "string")
                    .put("enum", new JSONArray()
                            .put(OP_REPLY_TIMER)
                            .put(OP_DUNGEON_PARTICIPATION))
                    .put("description", "Which runtime decision to record."));
            properties.put("wake_at_ms", new JSONObject()
                    .put("type", "integer")
                    .put("description", "For schedule_reply_timer: absolute Unix epoch milliseconds. Otherwise use 0."));
            properties.put("reason", new JSONObject()
                    .put("type", "string")
                    .put("description", "For schedule_reply_timer: short public defer reason. Otherwise use an empty string."));
            properties.put("participation_decision", new JSONObject()
                    .put("type", "string")
                    .put("enum", new JSONArray().put("none").put("refuse").put("hesitate").put("accept").put("withdraw"))
                    .put("description", "For set_dungeon_participation: the NPC's own integrated decision. Otherwise use none."));
            properties.put("willingness", new JSONObject()
                    .put("type", "number")
                    .put("minimum", 0.0)
                    .put("maximum", 1.0)
                    .put("description", "Descriptive current willingness, never an acceptance gate."));
            properties.put("fear", new JSONObject()
                    .put("type", "number")
                    .put("minimum", 0.0)
                    .put("maximum", 1.0)
                    .put("description", "Descriptive current fear, never an automatic retreat/withdraw gate."));
            properties.put("resolve", new JSONObject()
                    .put("type", "number")
                    .put("minimum", 0.0)
                    .put("maximum", 1.0)
                    .put("description", "Descriptive current resolve, never an acceptance gate."));
            properties.put("personal_reason", new JSONObject()
                    .put("type", "string")
                    .put("description", "Optional concise public paraphrase of the character's reason; empty is valid."));
            JSONArray required = new JSONArray()
                    .put("operation")
                    .put("wake_at_ms")
                    .put("reason")
                    .put("participation_decision")
                    .put("willingness")
                    .put("fear")
                    .put("resolve")
                    .put("personal_reason");
            JSONObject parameters = new JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required)
                    .put("additionalProperties", false);
            tool.put("type", "function");
            tool.put("name", TOOL_NAME);
            tool.put("description",
                    "Record one structured NPC runtime decision from Global Workspace. "
                            + "Use operation=set_dungeon_participation whenever the CURRENT conversational message asks, invites, negotiates, confirms, refuses, reconsiders, or withdraws this NPC's dungeon participation. "
                            + "The participation_decision is the character's integrated choice after considering personality, current state, fear, memory, relationship and grounded situation. "
                            + "Do not wait for numeric thresholds and do not require a personal_reason. Fear may coexist with accept; low fear may coexist with refuse. "
                            + "Use operation=schedule_reply_timer only when a grounded temporary condition makes no reply appropriate now but later re-evaluation is genuinely intended, such as a current meal, class, work task, commute or sleep. "
                            + "Do not schedule merely because the character does not want to reply or has nothing to say. The app binds NPC, room and source.");
            tool.put("parameters", parameters);
            tool.put("strict", true);
        } catch (Exception ignored) {
        }
        return tool;
    }

    @Override
    public JSONObject invoke(JSONObject arguments) {
        JSONObject output = new JSONObject();
        String operation = safe(arguments == null ? "" : arguments.optString("operation", ""), 80);
        try {
            if (binding == null || !binding.isValid()) {
                return output.put("ok", false).put("error", "invalid_bound_source");
            }
            if (OP_DUNGEON_PARTICIPATION.equals(operation)) {
                return recordParticipation(arguments, output);
            }
            if (OP_REPLY_TIMER.equals(operation)) {
                return scheduleReply(arguments, output);
            }
            return output.put("ok", false).put("error", "unknown_operation");
        } catch (Exception ignored) {
            try {
                return output.put("ok", false).put("error", "runtime_decision_failed");
            } catch (Exception ignoredAgain) {
                return new JSONObject();
            }
        }
    }

    private JSONObject recordParticipation(JSONObject arguments, JSONObject output) throws Exception {
        if (!ReplyTimerBinding.MODE_CONVERSATION.equals(binding.mode)) {
            return output.put("ok", false).put("error", "participation_requires_conversation");
        }
        String decision = arguments == null
                ? "" : arguments.optString("participation_decision", "").trim().toLowerCase(java.util.Locale.US);
        if (!(DungeonParticipationState.REFUSE.equals(decision)
                || DungeonParticipationState.HESITATE.equals(decision)
                || DungeonParticipationState.ACCEPT.equals(decision)
                || DungeonParticipationState.WITHDRAW.equals(decision))) {
            return output.put("ok", false).put("error", "invalid_participation_decision");
        }
        DungeonParticipationStore participationStore =
                DungeonParticipationStore.forNpc(appContext, binding.npcId);
        DungeonParticipationState before = participationStore.load();
        DungeonParticipationPolicy.Candidate candidate = new DungeonParticipationPolicy.Candidate(
                true,
                decision,
                arguments.optDouble("willingness", 0.5),
                arguments.optDouble("fear", 0.5),
                arguments.optDouble("resolve", 0.5),
                arguments.optString("personal_reason", ""));
        DungeonParticipationState after = DungeonParticipationPolicy.apply(
                before,
                candidate,
                Math.max(System.currentTimeMillis(), binding.decisionNowMs));
        participationStore.save(after);
        return output.put("ok", true)
                .put("recorded", OP_DUNGEON_PARTICIPATION)
                .put("stance", after.stance)
                .put("personal_reason_optional", true)
                .put("numeric_values_are_descriptive_only", true);
    }

    private JSONObject scheduleReply(JSONObject arguments, JSONObject output) throws Exception {
        long requested = arguments == null ? 0L : arguments.optLong("wake_at_ms", 0L);
        String reason = safe(arguments == null ? "" : arguments.optString("reason", ""), 240);
        long now = Math.max(System.currentTimeMillis(), binding.decisionNowMs);
        if (!ReplyTimerPolicy.isValidWake(now, requested)) {
            return output.put("ok", false)
                    .put("scheduled", false)
                    .put("error", "wake_at_ms_out_of_range")
                    .put("now_ms", now)
                    .put("min_delay_ms", ReplyTimerPolicy.MIN_DELAY_MS)
                    .put("max_delay_ms", ReplyTimerPolicy.MAX_DELAY_MS);
        }
        ReplyTimerTask task = store.schedule(binding, requested, reason);
        if (task == null) {
            return output.put("ok", false).put("scheduled", false).put("error", "schedule_failed");
        }
        scheduledTask = task;
        return output.put("ok", true)
                .put("scheduled", true)
                .put("wake_at_ms", task.wakeAtMs)
                .put("reason", task.reason)
                .put("bound_npc_id", task.npcId)
                .put("bound_mode", task.mode);
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
