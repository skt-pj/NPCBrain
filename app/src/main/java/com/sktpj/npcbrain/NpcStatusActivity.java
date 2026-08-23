package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NpcStatusActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 500L);
        }
    };

    private WorldRuntimeV040 worldRuntime;
    private NpcArchiveStore archiveStore;
    private NpcRegistryStore registryStore;
    private NpcAiStaminaStore staminaStore;
    private String selectedNpcId = "npc1";
    private final Map<String, Button> selectorButtons = new LinkedHashMap<>();
    private TextView nameValue;
    private TextView activityValue;
    private TextView locationValue;
    private TextView goalValue;
    private TextView contextValue;
    private TextView stateValue;
    private TextView timeValue;
    private TextView traceValue;
    private TextView replyValue;
    private TextView staminaValue;
    private CognitiveSphereView sphereView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        worldRuntime = new WorldRuntimeV040(this);
        archiveStore = new NpcArchiveStore(this);
        registryStore = new NpcRegistryStore(this);
        staminaStore = new NpcAiStaminaStore(this);
        normalizeSelection();
        setContentView(buildContent());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        normalizeSelection();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void normalizeSelection() {
        List<String> ids = NpcStatusPolicy.selectorNpcIds(registryStore.npcIds());
        if (ids.isEmpty()) return;
        if (!ids.contains(selectedNpcId)) selectedNpcId = ids.get(0);
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 12, 22));
        applySafeInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(8));

        TextView title = new TextView(this);
        title.setText("NPCBrain");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView subtitle = new TextView(this);
        subtitle.setText("現在状況 / 実認知グラフ");
        subtitle.setTextColor(Color.rgb(132, 157, 190));
        subtitle.setTextSize(11);
        header.addView(subtitle);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button conversation = tabButton("会話", false);
        conversation.setOnClickListener(v -> openConversation());
        tabs.addView(conversation, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button status = tabButton("NPC状況", true);
        status.setEnabled(false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        statusParams.leftMargin = dp(4);
        tabs.addView(status, statusParams);

        Button dungeon = tabButton("ダンジョン", false);
        dungeon.setOnClickListener(v -> openDungeon());
        LinearLayout.LayoutParams dungeonParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        dungeonParams.leftMargin = dp(4);
        tabs.addView(dungeon, dungeonParams);

        Button codex = tabButton("図鑑", false);
        codex.setOnClickListener(v -> openCodex());
        LinearLayout.LayoutParams codexParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        codexParams.leftMargin = dp(4);
        tabs.addView(codex, codexParams);
        root.addView(tabs);

        HorizontalScrollView selectorScroll = new HorizontalScrollView(this);
        selectorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setPadding(0, dp(8), 0, dp(8));
        selectorButtons.clear();
        for (String npcId : NpcStatusPolicy.selectorNpcIds(registryStore.npcIds())) {
            Button button = selectorButton(selectorLabel(npcId), npcId.equals(selectedNpcId));
            button.setOnClickListener(v -> selectNpc(npcId));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
            params.rightMargin = dp(8);
            selector.addView(button, params);
            selectorButtons.put(npcId, button);
        }
        selectorScroll.addView(selector, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(selectorScroll);

        ScrollView statusScroll = new ScrollView(this);
        statusScroll.setFillViewport(false);
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusCard.setBackground(cardBackground(Color.rgb(16, 26, 40), Color.rgb(38, 62, 88), 14));
        TextView statusTitle = new TextView(this);
        statusTitle.setText("NPCの現在状況");
        statusTitle.setTextColor(Color.rgb(220, 234, 252));
        statusTitle.setTextSize(15);
        statusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(statusTitle);
        nameValue = addStatusRow(statusCard, "名前");
        activityValue = addStatusRow(statusCard, "現在の活動");
        locationValue = addStatusRow(statusCard, "場所");
        goalValue = addStatusRow(statusCard, "目標");
        contextValue = addStatusRow(statusCard, "状況");
        stateValue = addStatusRow(statusCard, "動的状態");
        timeValue = addStatusRow(statusCard, "現在時刻");
        traceValue = addStatusRow(statusCard, "脳内ソース");
        replyValue = addStatusRow(statusCard, "返信判断");
        staminaValue = addStatusRow(statusCard, "AI STAMINA");

        Button diagnose = new Button(this);
        diagnose.setText("返信診断を見る");
        diagnose.setAllCaps(false);
        diagnose.setTextSize(12);
        diagnose.setTextColor(Color.rgb(220, 234, 252));
        diagnose.setBackgroundColor(Color.rgb(25, 50, 78));
        LinearLayout.LayoutParams diagnoseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        diagnoseParams.topMargin = dp(10);
        statusCard.addView(diagnose, diagnoseParams);
        diagnose.setOnClickListener(v -> showReplyDiagnostic());

        statusScroll.addView(statusCard);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(330));
        scp.topMargin = dp(2);
        root.addView(statusScroll, scp);

        LinearLayout graphHeader = new LinearLayout(this);
        graphHeader.setOrientation(LinearLayout.HORIZONTAL);
        graphHeader.setGravity(Gravity.CENTER_VERTICAL);
        graphHeader.setPadding(0, dp(10), 0, dp(7));
        TextView graphTitle = new TextView(this);
        graphTitle.setText("NPCが実際に使っている認知グラフ");
        graphTitle.setTextColor(Color.rgb(218, 232, 250));
        graphTitle.setTextSize(15);
        graphTitle.setTypeface(Typeface.DEFAULT_BOLD);
        graphHeader.addView(graphTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button reset = new Button(this);
        reset.setText("視点リセット");
        reset.setTextSize(11);
        reset.setAllCaps(false);
        reset.setTextColor(Color.rgb(197, 220, 250));
        reset.setBackgroundColor(Color.rgb(25, 39, 58));
        reset.setOnClickListener(v -> sphereView.resetView());
        graphHeader.addView(reset, new LinearLayout.LayoutParams(dp(104), dp(48)));
        root.addView(graphHeader);

        sphereView = new CognitiveSphereView(this);
        sphereView.setContentDescription("NPCの実認知グラフ。ドラッグで回転、ピンチで拡大、点をタップして詳細表示");
        sphereView.setNodeListener(this::showNodeDetails);
        root.addView(sphereView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private String selectorLabel(String npcId) {
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
        String name = character.displayName();
        if (character.isDead()) return npcId.toUpperCase(Locale.US) + " · 死亡";
        if (name == null || name.trim().isEmpty() || "NPC".equals(name.trim())) {
            return npcId.toUpperCase(Locale.US);
        }
        return name.trim();
    }

    private void selectNpc(String npcId) {
        selectedNpcId = NpcId.of(npcId).value();
        for (Map.Entry<String, Button> entry : selectorButtons.entrySet()) {
            entry.getValue().setBackgroundColor(entry.getKey().equals(selectedNpcId)
                    ? Color.rgb(42, 91, 156) : Color.rgb(25, 39, 58));
        }
        refreshStatus();
    }

    private void refreshStatus() {
        if (worldRuntime == null || sphereView == null) return;
        normalizeSelection();
        if (archiveStore != null && archiveStore.isDead(selectedNpcId)) {
            nameValue.setText(selectorLabel(selectedNpcId));
            activityValue.setText("死亡");
            locationValue.setText("—");
            goalValue.setText("—");
            contextValue.setText("図鑑に保存済み");
            stateValue.setText("—");
            timeValue.setText("—");
            traceValue.setText("図鑑に保存済み");
            replyValue.setText(NpcStatusPolicy.REPLY_NONE);
            updateStamina();
            sphereView.setGraph(CognitiveGraphBuilder.buildFromSemanticSnapshot(null));
            return;
        }

        LifeState lifeState = worldRuntime.lifeState(selectedNpcId);
        Context storageContext = NpcContexts.storage(this, selectedNpcId);
        CharacterStateStore character = new CharacterStateStore(storageContext);
        DemoCognitionObserver.Snapshot cognition = DemoCognitionObserver.snapshot(this, selectedNpcId);
        boolean exact = CognitiveGraphBuilder.isValidSemanticSnapshot(cognition.cognitiveGraph);

        nameValue.setText(character.displayName());
        activityValue.setText(displayValue(lifeState.currentActivity()));
        locationValue.setText(displayValue(lifeState.location()));
        goalValue.setText(displayValue(lifeState.currentGoal()));
        contextValue.setText(displayValue(lifeState.activeContext()));
        stateValue.setText(character.dynamicStateSummary());
        timeValue.setText(formatDateTime(lifeState.worldTimeMs()));

        if (cognition.live && exact) {
            traceValue.setText("リアルタイム実認知グラフ");
        } else if (cognition.live) {
            traceValue.setText("思考中 · 実認知グラフ初期化中");
        } else if (exact) {
            traceValue.setText("保存済み実認知グラフ · " + formatTime(cognition.timeMs));
        } else if (cognition.stages.length() > 0) {
            traceValue.setText("旧データ · 実認知グラフなし");
        } else {
            traceValue.setText("待機中 · 実認知グラフなし");
        }

        replyValue.setText(NpcStatusPolicy.replyState(
                selectedNpcId,
                latestTraceSenderId(selectedNpcId),
                cognition.live));
        updateStamina();
        sphereView.setGraph(exact
                ? CognitiveGraphBuilder.buildFromSemanticSnapshot(cognition.cognitiveGraph)
                : CognitiveGraphBuilder.buildFromSemanticSnapshot(null));
    }

    private void updateStamina() {
        NpcAiStaminaStore.Snapshot snapshot = staminaStore.snapshot(selectedNpcId);
        staminaValue.setText(String.format(
                Locale.JAPAN,
                "%d%% · 概算 ¥%.2f / ¥%.2f · %,d tokens",
                snapshot.remainingPercent,
                snapshot.remainingJpy,
                DungeonTokenCostPolicy.MAX_BUDGET_JPY,
                snapshot.totalTokens));
    }

    private String latestTraceSenderId(String npcId) {
        ConversationStore store = new ConversationStore(getApplicationContext());
        String bestSender = "";
        long bestTime = Long.MIN_VALUE;
        String[] rooms = ("npc1".equals(npcId) || "npc2".equals(npcId))
                ? new String[]{"direct_" + npcId, DemoRuntimeV032.ROOM_GROUP}
                : new String[]{"direct_" + npcId};
        for (String room : rooms) {
            JSONArray messages = store.messages(room);
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.optJSONObject(i);
                if (message == null) continue;
                String sender = message.optString("sender_id", "");
                if (!belongsToNpc(sender, npcId)) continue;
                JSONArray trace = message.optJSONArray("brain_trace");
                if (trace == null || trace.length() == 0) continue;
                long time = message.optLong("time_ms", 0L);
                if (time >= bestTime) {
                    bestTime = time;
                    bestSender = sender;
                }
            }
        }
        return bestSender;
    }

    private static boolean belongsToNpc(String senderId, String npcId) {
        String sender = senderId == null ? "" : senderId.trim();
        return npcId.equals(sender)
                || ("decision_" + npcId).equals(sender)
                || ("runtime_decision_" + npcId).equals(sender);
    }

    private void showReplyDiagnostic() {
        DemoCognitionObserver.Snapshot cognition = DemoCognitionObserver.snapshot(this, selectedNpcId);
        StringBuilder body = new StringBuilder();
        body.append("返信なしを避けたい場合は、この順で公開要約を確認します。\n")
                .append("Global Workspace → 行動選択 → 価値判断 → 注意・重要度 → 意味記憶\n\n")
                .append("activationは『今どれだけ注目しているか』で、正しさや返信確率ではありません。\n")
                .append("Global Workspaceが無言を選び、価値判断で会話価値が低い・注意が別目標へ向く・意味記憶で距離を置く方針が強い場合、そこに合う話題や関係性を会話から変えるのが有効です。\n\n");

        String[] order = {"global_workspace", "action_selection", "valuation", "salience", "semantic_memory"};
        for (String stageId : order) {
            JSONObject stage = findStage(cognition.stages, stageId);
            String label = stage == null ? stageLabel(stageId) : stage.optString("stage_label", stageLabel(stageId));
            body.append("■ ").append(label).append('\n');
            if (stage == null) {
                body.append("記録なし\n\n");
                continue;
            }
            String summary = stage.optString("summary", "").trim();
            body.append(summary.isEmpty() ? "公開要約なし" : summary);
            body.append("\nconfidence: ")
                    .append((int) Math.round(stage.optDouble("confidence", 0.0) * 100.0))
                    .append("%");
            String effect = stage.optString("personality_effect", "").trim();
            if (!effect.isEmpty()) body.append("\n人格の影響: ").append(effect);
            body.append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("返信診断 · " + selectorLabel(selectedNpcId))
                .setMessage(body.toString().trim())
                .setPositiveButton("閉じる", null)
                .show();
    }

    private static JSONObject findStage(JSONArray stages, String stageId) {
        if (stages == null) return null;
        for (int i = stages.length() - 1; i >= 0; i--) {
            JSONObject stage = stages.optJSONObject(i);
            if (stage != null && stageId.equals(stage.optString("stage_id", ""))) return stage;
        }
        return null;
    }

    private static String stageLabel(String stageId) {
        if ("global_workspace".equals(stageId)) return "Global Workspace";
        if ("action_selection".equals(stageId)) return "行動選択";
        if ("valuation".equals(stageId)) return "価値判断";
        if ("salience".equals(stageId)) return "注意・重要度";
        if ("semantic_memory".equals(stageId)) return "意味記憶";
        return stageId;
    }

    private TextView addStatusRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(7), 0, dp(2));
        TextView key = new TextView(this);
        key.setText(label);
        key.setTextColor(Color.rgb(126, 151, 181));
        key.setTextSize(11);
        row.addView(key, new LinearLayout.LayoutParams(dp(90),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView value = new TextView(this);
        value.setTextColor(Color.rgb(230, 238, 249));
        value.setTextSize(12);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setMaxLines(2);
        row.addView(value, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row);
        return value;
    }

    private void showNodeDetails(CognitiveGraph.Node node) {
        StringBuilder body = new StringBuilder();
        body.append(node.detail.isEmpty() ? "公開要約なし" : node.detail);
        body.append("\n\nID: ").append(node.id);
        body.append("\n種類: ").append(node.type);
        if (!node.moduleId.isEmpty()) body.append("\n領域: ").append(node.moduleId);
        body.append("\n中心からの距離: ")
                .append(String.format(Locale.JAPAN, "%.2f", node.radius()));
        body.append("\nactivation: ")
                .append((int) Math.round(node.activation * 100.0)).append("%");
        body.append("\n信頼度: ")
                .append((int) Math.round(node.confidence * 100.0)).append("%");
        new AlertDialog.Builder(this)
                .setTitle(node.label.isEmpty() ? "認知ポイント" : node.label)
                .setMessage(body.toString())
                .setPositiveButton("閉じる", null)
                .show();
    }

    private void openConversation() {
        Intent intent = new Intent(this, DemoActivityV032.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openDungeon() {
        Intent intent = new Intent(this, DungeonActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openCodex() {
        Intent intent = new Intent(this, CodexActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private Button tabButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? Color.WHITE : Color.rgb(188, 203, 222));
        button.setBackgroundColor(selected ? Color.rgb(42, 91, 156) : Color.rgb(21, 33, 49));
        button.setMinHeight(dp(48));
        return button;
    }

    private Button selectorButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(selected ? Color.rgb(42, 91, 156) : Color.rgb(25, 39, 58));
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(88));
        return button;
    }

    private String displayValue(String value) {
        if (value == null || value.trim().isEmpty() || "unknown".equalsIgnoreCase(value.trim())) return "—";
        return value.trim();
    }

    private String formatDateTime(long timeMs) {
        if (timeMs <= 0) return "—";
        return new SimpleDateFormat("MM/dd HH:mm:ss", Locale.JAPAN).format(new Date(timeMs));
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "";
        return new SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(new Date(timeMs));
    }

    private GradientDrawable cardBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(7, 12, 22));
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    }

    private void applySafeInsets(View root) {
        root.setPadding(dp(16), dp(10), dp(16), dp(8));
        if (Build.VERSION.SDK_INT >= 23) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        top = Math.max(top, cutout.getSafeInsetTop());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        right = Math.max(right, cutout.getSafeInsetRight());
                    }
                }
                view.setPadding(
                        Math.max(dp(16), left + dp(10)),
                        Math.max(dp(10), top + dp(4)),
                        Math.max(dp(16), right + dp(10)),
                        Math.max(dp(8), bottom + dp(4)));
                return insets;
            });
        }
    }
}
