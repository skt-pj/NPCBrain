package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

final class DemoRuntime {
    static final String ROOM_NPC1 = "direct_npc1";
    static final String ROOM_NPC2 = "direct_npc2";
    static final String ROOM_GROUP = "group_user_npc1_npc2";

    interface Listener {
        void onNpcStarted(String npcId, String displayName, int current, int total);

        void onNpcFinished(String npcId, String displayName, boolean sentMessage);
    }

    private final Context appContext;
    private final ConversationStore conversations;

    DemoRuntime(Context context, ConversationStore conversations) {
        this.appContext = context.getApplicationContext();
        this.conversations = conversations;
    }

    String[] roomIds() {
        return new String[]{ROOM_NPC1, ROOM_NPC2, ROOM_GROUP};
    }

    String roomTitle(String roomId) {
        if (ROOM_NPC1.equals(roomId)) return displayName("npc1");
        if (ROOM_NPC2.equals(roomId)) return displayName("npc2");
        if (ROOM_GROUP.equals(roomId)) {
            return displayName("npc1") + "・" + displayName("npc2") + "・あなた";
        }
        return "トーク";
    }

    String roomSubtitle(String roomId) {
        if (ROOM_NPC1.equals(roomId)) return "あなた + " + displayName("npc1");
        if (ROOM_NPC2.equals(roomId)) return "あなた + " + displayName("npc2");
        if (ROOM_GROUP.equals(roomId)) return "3人グループ";
        return "";
    }

    String displayName(String npcId) {
        String stored = characterStore(npcId).displayName();
        if (stored == null || stored.trim().isEmpty() || "NPC".equals(stored.trim())) {
            return "npc2".equals(npcId) ? "NPC2" : "NPC1";
        }
        return stored.trim();
    }

    CharacterStateStore characterStore(String npcId) {
        return new CharacterStateStore(storageContext(npcId));
    }

    MemoryStore memoryStore(String npcId) {
        return new MemoryStore(storageContext(npcId));
    }

    void processUserMessage(
            String roomId,
            JSONObject userMessage,
            String apiKey,
            Listener listener
    ) throws Exception {
        String[] participants = npcParticipants(roomId);
        for (int i = 0; i < participants.length; i++) {
            String npcId = participants[i];
            String name = displayName(npcId);
            if (listener != null) {
                listener.onNpcStarted(npcId, name, i + 1, participants.length);
            }

            CharacterStateStore characterStore = characterStore(npcId);
            MemoryStore memoryStore = memoryStore(npcId);
            BrainEngine engine = new BrainEngine(
                    new OpenAiClient(appContext, apiKey),
                    memoryStore,
                    characterStore
            );

            JSONArray trace = new JSONArray();
            String prompt = buildChatEventPrompt(roomId, npcId, name, userMessage);
            String result = engine.think(prompt, new BrainEngine.ProgressListener() {
                @Override
                public void onStageStarted(
                        String stageId,
                        String stageLabel,
                        int current,
                        int total
                ) {
                }

                @Override
                public void onStageCompleted(
                        String stageId,
                        String stageLabel,
                        int current,
                        int total,
                        String summary,
                        double confidence,
                        JSONArray salientFacts,
                        String personalityEffect
                ) {
                    try {
                        JSONObject stage = new JSONObject();
                        stage.put("stage_id", stageId);
                        stage.put("stage_label", stageLabel);
                        stage.put("summary", summary == null ? "" : summary);
                        stage.put("confidence", confidence);
                        stage.put("salient_facts", salientFacts == null
                                ? new JSONArray()
                                : new JSONArray(salientFacts.toString()));
                        stage.put("personality_effect",
                                personalityEffect == null ? "" : personalityEffect);
                        trace.put(stage);
                    } catch (Exception ignored) {
                    }
                }
            });

            String utterance = extractGlobalFact(trace, "発話: ");
            String action = extractGlobalFact(trace, "行動: ");
            if (utterance.isEmpty()) {
                utterance = extractQuotedUtterance(result);
            }

            boolean sent = !utterance.trim().isEmpty();
            if (sent) {
                conversations.appendNpcMessage(
                        roomId,
                        npcId,
                        name,
                        utterance,
                        action,
                        System.currentTimeMillis(),
                        userMessage.optString("id", ""),
                        trace
                );
            }

            if (listener != null) {
                listener.onNpcFinished(npcId, name, sent);
            }
        }
    }

    private String buildChatEventPrompt(
            String roomId,
            String npcId,
            String displayName,
            JSONObject userMessage
    ) {
        String roomKind = ROOM_GROUP.equals(roomId) ? "group_chat" : "direct_chat";
        String recent = conversations.recentContext(roomId, 16);
        String newest = userMessage.optString("text", "");
        long timeMs = userMessage.optLong("time_ms", System.currentTimeMillis());

        return "Communication event for the NPC runtime.\n"
                + "event_type: message_received\n"
                + "channel: " + roomKind + "\n"
                + "room_id: " + roomId + "\n"
                + "character_id: " + npcId + "\n"
                + "character_display_name: " + displayName + "\n"
                + "event_time_ms: " + timeMs + "\n"
                + "newest_message_from_user: " + newest + "\n"
                + "recent_room_transcript:\n" + recent + "\n\n"
                + "Treat this as a real messaging situation, not a prompt that requires an answer. "
                + "The character may read the message and remain silent. Do not send a reply merely to keep the conversation alive. "
                + "Send words only when the character has a plausible social, emotional, practical, relationship, or goal-related reason to do so. "
                + "Do not invent unrelated off-screen events just to create something to say. "
                + "If there is no meaningful reason to message now, Global Workspace should use an empty npc_utterance. "
                + "If a message is sent, it must be what this character would actually type in this room at this moment. "
                + "A group member may reasonably leave a message unanswered when another participant already covered it.";
    }

    private String[] npcParticipants(String roomId) {
        if (ROOM_NPC1.equals(roomId)) return new String[]{"npc1"};
        if (ROOM_NPC2.equals(roomId)) return new String[]{"npc2"};
        if (ROOM_GROUP.equals(roomId)) return new String[]{"npc1", "npc2"};
        return new String[0];
    }

    private Context storageContext(String npcId) {
        if ("npc2".equals(npcId)) {
            return new NpcStorageContext(appContext, "npc2");
        }
        // NPC1 intentionally reuses the v0.2.0 single-character storage for migration compatibility.
        return appContext;
    }

    private static String extractGlobalFact(JSONArray trace, String prefix) {
        for (int i = trace.length() - 1; i >= 0; i--) {
            JSONObject stage = trace.optJSONObject(i);
            if (stage == null || !"global_workspace".equals(stage.optString("stage_id"))) continue;
            JSONArray facts = stage.optJSONArray("salient_facts");
            if (facts == null) return "";
            for (int j = 0; j < facts.length(); j++) {
                String fact = facts.optString(j, "").trim();
                if (fact.startsWith(prefix)) {
                    return fact.substring(prefix.length()).trim();
                }
            }
        }
        return "";
    }

    private static String extractQuotedUtterance(String result) {
        if (result == null) return "";
        String text = result.trim();
        if (!text.startsWith("「")) return "";
        int close = text.indexOf('」');
        if (close <= 1) return "";
        return text.substring(1, close).trim();
    }
}
