package com.sktpj.npcbrain;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

final class DungeonParticipationChatBridge {
    private static final String PREFS = "npcbrain_dungeon_participation_observer_v1";
    private static final long REFRESH_MS = 600L;
    private static final WeakHashMap<DemoActivityV032, Boolean> INSTALLED = new WeakHashMap<>();

    private DungeonParticipationChatBridge() {
    }

    static synchronized void install(DemoActivityV032 activity) {
        if (activity == null || activity.isFinishing() || Boolean.TRUE.equals(INSTALLED.get(activity))) return;
        INSTALLED.put(activity, true);
        Handler handler = new Handler(Looper.getMainLooper());
        WeakReference<DemoActivityV032> reference = new WeakReference<>(activity);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                DemoActivityV032 target = reference.get();
                if (target == null || target.isFinishing()) return;
                View content = target.findViewById(android.R.id.content);
                if (content == null || content.getWindowToken() == null) {
                    handler.postDelayed(this, REFRESH_MS);
                    return;
                }
                process(target);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        handler.post(task);
    }

    static void process(Context context) {
        if (context == null) return;
        ConversationStore conversations = new ConversationStore(context);
        processNpc(context, conversations, "npc1", DemoRuntimeV032.ROOM_NPC1);
        processNpc(context, conversations, "npc2", DemoRuntimeV032.ROOM_NPC2);
    }

    private static void processNpc(
            Context context,
            ConversationStore conversations,
            String npcId,
            String directRoom
    ) {
        SharedPreferences checkpoint = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String initKey = npcId + "_initialized";
        String timeKey = npcId + "_last_time";
        List<Evidence> evidence = new ArrayList<>();
        collect(conversations.messages(directRoom), npcId, evidence);
        collect(conversations.messages(DemoRuntimeV032.ROOM_GROUP), npcId, evidence);
        evidence.sort(Comparator.comparingLong(item -> item.timeMs));
        long newest = evidence.isEmpty() ? 0L : evidence.get(evidence.size() - 1).timeMs;

        if (!checkpoint.getBoolean(initKey, false)) {
            checkpoint.edit().putBoolean(initKey, true).putLong(timeKey, newest).apply();
            return;
        }

        long lastTime = checkpoint.getLong(timeKey, 0L);
        DungeonParticipationStore store = DungeonParticipationStore.forNpc(context, npcId);
        DungeonParticipationState state = store.load();
        long processedThrough = lastTime;
        for (Evidence item : evidence) {
            if (item.timeMs <= lastTime) continue;
            DungeonParticipationPolicy.Candidate candidate = DungeonParticipationInference.infer(
                    state,
                    item.userText,
                    item.npcText,
                    item.brainTrace);
            state = DungeonParticipationPolicy.apply(state, candidate, item.timeMs);
            store.save(state);
            processedThrough = Math.max(processedThrough, item.timeMs);
        }
        if (processedThrough > lastTime) checkpoint.edit().putLong(timeKey, processedThrough).apply();
    }

    private static void collect(JSONArray messages, String npcId, List<Evidence> target) {
        if (messages == null) return;
        String lastUserText = "";
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String senderId = message.optString("sender_id", "");
            if ("user".equals(senderId)) {
                lastUserText = message.optString("text", "");
                continue;
            }
            if (!npcId.equals(senderId) || ConversationStore.isDebugDecisionSender(senderId)) continue;
            if (lastUserText.trim().isEmpty()) continue;
            JSONArray trace = message.optJSONArray("brain_trace");
            if (trace == null || trace.length() == 0) continue;
            target.add(new Evidence(
                    message.optLong("time_ms", 0L),
                    lastUserText,
                    message.optString("text", ""),
                    trace));
        }
    }

    private static final class Evidence {
        final long timeMs;
        final String userText;
        final String npcText;
        final JSONArray brainTrace;

        Evidence(long timeMs, String userText, String npcText, JSONArray brainTrace) {
            this.timeMs = Math.max(0L, timeMs);
            this.userText = userText == null ? "" : userText;
            this.npcText = npcText == null ? "" : npcText;
            this.brainTrace = brainTrace == null ? new JSONArray() : brainTrace;
        }
    }
}
