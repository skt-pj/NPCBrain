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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private String selectedNpcId = "npc1";
    private Button npc1Button;
    private Button npc2Button;
    private TextView nameValue;
    private TextView activityValue;
    private TextView locationValue;
    private TextView goalValue;
    private TextView contextValue;
    private TextView stateValue;
    private TextView timeValue;
    private TextView traceValue;
    private CognitiveSphereView sphereView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        worldRuntime = new WorldRuntimeV040(this);
        archiveStore = new NpcArchiveStore(this);
        setContentView(buildContent());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
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

        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setPadding(0, dp(8), 0, dp(8));
        npc1Button = selectorButton("NPC1", true);
        npc2Button = selectorButton("NPC2", false);
        npc1Button.setOnClickListener(v -> selectNpc("npc1"));
        npc2Button.setOnClickListener(v -> selectNpc("npc2"));
        selector.addView(npc1Button, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams n2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        n2.leftMargin = dp(8);
        selector.addView(npc2Button, n2);
        root.addView(selector);

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
        statusScroll.addView(statusCard);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(248));
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

    private void selectNpc(String npcId) {
        selectedNpcId = npcId;
        npc1Button.setBackgroundColor("npc1".equals(npcId)
                ? Color.rgb(42, 91, 156) : Color.rgb(25, 39, 58));
        npc2Button.setBackgroundColor("npc2".equals(npcId)
                ? Color.rgb(42, 91, 156) : Color.rgb(25, 39, 58));
        refreshStatus();
    }

    private void refreshStatus() {
        if (worldRuntime == null || sphereView == null) return;
        if (archiveStore != null && archiveStore.isDead(selectedNpcId)) {
            nameValue.setText("死亡");
            activityValue.setText("死亡");
            locationValue.setText("—");
            goalValue.setText("—");
            contextValue.setText("図鑑に保存済み");
            stateValue.setText("—");
            timeValue.setText("—");
            traceValue.setText("図鑑に保存済み");
            sphereView.setGraph(CognitiveGraphBuilder.buildFromSemanticSnapshot(null));
            return;
        }

        LifeState lifeState = worldRuntime.lifeState(selectedNpcId);
        Context storageContext = storageContext(selectedNpcId);
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

        sphereView.setGraph(exact
                ? CognitiveGraphBuilder.buildFromSemanticSnapshot(cognition.cognitiveGraph)
                : CognitiveGraphBuilder.buildFromSemanticSnapshot(null));
    }

    private Context storageContext(String npcId) {
        Context app = getApplicationContext();
        return "npc2".equals(npcId) ? new NpcStorageContext(app, "npc2") : app;
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
                view.setPadding(dp(16) + left, dp(10) + top, dp(16) + right, dp(8) + bottom);
                return insets;
            });
        }
    }
}
