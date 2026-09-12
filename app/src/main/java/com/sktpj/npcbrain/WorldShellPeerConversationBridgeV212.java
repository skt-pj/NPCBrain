package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Makes canonical peer-room conversations visible inside the v2.1.2 conversation tab.
 *
 * Peer messages already commit through WorldConversationGatewayV210. This bridge is deliberately
 * read-only: the user can observe NPC-to-NPC dialogue but is not injected as a participant.
 */
final class WorldShellPeerConversationBridgeV212 {
    private static final String TAG = "world_shell_peer_rooms_v212";
    private static final long REFRESH_MS = 500L;
    private static final WeakHashMap<WorldShellActivityV212, Boolean> INSTALLED = new WeakHashMap<>();

    private WorldShellPeerConversationBridgeV212() {}

    static synchronized void install(WorldShellActivityV212 activity) {
        if (activity == null || activity.isFinishing() || INSTALLED.containsKey(activity)) return;
        INSTALLED.put(activity, true);
        Handler handler = new Handler(Looper.getMainLooper());
        WeakReference<WorldShellActivityV212> ref = new WeakReference<>(activity);
        Runnable task = new Runnable() {
            @Override public void run() {
                WorldShellActivityV212 target = ref.get();
                if (target == null || target.isFinishing() || target.isDestroyed()) return;
                injectIfConversationList(target);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        handler.post(task);
    }

    private static void injectIfConversationList(WorldShellActivityV212 activity) {
        Object tab = fieldValue(activity, "currentTab");
        if (tab == null || !"CONVERSATION".equals(tab.toString())) return;
        String roomId = stringField(activity, "currentRoomId");
        if (!roomId.isEmpty()) return;
        FrameLayout content = field(activity, "content", FrameLayout.class);
        if (content == null || content.getChildCount() == 0) return;
        ScrollView scroll = findScroll(content);
        if (scroll == null || scroll.getChildCount() == 0) return;
        View child = scroll.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout body = (LinearLayout) child;

        View old = body.findViewWithTag(TAG);
        if (old != null) body.removeView(old);

        NpcRegistryStore registry = new NpcRegistryStore(activity);
        ConversationStore conversations = new ConversationStore(activity);
        List<String> ids = registry.activeNpcIds();
        LinearLayout section = new LinearLayout(activity);
        section.setTag(TAG);
        section.setOrientation(LinearLayout.VERTICAL);

        TextView heading = new TextView(activity);
        heading.setText("NPC同士の会話 · 観測のみ");
        heading.setTextColor(AppUiTheme.APP_TEXT);
        heading.setTextSize(12f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, dp(activity, 12), 0, dp(activity, 6));
        section.addView(heading);

        boolean found = false;
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                String first = ids.get(i);
                String second = ids.get(j);
                String peerRoom = NpcPeerRoomPolicy.roomId(first, second);
                if (peerRoom.isEmpty() || conversations.messageCount(peerRoom) == 0) continue;
                found = true;
                String firstName = displayName(activity, first);
                String secondName = displayName(activity, second);
                JSONObject last = conversations.lastMessage(peerRoom);
                String preview = last == null ? "" : last.optString("text", "").trim();
                if (preview.length() > 70) preview = preview.substring(0, 67) + "…";

                Button row = new Button(activity);
                row.setAllCaps(false);
                row.setText(firstName + " ↔ " + secondName
                        + (preview.isEmpty() ? "" : "\n" + preview));
                row.setTextSize(11f);
                row.setTextColor(AppUiTheme.APP_TEXT);
                row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                row.setBackgroundColor(AppUiTheme.APP_SURFACE);
                row.setContentDescription(firstName + "と" + secondName + "の私的会話を観測");
                row.setOnClickListener(v -> showTranscript(
                        activity, peerRoom, firstName, secondName, conversations));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(activity, 6);
                section.addView(row, params);
            }
        }

        if (!found) {
            TextView empty = new TextView(activity);
            empty.setText("まだNPC同士の自発会話はありません");
            empty.setTextColor(AppUiTheme.APP_MUTED);
            empty.setTextSize(11f);
            empty.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 8));
            section.addView(empty);
        }
        body.addView(section);
    }

    private static void showTranscript(
            WorldShellActivityV212 activity,
            String roomId,
            String firstName,
            String secondName,
            ConversationStore conversations
    ) {
        JSONArray messages = conversations.messages(roomId);
        LinearLayout transcript = new LinearLayout(activity);
        transcript.setOrientation(LinearLayout.VERTICAL);
        transcript.setPadding(dp(activity, 18), dp(activity, 12), dp(activity, 18), dp(activity, 18));
        int start = Math.max(0, messages.length() - 80);
        for (int i = start; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null || ConversationStore.isDebugDecisionSender(
                    message.optString("sender_id", ""))) continue;
            TextView sender = new TextView(activity);
            sender.setText(message.optString("sender_name", message.optString("sender_id", "NPC")));
            sender.setTextColor(AppUiTheme.APP_MUTED);
            sender.setTextSize(10f);
            transcript.addView(sender);
            TextView text = new TextView(activity);
            text.setText(message.optString("text", ""));
            text.setTextColor(AppUiTheme.APP_TEXT);
            text.setTextSize(14f);
            text.setPadding(0, dp(activity, 2), 0, dp(activity, 10));
            transcript.addView(text);
        }
        if (transcript.getChildCount() == 0) {
            TextView empty = new TextView(activity);
            empty.setText("表示できる会話はありません");
            empty.setTextColor(AppUiTheme.APP_MUTED);
            transcript.addView(empty);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(transcript);
        new AlertDialog.Builder(activity)
                .setTitle(firstName + " ↔ " + secondName + " · 観測")
                .setMessage("あなたはこの会話の参加者ではありません。")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private static ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView found = findScroll(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static String displayName(WorldShellActivityV212 activity, String npcId) {
        String value = new CharacterStateStore(NpcContexts.storage(activity, npcId)).displayName();
        return value == null || value.trim().isEmpty() || "NPC".equals(value.trim())
                ? npcId.toUpperCase(java.util.Locale.US)
                : value.trim();
    }

    private static Object fieldValue(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringField(Object target, String name) {
        Object value = fieldValue(target, name);
        return value == null ? "" : value.toString().trim();
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        Object value = fieldValue(target, name);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static int dp(WorldShellActivityV212 activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
