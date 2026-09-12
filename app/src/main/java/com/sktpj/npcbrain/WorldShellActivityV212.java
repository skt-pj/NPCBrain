package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v2.1.2 primary UI.
 *
 * One Activity owns the visual shell only. Every tab observes the same canonical WorldQueryService
 * and the same UI-only NPC focus. Changing tabs never creates or advances world state.
 */
public final class WorldShellActivityV212 extends Activity {
    private static final long REFRESH_MS = 750L;

    private enum Tab {
        CONVERSATION("会話"),
        STATUS("NPC状況"),
        DUNGEON("ダンジョン"),
        CODEX("図鑑"),
        SETTINGS("設定");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refreshWorldChrome(false);
            refreshVisibleTabIfNeeded();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private WorldQueryServiceV210 query;
    private NpcRegistryStore registry;
    private NpcArchiveStore archive;
    private WorldFocusStoreV212 focusStore;
    private ConversationStore conversations;
    private DemoRuntimeV032 demoRuntime;
    private SecureApiKeyStore apiKeyStore;
    private ModelSettingsStore modelSettingsStore;

    private LinearLayout root;
    private TextView tabTitle;
    private TextView worldLine;
    private TextView npcLine;
    private LinearLayout focusSelector;
    private FrameLayout content;
    private LinearLayout nav;

    private Tab currentTab = Tab.CONVERSATION;
    private String focusedNpcId = "";
    private String currentRoomId = "";
    private boolean individualDungeonMode;
    private boolean processing;
    private TextView processingStatus;
    private long lastWorldRevision = Long.MIN_VALUE;
    private int lastRenderedMessageCount = -1;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        registry = new NpcRegistryStore(this);
        archive = new NpcArchiveStore(this);
        focusStore = new WorldFocusStoreV212(this);
        conversations = new ConversationStore(this);
        demoRuntime = new DemoRuntimeV032(this, conversations);
        apiKeyStore = new SecureApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        query = NPCBrainApplication.worldQuery();
        if (query == null) {
            WorldKernelV210 kernel = WorldKernelV210.get(getApplicationContext());
            new LegacyWorldImporterV210(getApplicationContext(), kernel.database()).importIfNeeded();
            query = new WorldQueryServiceV210(kernel.database());
        }
        focusedNpcId = focusStore.focusedNpcId(registry.activeNpcIds());
        setContentView(buildShell());
        selectTab(Tab.CONVERSATION, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        focusedNpcId = focusStore.focusedNpcId(registry.activeNpcIds());
        rebuildFocusSelector();
        refreshWorldChrome(true);
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
        maybeRunSpontaneousConversation();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (currentTab == Tab.CONVERSATION && !currentRoomId.isEmpty()) {
            currentRoomId = "";
            lastRenderedMessageCount = -1;
            hideKeyboard();
            renderCurrentTab();
            return;
        }
        if (currentTab != Tab.CONVERSATION) {
            selectTab(Tab.CONVERSATION, true);
            return;
        }
        super.onBackPressed();
    }

