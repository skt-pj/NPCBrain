package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.WeakHashMap;

/** Adds read-only NPC-to-NPC conversations to the Chat tab without making the user a participant. */
final class NpcPeerConversationUiBridge {
    private static final String SECTION_TAG = "npcbrain_peer_conversation_section_v210";
    private static final long REFRESH_MS = 900L;
    private static final WeakHashMap<DemoActivityV032, Boolean> INSTALLED = new WeakHashMap<>();

    private NpcPeerConversationUiBridge() {}

    static synchronized void install(DemoActivityV032 activity) {
        if (activity == null || activity.isFinishing() || INSTALLED.containsKey(activity)) return;
        INSTALLED.put(activity, true);
        Handler handler = new Handler(Looper.getMainLooper());
        WeakReference<DemoActivityV032> ref = new WeakReference<>(activity);
        Runnable task = new Runnable() {
            @Override public void run() {
                DemoActivityV032 target = ref.get();
                if (target == null || target.isFinishing() || target.isDestroyed()) return;
                renderIfRoomList(target);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        handler.post(task);
    }

    private static void renderIfRoomList(DemoActivityV032 activity) {
        if (stringField(activity, "currentRoomId") != null) return;
        LinearLayout screen = field(activity, "screenContainer", LinearLayout.class);
        if (screen == null) return;
        ScrollView scroll = findScrollView(screen);
        if (scroll == null || scroll.getChildCount() == 0) return;
        View child = scroll.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout rooms = (LinearLayout) child;
        View existing = rooms.findViewWithTag(SECTION_TAG);
        if (existing != null) rooms.removeView(existing);

        NpcRegistryStore registry = new NpcRegistryStore(activity);
        List<String> ids = registry.activeNpcIds();
        ConversationStore conversations = new ConversationStore(activity);
        LinearLayout section = new LinearLayout(activity);
        section.setTag(SECTION_TAG);
        section.setOrientation(LinearLayout.VERTICAL);
        boolean hasConversation = false;

        TextView heading = new TextView(activity);
        heading.setText("NPC同士の会話（観測）");
        heading.setTextSize(13f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(dp(activity, 4), dp(activity, 16), dp(activity, 4), dp(activity, 8));
        section.addView(heading);

        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                String first = ids.get(i);
                String second = ids.get(j);
                String roomId = NpcPeerRoomPolicy.roomId(first, second);
                if (roomId.isEmpty() || conversations.messageCount(roomId) == 0) continue;
                hasConversation = true;
                Button row = new Button(activity);
                String firstName = displayName(activity, first);
                String secondName = displayName(activity, second);
                JSONObject last = conversations.lastMessage(roomId);
                String preview = last == null ? "" : last.optString("text", "").trim();
                if (preview.length() > 52) preview = preview.substring(0, 52) + "…";
                row.setAllCaps(false);
                row.setText(firstName + " ↔ " + secondName + (preview.isEmpty() ? "" : "\n" + preview));
                row.setContentDescription(firstName + "と" + secondName + "のNPC同士の会話を見る");
                row.setOnClickListener(v -> showPeerConversation(
                        activity, roomId, firstName, secondName, conversations));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(activity, 6);
                section.addView(row, params);
            }
        }

        if (!hasConversation) {
            TextView empty = new TextView(activity);
            empty.setText("まだNPC同士の自発会話はありません");
            empty.setTextSize(12f);
            empty.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 10));
            section.addView(empty);
        }
        rooms.addView(section);
    }

    private static void showPeerConversation(
            DemoActivityV032 activity,
            String roomId,
            String firstName,
            String secondName,
            ConversationStore conversations
    ) {
        JSONArray messages = conversations.messages(roomId);
        StringBuilder transcript = new StringBuilder();
        int start = Math.max(0, messages.length() - 60);
        for (int i = start; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null || ConversationStore.isDebugDecisionSender(
                    message.optString("sender_id", ""))) continue;
            if (transcript.length() > 0) transcript.append("\n\n");
            transcript.append(message.optString("sender_name", message.optString("sender_id", "NPC")))
                    .append("\n")
                    .append(message.optString("text", ""));
        }
        TextView text = new TextView(activity);
        text.setText(transcript.length() == 0 ? "表示できる会話はありません。" : transcript.toString());
        text.setTextSize(15f);
        text.setPadding(dp(activity, 20), dp(activity, 14), dp(activity, 20), dp(activity, 20));
        text.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity)
                .setTitle(firstName + " ↔ " + secondName)
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private static ScrollView findScrollView(View root) {
        if (root instanceof ScrollView) return (ScrollView) root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView found = findScrollView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static String displayName(DemoActivityV032 activity, String npcId) {
        String value = new CharacterStateStore(NpcContexts.storage(activity, npcId)).displayName();
        return value == null || value.trim().isEmpty() || "NPC".equals(value.trim())
                ? npcId.toUpperCase(java.util.Locale.US)
                : value.trim();
    }

    private static String stringField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            if (value == null) return null;
            String text = value.toString().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int dp(DemoActivityV032 activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