    private View buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUiTheme.APP_BACKGROUND);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(10), dp(8), dp(10), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(2), dp(2), dp(6));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("NPCBRAIN · ONE WORLD", 10, Color.rgb(104, 153, 205), true);
        eyebrow.setLetterSpacing(0.12f);
        heading.addView(eyebrow);
        tabTitle = text("会話", 24, AppUiTheme.APP_TEXT, true);
        heading.addView(tabTitle);
        header.addView(heading, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView live = text("● LIVE", 10, Color.rgb(105, 205, 150), true);
        live.setGravity(Gravity.CENTER);
        header.addView(live, new LinearLayout.LayoutParams(dp(64), dp(40)));
        root.addView(header);

        LinearLayout worldCard = new LinearLayout(this);
        worldCard.setOrientation(LinearLayout.VERTICAL);
        worldCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        worldCard.setBackground(cardBackground(
                Color.rgb(13, 24, 38), Color.rgb(40, 63, 87), 13));
        worldLine = text("WORLD", 10, Color.rgb(130, 166, 202), true);
        worldCard.addView(worldLine);
        npcLine = text("観測NPCなし", 12, AppUiTheme.APP_TEXT, true);
        LinearLayout.LayoutParams npcLineParams = wrap();
        npcLineParams.topMargin = dp(2);
        worldCard.addView(npcLine, npcLineParams);
        root.addView(worldCard);

        HorizontalScrollView focusScroll = new HorizontalScrollView(this);
        focusScroll.setHorizontalScrollBarEnabled(false);
        focusSelector = new LinearLayout(this);
        focusSelector.setOrientation(LinearLayout.HORIZONTAL);
        focusSelector.setPadding(0, dp(6), 0, dp(6));
        focusScroll.addView(focusSelector);
        root.addView(focusScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        View divider = new View(this);
        divider.setBackgroundColor(AppUiTheme.NAV_DIVIDER);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        root.addView(nav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(AppUiTheme.NAV_HEIGHT_DP)));
        rebuildFocusSelector();
        rebuildNav();
        return root;
    }

    private void rebuildNav() {
        if (nav == null) return;
        nav.removeAllViews();
        for (Tab tab : Tab.values()) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(tab.label);
            button.setTextSize(10);
            button.setSingleLine(true);
            button.setPadding(dp(2), 0, dp(2), 0);
            boolean selected = tab == currentTab;
            button.setTextColor(selected ? Color.WHITE : AppUiTheme.NAV_MUTED);
            button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            button.setBackgroundColor(selected ? AppUiTheme.NAV_SELECTED : AppUiTheme.NAV_BACKGROUND);
            button.setEnabled(!selected);
            button.setOnClickListener(v -> selectTab(tab, true));
            nav.addView(button, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private void rebuildFocusSelector() {
        if (focusSelector == null) return;
        focusSelector.removeAllViews();
        List<String> active = registry.activeNpcIds();
        focusedNpcId = focusStore.focusedNpcId(active);
        if (active.isEmpty()) {
            focusSelector.addView(text("生存NPCなし", 11, AppUiTheme.APP_MUTED, false));
            return;
        }
        for (String npcId : active) {
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
            String label = character.displayName();
            if (label == null || label.trim().isEmpty() || "NPC".equals(label.trim())) label = npcId;
            Button chip = new Button(this);
            chip.setAllCaps(false);
            chip.setText("観測 · " + label);
            chip.setTextSize(10);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(dp(9), 0, dp(9), 0);
            chip.setBackground(cardBackground(
                    npcId.equals(focusedNpcId) ? Color.rgb(41, 89, 151) : Color.rgb(20, 34, 50),
                    npcId.equals(focusedNpcId) ? Color.rgb(80, 137, 204) : Color.rgb(48, 68, 91),
                    11));
            chip.setOnClickListener(v -> selectFocus(npcId));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
            params.rightMargin = dp(6);
            focusSelector.addView(chip, params);
        }
    }

    private void selectFocus(String npcId) {
        focusStore.select(npcId);
        focusedNpcId = focusStore.focusedNpcId(registry.activeNpcIds());
        lastWorldRevision = Long.MIN_VALUE;
        rebuildFocusSelector();
        refreshWorldChrome(true);
        if (currentTab == Tab.STATUS || currentTab == Tab.DUNGEON) renderCurrentTab();
    }

    private void selectTab(Tab tab, boolean render) {
        if (tab == null) return;
        currentTab = tab;
        if (tabTitle != null) tabTitle.setText(tab.label);
        hideKeyboard();
        rebuildNav();
        if (render) renderCurrentTab();
    }

    private void refreshWorldChrome(boolean force) {
        if (query == null) return;
        long revision = query.revision();
        long worldTime = query.worldTimeMs();
        if (force || revision != lastWorldRevision) {
            lastWorldRevision = revision;
            worldLine.setText("WORLD REV " + revision + "  ·  " + formatWorldTime(worldTime));
            focusedNpcId = focusStore.focusedNpcId(registry.activeNpcIds());
            if (focusedNpcId.isEmpty()) {
                npcLine.setText("観測NPCなし");
            } else {
                JSONObject snapshot = query.snapshot(focusedNpcId);
                JSONObject npc = snapshot.optJSONObject("npc");
                CharacterStateStore character = new CharacterStateStore(
                        NpcContexts.storage(this, focusedNpcId));
                String name = character.displayName();
                if (npc == null) {
                    npcLine.setText(name + " · state unavailable");
                } else {
                    String location = displayValue(npc.optString("location", "unknown"));
                    String activity = displayValue(npc.optString("activity", "idle"));
                    String goal = displayValue(npc.optString("goal", ""));
                    npcLine.setText(name + "  ·  " + location + " / " + activity
                            + (goal.equals("—") ? "" : "  ·  目標 " + goal));
                }
            }
        }
    }

    private void refreshVisibleTabIfNeeded() {
        if (currentTab == Tab.CONVERSATION) {
            if (currentRoomId.isEmpty()) {
                renderConversationList();
            } else {
                int count = conversations.messageCount(currentRoomId);
                if (count != lastRenderedMessageCount && !processing) renderConversationRoom();
            }
            return;
        }
        if (currentTab == Tab.STATUS || currentTab == Tab.DUNGEON) renderCurrentTab();
    }

    private void renderCurrentTab() {
        if (content == null) return;
        content.removeAllViews();
        switch (currentTab) {
            case CONVERSATION:
                if (currentRoomId.isEmpty()) renderConversationList();
                else renderConversationRoom();
                break;
            case STATUS:
                renderStatus();
                break;
            case DUNGEON:
                renderDungeon();
                break;
            case CODEX:
                renderCodex();
                break;
            case SETTINGS:
                renderSettings();
                break;
        }
    }

    private void renderConversationList() {
        if (content == null || currentTab != Tab.CONVERSATION) return;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = verticalBody();

        TextView intro = text(
                "あなたとの会話、NPC同士の私的会話、グループ会話を同じ世界の時系列として表示します。",
                11, AppUiTheme.APP_MUTED, false);
        body.addView(intro, matchTop(0));

        String[] rooms = demoRuntime.roomIds();
        addRoomSection(body, "あなたとの会話", rooms, 0);
        addRoomSection(body, "NPC同士の会話 · 観測のみ", rooms, 1);
        addRoomSection(body, "グループ", rooms, 2);

        scroll.addView(body);
        content.addView(scroll, matchFrame());
    }

    private void addRoomSection(LinearLayout body, String title, String[] rooms, int kind) {
        TextView section = text(title, 12, Color.rgb(150, 185, 221), true);
        LinearLayout.LayoutParams sp = matchTop(dp(12));
        body.addView(section, sp);
        int added = 0;
        for (String roomId : rooms) {
            boolean peer = NpcPeerRoomPolicy.isPeerRoom(roomId);
            boolean group = DemoRuntimeV032.ROOM_GROUP.equals(roomId);
            boolean direct = !peer && !group;
            if ((kind == 0 && !direct) || (kind == 1 && !peer) || (kind == 2 && !group)) continue;
            body.addView(roomCard(roomId), matchTop(dp(6)));
            added++;
        }
        if (added == 0) {
            TextView empty = text("まだ対象のルームはありません", 11, Color.rgb(105, 124, 145), false);
            body.addView(empty, matchTop(dp(5)));
        }
    }

    private View roomCard(String roomId) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openRoom(roomId));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(demoRuntime.roomTitle(roomId), 15, AppUiTheme.APP_TEXT, true);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (NpcPeerRoomPolicy.isPeerRoom(roomId)) {
            TextView observer = text("OBSERVE", 9, Color.rgb(118, 187, 224), true);
            row.addView(observer);
        }
        card.addView(row);
        TextView subtitle = text(demoRuntime.roomSubtitle(roomId), 10, AppUiTheme.APP_MUTED, false);
        card.addView(subtitle, matchTop(dp(2)));
        JSONObject last = conversations.lastMessage(roomId);
        TextView preview = text(last == null ? "まだ会話はありません"
                : last.optString("sender_name", "") + ": " + last.optString("text", ""),
                12, Color.rgb(190, 203, 219), false);
        preview.setMaxLines(2);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(preview, matchTop(dp(7)));
        return card;
    }

    private void openRoom(String roomId) {
        currentRoomId = roomId == null ? "" : roomId;
        lastRenderedMessageCount = -1;
        List<String> peer = NpcPeerRoomPolicy.participants(currentRoomId);
        if (!peer.isEmpty() && !peer.contains(focusedNpcId)) selectFocus(peer.get(0));
        String direct = directNpcId(currentRoomId);
        if (!direct.isEmpty()) selectFocus(direct);
        renderConversationRoom();
    }

    private void renderConversationRoom() {
        if (content == null || currentRoomId.isEmpty()) return;
        content.removeAllViews();
        LinearLayout room = new LinearLayout(this);
        room.setOrientation(LinearLayout.VERTICAL);

        LinearLayout roomHeader = new LinearLayout(this);
        roomHeader.setOrientation(LinearLayout.HORIZONTAL);
        roomHeader.setGravity(Gravity.CENTER_VERTICAL);
        Button back = smallButton("‹ 一覧");
        back.setOnClickListener(v -> {
            currentRoomId = "";
            lastRenderedMessageCount = -1;
            renderConversationList();
        });
        roomHeader.addView(back, new LinearLayout.LayoutParams(dp(82), dp(42)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(8), 0, 0, 0);
        names.addView(text(demoRuntime.roomTitle(currentRoomId), 15, AppUiTheme.APP_TEXT, true));
        names.addView(text(demoRuntime.roomSubtitle(currentRoomId), 10, AppUiTheme.APP_MUTED, false));
        roomHeader.addView(names, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        room.addView(roomHeader);

        ScrollView messagesScroll = new ScrollView(this);
        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(6), 0, dp(8));
        JSONArray items = conversations.messages(currentRoomId);
        lastRenderedMessageCount = items.length();
        if (items.length() == 0) {
            TextView empty = text("まだ会話はありません", 12, AppUiTheme.APP_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(40), dp(12), dp(40));
            messages.addView(empty);
        } else {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) messages.addView(messageBubble(item));
            }
        }
        messagesScroll.addView(messages);
        room.addView(messagesScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        boolean peerRoom = NpcPeerRoomPolicy.isPeerRoom(currentRoomId);
        if (peerRoom) {
            TextView observer = text(
                    "NPC同士の私的会話を観測中。あなたはこのルームの参加者ではありません。",
                    11, Color.rgb(137, 185, 218), true);
            observer.setGravity(Gravity.CENTER);
            observer.setPadding(dp(10), dp(10), dp(10), dp(10));
            observer.setBackground(cardBackground(
                    Color.rgb(13, 35, 48), Color.rgb(45, 86, 111), 12));
            room.addView(observer);
        } else {
            processingStatus = text(processing ? "NPCが考えています…" : "", 10,
                    Color.rgb(135, 176, 217), true);
            room.addView(processingStatus, matchTop(dp(3)));
            LinearLayout composer = new LinearLayout(this);
            composer.setOrientation(LinearLayout.HORIZONTAL);
            composer.setGravity(Gravity.BOTTOM);
            EditText input = new EditText(this);
            input.setHint("メッセージ");
            input.setHintTextColor(Color.rgb(100, 120, 142));
            input.setTextColor(AppUiTheme.APP_TEXT);
            input.setTextSize(14);
            input.setMinLines(1);
            input.setMaxLines(4);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setBackground(cardBackground(
                    Color.rgb(18, 29, 43), Color.rgb(48, 67, 88), 12));
            input.setPadding(dp(10), dp(8), dp(10), dp(8));
            composer.addView(input, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button send = smallButton("送信");
            send.setEnabled(!processing);
            send.setOnClickListener(v -> sendMessage(input, send));
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(72), dp(50));
            sendParams.leftMargin = dp(6);
            composer.addView(send, sendParams);
            room.addView(composer, matchTop(dp(4)));
        }
        content.addView(room, matchFrame());
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private View messageBubble(JSONObject message) {
        String senderId = message.optString("sender_id", "");
        boolean user = "user".equals(senderId);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(user ? Gravity.END : Gravity.START);
        wrap.setPadding(dp(2), dp(4), dp(2), dp(4));
        if (!user) wrap.addView(text(message.optString("sender_name", "NPC"),
                10, AppUiTheme.APP_MUTED, false));
        TextView bubble = text(message.optString("text", ""), 14, AppUiTheme.APP_TEXT, false);
        bubble.setPadding(dp(11), dp(8), dp(11), dp(8));
        bubble.setMaxWidth(dp(330));
        bubble.setBackground(cardBackground(
                user ? Color.rgb(36, 78, 65) : Color.rgb(20, 32, 48),
                user ? Color.rgb(56, 119, 94) : Color.rgb(48, 67, 89), 14));
        JSONArray trace = message.optJSONArray("brain_trace");
        if (trace != null && trace.length() > 0) {
            bubble.setClickable(true);
            bubble.setOnClickListener(v -> showBrainTrace(message));
        }
        wrap.addView(bubble);
        return wrap;
    }

    private void showBrainTrace(JSONObject message) {
        JSONArray trace = message.optJSONArray("brain_trace");
        if (trace == null || trace.length() == 0) return;
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < trace.length(); i++) {
            JSONObject stage = trace.optJSONObject(i);
            if (stage == null) continue;
            if (body.length() > 0) body.append("\n\n");
            body.append(stage.optString("stage_label", stage.optString("stage_id", "脳機能")))
                    .append("\n")
                    .append(stage.optString("summary", ""));
        }
        new AlertDialog.Builder(this)
                .setTitle(message.optString("sender_name", "NPC") + " · 脳内トレース")
                .setMessage(body.toString())
                .setPositiveButton("閉じる", null)
                .show();
    }

    private void sendMessage(EditText input, Button send) {
        if (processing || currentRoomId.isEmpty() || NpcPeerRoomPolicy.isPeerRoom(currentRoomId)) return;
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return;
        String apiKey = loadApiKey();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "設定タブでOpenAI APIキーを設定してください", Toast.LENGTH_LONG).show();
            selectTab(Tab.SETTINGS, true);
            return;
        }
        JSONObject message = conversations.appendUserMessage(currentRoomId, value, System.currentTimeMillis());
        input.setText("");
        processing = true;
        send.setEnabled(false);
        if (processingStatus != null) processingStatus.setText("NPCが世界状態と記憶を確認しています…");
        String room = currentRoomId;
        String effort = modelSettingsStore.reasoningEffort();
        new Thread(() -> {
            String error = "";
            try {
                demoRuntime.processUserMessage(room, message, apiKey, effort, listener());
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String finalError = error;
            runOnUiThread(() -> {
                processing = false;
                lastRenderedMessageCount = -1;
                if (!finalError.isEmpty()) {
                    Toast.makeText(this, finalError, Toast.LENGTH_LONG).show();
                }
                if (currentTab == Tab.CONVERSATION && room.equals(currentRoomId)) renderConversationRoom();
            });
        }, "world-shell-chat").start();
    }

    private DemoRuntimeV032.Listener listener() {
        return new DemoRuntimeV032.Listener() {
            @Override public void onNpcStarted(String npcId, String displayName, int current, int total) {
                runOnUiThread(() -> {
                    if (processingStatus != null) processingStatus.setText(displayName + " が考えています…");
                });
            }
            @Override public void onStageStarted(String npcId, String displayName, String stageId,
                    String stageLabel, int current, int total) {
                runOnUiThread(() -> {
                    if (processingStatus != null) processingStatus.setText(displayName + " · " + stageLabel);
                });
            }
            @Override public void onStageCompleted(String npcId, String displayName, String stageId,
                    String stageLabel, int current, int total, String summary, double confidence,
                    JSONArray salientFacts, String personalityEffect, String model, String reasoningEffort) {
            }
            @Override public void onNpcFinished(String npcId, String displayName, boolean sentMessage) {
            }
        };
    }

    private void maybeRunSpontaneousConversation() {
        if (processing || !demoRuntime.hasDueSpontaneousEvents()) return;
        String apiKey = loadApiKey();
        if (apiKey.isEmpty()) return;
        processing = true;
        new Thread(() -> {
            try {
                demoRuntime.processPendingSpontaneous(apiKey, modelSettingsStore.reasoningEffort(), listener());
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                processing = false;
                lastRenderedMessageCount = -1;
                if (currentTab == Tab.CONVERSATION) renderCurrentTab();
            });
        }, "world-shell-spontaneous").start();
    }

    private void renderStatus() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = verticalBody();
        if (focusedNpcId.isEmpty()) {
            body.addView(emptyCard("生存NPCがいません"));
            scroll.addView(body);
            content.addView(scroll, matchFrame());
            return;
        }
        JSONObject snapshot = query.snapshot(focusedNpcId);
        JSONObject npc = snapshot.optJSONObject("npc");
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, focusedNpcId));
        if (npc == null) {
            body.addView(emptyCard("Canonical World上のNPC状態を取得できません"));
        } else {
            LinearLayout hero = card();
            hero.addView(text(character.displayName(), 22, AppUiTheme.APP_TEXT, true));
            hero.addView(text("WORLD REV " + snapshot.optLong("revision", 0L)
                    + " · STATE v" + npc.optLong("state_version", 0L),
                    10, Color.rgb(122, 162, 205), true), matchTop(dp(3)));
            addKeyValue(hero, "現在地", displayValue(npc.optString("location", "")));
            addKeyValue(hero, "活動", displayValue(npc.optString("activity", "")));
            addKeyValue(hero, "目標", displayValue(npc.optString("goal", "")));
            addKeyValue(hero, "ユーザーとの関係", character.relationshipToUser());
            body.addView(hero);

            JSONObject inner = npc.optJSONObject("inner_life");
            JSONObject dynamic = npc.optJSONObject("dynamic_state");
            LinearLayout mind = card();
            mind.addView(text("同じ脳の現在状態", 15, AppUiTheme.APP_TEXT, true));
            mind.addView(text("内面  " + compactJson(inner), 11, AppUiTheme.APP_MUTED, false), matchTop(dp(7)));
            mind.addView(text("動的状態  " + compactJson(dynamic), 11, AppUiTheme.APP_MUTED, false), matchTop(dp(5)));
            body.addView(mind, matchTop(dp(9)));

            DemoCognitionObserver.Snapshot cognition = DemoCognitionObserver.snapshot(this, focusedNpcId);
            LinearLayout graphCard = card();
            graphCard.addView(text("認知グラフ · " + (cognition.live ? "思考中" : "最新保存状態"),
                    15, AppUiTheme.APP_TEXT, true));
            TextView source = text("このタブ専用のBrainではなく、会話・生活・ダンジョンで共通利用するNPCの認知状態です。",
                    10, AppUiTheme.APP_MUTED, false);
            graphCard.addView(source, matchTop(dp(4)));
            CognitiveSphereView sphere = new CognitiveSphereView(this);
            sphere.setGraph(CognitiveGraphBuilder.isValidSemanticSnapshot(cognition.cognitiveGraph)
                    ? CognitiveGraphBuilder.buildFromSemanticSnapshot(cognition.cognitiveGraph)
                    : CognitiveGraphBuilder.buildFromSemanticSnapshot(null));
            graphCard.addView(sphere, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(330)));
            body.addView(graphCard, matchTop(dp(9)));
        }
        scroll.addView(body);
        content.addView(scroll, matchFrame());
    }

    private void renderDungeon() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        LinearLayout mode = new LinearLayout(this);
        mode.setOrientation(LinearLayout.HORIZONTAL);
        Button focused = modeButton("観測NPC", !individualDungeonMode);
        focused.setOnClickListener(v -> {
            individualDungeonMode = false;
            renderDungeon();
        });
        mode.addView(focused, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button all = modeButton("最大8人を同時監視", individualDungeonMode);
        all.setOnClickListener(v -> {
            individualDungeonMode = true;
            renderDungeon();
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(42), 1f);
        ap.leftMargin = dp(6);
        mode.addView(all, ap);
        page.addView(mode);

        TextView boundary = text(
                "進行主体はWorld Runtime。タブを開く・閉じる・NPCを選ぶ操作ではダンジョンを進めません。",
                10, Color.rgb(126, 158, 190), false);
        page.addView(boundary, matchTop(dp(5)));
        if (individualDungeonMode) {
            page.addView(buildDungeonMonitor(), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            page.addView(buildFocusedDungeon(), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        content.addView(page, matchFrame());
    }

    private View buildFocusedDungeon() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(6), 0, 0);
        if (focusedNpcId.isEmpty()) {
            body.addView(emptyCard("観測できるNPCがいません"));
            return body;
        }
        JSONObject snapshot = query.snapshot(focusedNpcId);
        JSONObject npc = snapshot.optJSONObject("npc");
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, focusedNpcId));
        LinearLayout hud = card();
        hud.addView(text(character.displayName(), 16, AppUiTheme.APP_TEXT, true));
        hud.addView(text("WORLD REV " + snapshot.optLong("revision", 0L),
                10, Color.rgb(126, 163, 202), true), matchTop(dp(2)));
        body.addView(hud);

        DungeonBoardView board = new DungeonBoardView(this);
        boolean present = npc != null && npc.optBoolean("dungeon_present", false)
                && !npc.optBoolean("dead", false);
        DungeonState state = npc == null ? null : DungeonState.fromJson(npc.optJSONObject("dungeon_actor"));
        if (!present || state == null) {
            board.setState(null);
            hud.addView(text("ダンジョン外 · "
                    + displayValue(npc == null ? "" : npc.optString("location", "")) + " / "
                    + displayValue(npc == null ? "" : npc.optString("activity", "")),
                    12, AppUiTheme.APP_MUTED, false), matchTop(dp(7)));
            hud.addView(text("人格・目標・記憶から参加を判断します。UI側の強制参加ゲートはありません。",
                    10, Color.rgb(150, 177, 202), false), matchTop(dp(4)));
        } else {
            board.setState(state);
            int hp = Math.max(0, state.hp);
            int max = Math.max(1, state.maxHp);
            hud.addView(text(state.floor + "F · TURN " + state.turn + " · HP " + hp + "/" + max,
                    13, Color.rgb(197, 225, 212), true), matchTop(dp(6)));
            String action = state.lastAction == null ? "" : state.lastAction.trim();
            hud.addView(text("直近の行動  " + (action.isEmpty() ? "観測待ち" : action),
                    11, AppUiTheme.APP_MUTED, false), matchTop(dp(4)));
        }
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardParams.topMargin = dp(7);
        body.addView(board, boardParams);
        return body;
    }

    private View buildDungeonMonitor() {
        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, dp(4), 0, dp(10));
        List<String> present = new ArrayList<>();
        for (String npcId : registry.activeNpcIds()) {
            JSONObject npc = query.snapshot(npcId).optJSONObject("npc");
            if (npc != null && npc.optBoolean("dungeon_present", false)) present.add(npcId);
        }
        for (int index = 0; index < IndividualDungeonPolicy.MAX_SLOTS; index++) {
            LinearLayout panel = card();
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(220);
            params.columnSpec = GridLayout.spec(index % 2, 1f);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            if (index >= present.size()) {
                panel.addView(text("空き " + (index + 1), 11, AppUiTheme.APP_MUTED, true));
                panel.addView(text("探索中NPCなし", 9, Color.rgb(105, 125, 146), false), matchTop(dp(2)));
            } else {
                String npcId = present.get(index);
                JSONObject snapshot = query.snapshot(npcId);
                JSONObject npc = snapshot.optJSONObject("npc");
                DungeonState state = npc == null ? null : DungeonState.fromJson(npc.optJSONObject("dungeon_actor"));
                CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
                panel.addView(text(character.displayName(), 11, AppUiTheme.APP_TEXT, true));
                panel.addView(text(state == null ? "開始待ち" : "HP " + state.hp + "/" + state.maxHp
                        + " · " + state.floor + "F · T" + state.turn,
                        8, AppUiTheme.APP_MUTED, false), matchTop(dp(1)));
                DungeonBoardView board = new DungeonBoardView(this);
                board.setClickable(false);
                board.setFocusable(false);
                board.setState(state);
                panel.addView(board, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                panel.setOnClickListener(v -> selectFocus(npcId));
                panel.setClickable(true);
            }
            grid.addView(panel, params);
        }
        scroll.addView(grid);
        return scroll;
    }

    private void renderCodex() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = verticalBody();
        List<NpcArchiveStore.Record> records = archive.records();
        TextView intro = text("死亡したNPCだけを保存する世界の記録。生存NPCの状態はNPC状況タブで観測します。",
                11, AppUiTheme.APP_MUTED, false);
        body.addView(intro);
        if (records.isEmpty()) {
            body.addView(emptyCard("死亡したNPCの記録はまだありません"), matchTop(dp(10)));
        } else {
            for (NpcArchiveStore.Record record : records) {
                LinearLayout card = card();
                card.addView(text(record.displayName(), 19, AppUiTheme.APP_TEXT, true));
                card.addView(text("MEMORIAL · " + record.floor + "F · TURN " + record.turn,
                        10, Color.rgb(205, 177, 112), true), matchTop(dp(3)));
                JSONObject traits = record.traits();
                card.addView(text("性格  外向 " + percent(traits.optDouble("extraversion", 0.0))
                                + " / 協調 " + percent(traits.optDouble("agreeableness", 0.0))
                                + " / 誠実 " + percent(traits.optDouble("conscientiousness", 0.0)),
                        11, AppUiTheme.APP_MUTED, false), matchTop(dp(7)));
                body.addView(card, matchTop(dp(9)));
            }
        }
        scroll.addView(body);
        content.addView(scroll, matchFrame());
    }

    private void renderSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = verticalBody();
        LinearLayout ai = card();
        ai.addView(text("AI / 推論設定", 17, AppUiTheme.APP_TEXT, true));
        ai.addView(text("GPT-5.6 Luna · "
                        + ModelSettingsStore.displayLabel(modelSettingsStore.reasoningEffort())
                        + " · APIキー " + (loadApiKey().isEmpty() ? "未設定" : "設定済み"),
                11, AppUiTheme.APP_MUTED, false), matchTop(dp(5)));
        Button details = actionButton("詳細設定を開く");
        details.setOnClickListener(v -> openDetailsSettings());
        ai.addView(details, matchTop(dp(9)));
        body.addView(ai);

        JSONObject diagnostics = query.diagnostics();
        LinearLayout world = card();
        world.addView(text("World Runtime", 17, AppUiTheme.APP_TEXT, true));
        world.addView(text("revision " + query.revision()
                        + " · projection lag " + diagnostics.optLong("projection_lag", 0L),
                11, AppUiTheme.APP_MUTED, false), matchTop(dp(5)));
        world.addView(text("会話・生活・ダンジョン・関係・記憶は同じcanonical worldを共有します。",
                11, Color.rgb(164, 190, 216), false), matchTop(dp(4)));
        body.addView(world, matchTop(dp(9)));

        if (NPCBrainApplication.isDebugBuild()) {
            LinearLayout debug = card();
            debug.addView(text("DEBUG", 13, Color.rgb(226, 176, 106), true));
            Button manager = actionButton("NPC管理を開く");
            manager.setOnClickListener(v -> openNpcManager());
            debug.addView(manager, matchTop(dp(7)));
            body.addView(debug, matchTop(dp(9)));
        }
        scroll.addView(body);
        content.addView(scroll, matchFrame());
    }

    private void openDetailsSettings() {
        startActivity(new Intent(this, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        overridePendingTransition(0, 0);
    }

    private void openNpcManager() {
        startActivity(new Intent(this, NpcManagerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        overridePendingTransition(0, 0);
    }

    private LinearLayout verticalBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(5), 0, dp(12));
        return body;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        card.setBackground(cardBackground(AppUiTheme.APP_SURFACE, AppUiTheme.APP_BORDER, 14));
        return card;
    }

    private View emptyCard(String message) {
        LinearLayout card = card();
        TextView text = text(message, 12, AppUiTheme.APP_MUTED, false);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(8), dp(28), dp(8), dp(28));
        card.addView(text);
        return card;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(10);
        button.setTextColor(AppUiTheme.APP_TEXT);
        button.setBackground(cardBackground(Color.rgb(23, 39, 58), Color.rgb(48, 69, 93), 11));
        return button;
    }

    private Button modeButton(String label, boolean selected) {
        Button button = smallButton(label);
        button.setBackground(cardBackground(
                selected ? Color.rgb(40, 87, 150) : Color.rgb(20, 34, 50),
                selected ? Color.rgb(77, 136, 203) : Color.rgb(48, 69, 93), 11));
        button.setEnabled(!selected);
        return button;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setBackground(cardBackground(Color.rgb(40, 87, 150), Color.rgb(67, 116, 177), 12));
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void addKeyValue(LinearLayout parent, String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, 0);
        TextView label = text(key, 11, AppUiTheme.APP_MUTED, false);
        row.addView(label, new LinearLayout.LayoutParams(dp(112),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView result = text(value, 12, AppUiTheme.APP_TEXT, true);
        row.addView(result, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchTop(int top) {
        LinearLayout.LayoutParams params = wrap();
        params.topMargin = top;
        return params;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private GradientDrawable cardBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(AppUiTheme.APP_BACKGROUND);
        window.setNavigationBarColor(AppUiTheme.APP_BACKGROUND);
    }

    private void hideKeyboard() {
        View current = getCurrentFocus();
        if (current == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(current.getWindowToken(), 0);
        current.clearFocus();
    }

    private String loadApiKey() {
        try {
            return apiKeyStore.load().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String directNpcId(String roomId) {
        if (roomId == null || !roomId.startsWith("direct_")) return "";
        String candidate = roomId.substring("direct_".length());
        return registry.activeNpcIds().contains(candidate) ? candidate : "";
    }

    private static String displayValue(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() || "unknown".equalsIgnoreCase(text) ? "—" : text;
    }

    private static String compactJson(JSONObject json) {
        if (json == null || json.length() == 0) return "—";
        String raw = json.toString();
        return raw.length() <= 180 ? raw : raw.substring(0, 177) + "…";
    }

    private static String percent(double value) {
        double normalized = value <= 1.0 ? value * 100.0 : value;
        return Math.round(Math.max(0.0, Math.min(100.0, normalized))) + "%";
    }

    private static String formatWorldTime(long millis) {
        if (millis <= 0L) return "TIME —";
        return new SimpleDateFormat("MM/dd HH:mm:ss", Locale.JAPAN).format(new Date(millis));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
