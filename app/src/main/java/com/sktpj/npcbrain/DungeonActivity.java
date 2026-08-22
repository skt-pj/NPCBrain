package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class DungeonActivity extends Activity {
    private static final long[] TURN_INTERVALS = {1100L, 650L, 350L};
    private static final String[] SPEED_LABELS = {"0.5×", "1×", "2×"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable turnTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!paused) advanceTurn();
            scheduleNextTurn();
        }
    };

    private DungeonStore dungeonStore;
    private DungeonMindStore mindStore;
    private DungeonBrainRuntime brainRuntime;
    private SecureApiKeyStore apiKeyStore;
    private ModelSettingsStore modelSettingsStore;

    private String selectedNpcId = "npc1";
    private DungeonState state;
    private DungeonIntent currentIntent;
    private DungeonMindStore.Snapshot mindSnapshot;
    private DungeonCognitionGate.Signal lastSignal;
    private int lastBrainTurn;

    private DungeonBoardView boardView;
    private TextView titleStatusView;
    private TextView metricsView;
    private ProgressBar hpBar;
    private TextView brainBadgeView;
    private TextView intentView;
    private TextView summaryView;
    private TextView actionView;
    private Button npc1Button;
    private Button npc2Button;
    private Button pauseButton;
    private Button speedButton;

    private boolean running;
    private boolean paused;
    private int speedIndex = 1;
    private boolean brainThinking;
    private String pendingTrigger = "";
    private String brainState = DungeonMindStore.STATE_LOCAL;
    private String brainError = "";
    private String activeBrainNpcId = "";
    private int activeBrainFloor = -1;

    private JSONArray liveStages = new JSONArray();
    private AlertDialog mindDialog;
    private LinearLayout mindDialogContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        dungeonStore = new DungeonStore(this);
        mindStore = new DungeonMindStore(this);
        brainRuntime = new DungeonBrainRuntime(this);
        apiKeyStore = new SecureApiKeyStore(this);
        modelSettingsStore = new ModelSettingsStore(this);
        setContentView(buildContent());
        loadSelectedNpc();
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        handler.removeCallbacks(turnTask);
        scheduleNextTurn();
        if (state != null) {
            String reason = currentIntent == null || !currentIntent.isBrain()
                    ? DungeonCognitionGate.FLOOR_START
                    : DungeonCognitionGate.reason(lastSignal,
                    DungeonCognitionGate.snapshot(state), lastBrainTurn);
            if (reason == null || reason.isEmpty()) {
                if (state.turn - lastBrainTurn >= DungeonCognitionGate.PERIODIC_TURNS) {
                    reason = DungeonCognitionGate.PERIODIC;
                }
            }
            if (reason != null && !reason.isEmpty()) requestBrain(reason);
        }
    }

    @Override
    protected void onPause() {
        running = false;
        handler.removeCallbacks(turnTask);
        persistCurrent();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(turnTask);
        if (mindDialog != null) mindDialog.dismiss();
        super.onDestroy();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 9, 16));
        applySafeInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(2), 0, dp(6));

        TextView title = new TextView(this);
        title.setText("NPCBrain");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView subtitle = new TextView(this);
        subtitle.setText("COGNITIVE DUNGEON");
        subtitle.setTextColor(Color.rgb(119, 151, 184));
        subtitle.setTextSize(10);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(subtitle);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button conversation = tabButton("会話", false);
        conversation.setOnClickListener(v -> openConversation());
        tabs.addView(conversation, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button npcStatus = tabButton("NPC状況", false);
        npcStatus.setOnClickListener(v -> openNpcStatus());
        LinearLayout.LayoutParams npcStatusParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        npcStatusParams.leftMargin = dp(6);
        tabs.addView(npcStatus, npcStatusParams);

        Button dungeon = tabButton("ダンジョン", true);
        dungeon.setEnabled(false);
        LinearLayout.LayoutParams dungeonParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        dungeonParams.leftMargin = dp(6);
        tabs.addView(dungeon, dungeonParams);
        root.addView(tabs);

        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setPadding(0, dp(7), 0, dp(7));
        npc1Button = selectorButton("NPC1", true);
        npc2Button = selectorButton("NPC2", false);
        npc1Button.setOnClickListener(v -> selectNpc("npc1"));
        npc2Button.setOnClickListener(v -> selectNpc("npc2"));
        selector.addView(npc1Button, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams n2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        n2.leftMargin = dp(7);
        selector.addView(npc2Button, n2);
        root.addView(selector);

        LinearLayout hud = new LinearLayout(this);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setPadding(dp(12), dp(9), dp(12), dp(9));
        hud.setBackground(cardBackground(Color.rgb(15, 25, 38), Color.rgb(37, 62, 88), 14));

        LinearLayout hudTop = new LinearLayout(this);
        hudTop.setOrientation(LinearLayout.HORIZONTAL);
        hudTop.setGravity(Gravity.CENTER_VERTICAL);
        titleStatusView = new TextView(this);
        titleStatusView.setTextColor(Color.rgb(234, 243, 255));
        titleStatusView.setTextSize(15);
        titleStatusView.setTypeface(Typeface.DEFAULT_BOLD);
        hudTop.addView(titleStatusView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        brainBadgeView = new TextView(this);
        brainBadgeView.setTextSize(10);
        brainBadgeView.setTypeface(Typeface.DEFAULT_BOLD);
        brainBadgeView.setGravity(Gravity.CENTER);
        brainBadgeView.setPadding(dp(9), dp(5), dp(9), dp(5));
        hudTop.addView(brainBadgeView);
        hud.addView(hudTop);

        hpBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        hpBar.setMax(100);
        hpBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(33, 44, 56)));
        hpBar.setProgressTintList(ColorStateList.valueOf(Color.rgb(76, 192, 130)));
        LinearLayout.LayoutParams hpParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        hpParams.topMargin = dp(7);
        hud.addView(hpBar, hpParams);

        metricsView = new TextView(this);
        metricsView.setTextColor(Color.rgb(157, 181, 207));
        metricsView.setTextSize(11);
        LinearLayout.LayoutParams metricParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metricParams.topMargin = dp(5);
        hud.addView(metricsView, metricParams);
        root.addView(hud);

        boardView = new DungeonBoardView(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardParams.topMargin = dp(7);
        boardParams.bottomMargin = dp(7);
        root.addView(boardView, boardParams);

        LinearLayout mindPanel = new LinearLayout(this);
        mindPanel.setOrientation(LinearLayout.VERTICAL);
        mindPanel.setPadding(dp(12), dp(9), dp(12), dp(10));
        mindPanel.setBackground(cardBackground(Color.rgb(12, 20, 31), Color.rgb(35, 55, 76), 14));

        intentView = new TextView(this);
        intentView.setTextColor(Color.rgb(224, 237, 252));
        intentView.setTextSize(14);
        intentView.setTypeface(Typeface.DEFAULT_BOLD);
        mindPanel.addView(intentView);

        summaryView = new TextView(this);
        summaryView.setTextColor(Color.rgb(159, 186, 216));
        summaryView.setTextSize(11);
        summaryView.setMaxLines(2);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(3);
        mindPanel.addView(summaryView, summaryParams);

        actionView = new TextView(this);
        actionView.setTextColor(Color.rgb(199, 214, 232));
        actionView.setTextSize(11);
        actionView.setMaxLines(1);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(4);
        mindPanel.addView(actionView, actionParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, 0);
        pauseButton = controlButton("一時停止");
        pauseButton.setOnClickListener(v -> togglePause());
        controls.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        speedButton = controlButton("速度 1×");
        speedButton.setOnClickListener(v -> cycleSpeed());
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        speedParams.leftMargin = dp(7);
        controls.addView(speedButton, speedParams);
        Button mindButton = controlButton("脳内を見る");
        mindButton.setOnClickListener(v -> showMindDialog());
        LinearLayout.LayoutParams mindParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        mindParams.leftMargin = dp(7);
        controls.addView(mindButton, mindParams);
        mindPanel.addView(controls);
        root.addView(mindPanel);
        return root;
    }

    private void selectNpc(String npcId) {
        if (npcId.equals(selectedNpcId)) return;
        persistCurrent();
        selectedNpcId = npcId;
        npc1Button.setBackground(selectorBackground("npc1".equals(npcId)));
        npc2Button.setBackground(selectorBackground("npc2".equals(npcId)));
        loadSelectedNpc();
        if (running) requestBrain(DungeonCognitionGate.FLOOR_START);
    }

    private void loadSelectedNpc() {
        state = dungeonStore.load(selectedNpcId);
        if (state == null) {
            long seed = System.nanoTime()
                    ^ System.currentTimeMillis()
                    ^ ((long) selectedNpcId.hashCode() << 17);
            state = DungeonGenerator.generate(seed, 1);
        }
        DungeonPerception.refreshExploration(state);
        dungeonStore.save(selectedNpcId, state);

        mindSnapshot = mindStore.load(selectedNpcId);
        if (mindSnapshot != null && mindSnapshot.intent != null
                && mindSnapshot.intent.floor == state.floor) {
            currentIntent = mindSnapshot.intent;
            brainState = mindSnapshot.brainState;
            brainError = mindSnapshot.error;
            lastBrainTurn = currentIntent.turn;
        } else {
            currentIntent = DungeonIntent.localFallback(state, currentTraits(), "初期評価");
            brainState = DungeonMindStore.STATE_LOCAL;
            brainError = "";
            mindSnapshot = new DungeonMindStore.Snapshot(
                    currentIntent, new JSONArray(), new JSONObject(),
                    brainState, "", System.currentTimeMillis());
        }
        lastSignal = DungeonCognitionGate.snapshot(state);
        render();
    }

    private void advanceTurn() {
        if (state == null) return;
        int oldFloor = state.floor;
        state = DungeonEngine.step(state, currentTraits(), currentIntent);
        DungeonPerception.refreshExploration(state);
        dungeonStore.save(selectedNpcId, state);

        if (state.floor != oldFloor) {
            currentIntent = DungeonIntent.localFallback(state, currentTraits(), "新しい階を観察中");
            brainState = DungeonMindStore.STATE_LOCAL;
            brainError = "";
        }

        DungeonCognitionGate.Signal signal = DungeonCognitionGate.snapshot(state);
        String reason = DungeonCognitionGate.reason(lastSignal, signal, lastBrainTurn);
        lastSignal = signal;
        render();
        if (!reason.isEmpty()) requestBrain(reason);
    }

    private void requestBrain(String triggerReason) {
        if (state == null || triggerReason == null || triggerReason.isEmpty()) return;
        if (brainThinking) {
            pendingTrigger = DungeonCognitionGate.mergePending(pendingTrigger, triggerReason);
            return;
        }

        String apiKey;
        try {
            apiKey = apiKeyStore.load().trim();
        } catch (Exception error) {
            applyLocalFallback("APIキー読出し失敗");
            return;
        }
        if (apiKey.isEmpty()) {
            applyLocalFallback("APIキー未設定");
            return;
        }

        DungeonState captured;
        try {
            captured = DungeonState.fromJson(new JSONObject(state.toJson().toString()));
        } catch (Exception error) {
            captured = null;
        }
        if (captured == null) {
            applyLocalFallback("状態コピー失敗");
            return;
        }

        final String npcId = selectedNpcId;
        final int floor = captured.floor;
        final int turn = captured.turn;
        final String effort = modelSettingsStore.reasoningEffort();
        brainThinking = true;
        activeBrainNpcId = npcId;
        activeBrainFloor = floor;
        brainState = DungeonMindStore.STATE_THINKING;
        brainError = "";
        initializeLiveStages();
        render();
        renderMindDialogIfOpen();

        new Thread(() -> {
            try {
                DungeonBrainRuntime.Result result = brainRuntime.run(
                        npcId,
                        captured,
                        triggerReason,
                        apiKey,
                        effort,
                        new DungeonBrainRuntime.Listener() {
                            @Override
                            public void onStageStarted(
                                    String stageId,
                                    String stageLabel,
                                    int current,
                                    int total
                            ) {
                                runOnUiThread(() -> {
                                    if (!npcId.equals(activeBrainNpcId) || floor != activeBrainFloor) return;
                                    markStageStarted(stageId, stageLabel, current, total);
                                    renderMindDialogIfOpen();
                                });
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
                                runOnUiThread(() -> {
                                    if (!npcId.equals(activeBrainNpcId) || floor != activeBrainFloor) return;
                                    markStageCompleted(
                                            stageId, stageLabel, current, total,
                                            summary, confidence, salientFacts, personalityEffect,
                                            effort);
                                    renderMindDialogIfOpen();
                                });
                            }
                        });
                runOnUiThread(() -> finishBrainSuccess(npcId, floor, turn, result));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.toString() : error.getMessage();
                runOnUiThread(() -> finishBrainFailure(npcId, floor, message));
            }
        }, "npcbrain-v048-dungeon-brain").start();
    }

    private void finishBrainSuccess(
            String npcId,
            int floor,
            int requestedTurn,
            DungeonBrainRuntime.Result result
    ) {
        DungeonMindStore.Snapshot snapshot = new DungeonMindStore.Snapshot(
                result.intent,
                result.trace,
                result.cognitiveGraph,
                DungeonMindStore.STATE_BRAIN,
                "",
                System.currentTimeMillis());
        mindStore.save(npcId, snapshot);

        boolean applies = npcId.equals(selectedNpcId)
                && state != null
                && state.floor == floor;
        if (applies) {
            mindSnapshot = snapshot;
            currentIntent = result.intent;
            brainState = DungeonMindStore.STATE_BRAIN;
            brainError = "";
            lastBrainTurn = Math.max(requestedTurn, state.turn);
        }
        completeBrainRequest(npcId, floor);
    }

    private void finishBrainFailure(String npcId, int floor, String error) {
        boolean applies = npcId.equals(selectedNpcId)
                && state != null
                && state.floor == floor;
        if (applies) applyLocalFallback(error == null ? "Brain失敗" : compactError(error));
        completeBrainRequest(npcId, floor);
    }

    private void completeBrainRequest(String npcId, int floor) {
        if (npcId.equals(activeBrainNpcId) && floor == activeBrainFloor) {
            brainThinking = false;
            activeBrainNpcId = "";
            activeBrainFloor = -1;
        }
        render();
        renderMindDialogIfOpen();
        String pending = pendingTrigger;
        pendingTrigger = "";
        if (!pending.isEmpty() && running) requestBrain(pending);
    }

    private void applyLocalFallback(String reason) {
        if (state == null) return;
        currentIntent = DungeonIntent.localFallback(state, currentTraits(), reason);
        brainState = DungeonMindStore.STATE_LOCAL;
        brainError = reason == null ? "" : reason;
        lastBrainTurn = state.turn;
        mindSnapshot = new DungeonMindStore.Snapshot(
                currentIntent,
                mindSnapshot == null ? new JSONArray() : mindSnapshot.trace,
                mindSnapshot == null ? new JSONObject() : mindSnapshot.cognitiveGraph,
                brainState,
                brainError,
                System.currentTimeMillis());
        mindStore.save(selectedNpcId, mindSnapshot);
        render();
    }

    private void togglePause() {
        paused = !paused;
        pauseButton.setText(paused ? "再開" : "一時停止");
        render();
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % TURN_INTERVALS.length;
        speedButton.setText("速度 " + SPEED_LABELS[speedIndex]);
        handler.removeCallbacks(turnTask);
        if (running) scheduleNextTurn();
    }

    private void scheduleNextTurn() {
        handler.removeCallbacks(turnTask);
        if (running) handler.postDelayed(turnTask, TURN_INTERVALS[speedIndex]);
    }

    private void persistCurrent() {
        if (state != null) dungeonStore.save(selectedNpcId, state);
        if (mindSnapshot != null) mindStore.save(selectedNpcId, mindSnapshot);
    }

    private void render() {
        if (state == null || boardView == null) return;
        CharacterStateStore character = currentCharacterStore();
        int hpPercent = (int) Math.round(100.0 * state.hp / Math.max(1, state.maxHp));
        titleStatusView.setText(character.displayName() + "   " + state.floor + "F");
        hpBar.setProgress(Math.max(0, Math.min(100, hpPercent)));
        hpBar.setProgressTintList(ColorStateList.valueOf(
                hpPercent <= 25 ? Color.rgb(222, 76, 87)
                        : hpPercent <= 50 ? Color.rgb(224, 157, 68)
                        : Color.rgb(76, 192, 130)));
        String hpLabel = hpPercent <= 25 ? " · LOW HP" : "";
        metricsView.setText("HP " + state.hp + "/" + state.maxHp + hpLabel
                + "   ·   Turn " + state.turn
                + "   ·   敵 " + state.aliveEnemyCount()
                + "   ·   階段 " + (DungeonPerception.stairsKnown(state) ? "発見" : "未発見"));

        renderBrainBadge();
        DungeonIntent intent = currentIntent == null
                ? DungeonIntent.localFallback(state, currentTraits(), "待機") : currentIntent;
        int confidence = (int) Math.round(intent.confidence * 100.0);
        intentView.setText("戦術: " + DungeonIntent.modeLabel(intent.mode)
                + (intent.isBrain() ? "  ·  Brain " + confidence + "%" : "  ·  Local"));
        String summary = intent.summary;
        if (brainThinking) summary = "10段階認知を更新中。直近の戦術で自律行動を継続します。";
        if (summary == null || summary.trim().isEmpty()) summary = "状況を評価しています。";
        summaryView.setText(summary);
        actionView.setText("直近: " + state.lastAction + (paused ? "   ·   PAUSED" : ""));
        pauseButton.setText(paused ? "再開" : "一時停止");
        speedButton.setText("速度 " + SPEED_LABELS[speedIndex]);

        boardView.setState(state);
        boardView.setContentDescription(state.floor + "F、HP " + state.hp + "/" + state.maxHp
                + "、可視敵 " + DungeonPerception.visibleEnemyIds(state).size()
                + "、階段 " + (DungeonPerception.stairsKnown(state) ? "発見" : "未発見")
                + "、Brain " + brainState);
    }

    private void renderBrainBadge() {
        if (brainThinking) {
            brainBadgeView.setText("THINKING · 脳内更新");
            brainBadgeView.setTextColor(Color.rgb(220, 239, 255));
            brainBadgeView.setBackground(cardBackground(
                    Color.rgb(35, 85, 132), Color.rgb(72, 139, 202), 12));
            return;
        }
        if (DungeonMindStore.STATE_BRAIN.equals(brainState)) {
            brainBadgeView.setText("BRAIN · ACTIVE");
            brainBadgeView.setTextColor(Color.rgb(217, 252, 231));
            brainBadgeView.setBackground(cardBackground(
                    Color.rgb(27, 90, 61), Color.rgb(65, 148, 104), 12));
        } else {
            brainBadgeView.setText("LOCAL FALLBACK");
            brainBadgeView.setTextColor(Color.rgb(255, 226, 188));
            brainBadgeView.setBackground(cardBackground(
                    Color.rgb(92, 61, 28), Color.rgb(160, 111, 50), 12));
        }
    }

    private void initializeLiveStages() {
        liveStages = new JSONArray();
        String[] ids = BrainEngine.stageIds();
        for (int i = 0; i < ids.length; i++) {
            JSONObject stage = new JSONObject();
            try {
                stage.put("stage_id", ids[i]);
                stage.put("stage_label", BrainEngine.stageLabel(ids[i]));
                stage.put("status", "waiting");
                stage.put("current", i + 1);
                stage.put("total", ids.length);
            } catch (Exception ignored) {
            }
            liveStages.put(stage);
        }
    }

    private void markStageStarted(String id, String label, int current, int total) {
        JSONObject stage = findLiveStage(id);
        if (stage == null) return;
        try {
            stage.put("stage_label", label);
            stage.put("status", "running");
            stage.put("current", current);
            stage.put("total", total);
        } catch (Exception ignored) {
        }
    }

    private void markStageCompleted(
            String id,
            String label,
            int current,
            int total,
            String summary,
            double confidence,
            JSONArray facts,
            String personalityEffect,
            String effort
    ) {
        JSONObject stage = findLiveStage(id);
        if (stage == null) return;
        try {
            stage.put("stage_label", label);
            stage.put("status", "done");
            stage.put("current", current);
            stage.put("total", total);
            stage.put("summary", summary == null ? "" : summary);
            stage.put("confidence", confidence);
            stage.put("salient_facts", facts == null ? new JSONArray() : new JSONArray(facts.toString()));
            stage.put("personality_effect", personalityEffect == null ? "" : personalityEffect);
            stage.put("model", OpenAiClient.MODEL);
            stage.put("reasoning_effort", effort);
        } catch (Exception ignored) {
        }
    }

    private JSONObject findLiveStage(String id) {
        for (int i = 0; i < liveStages.length(); i++) {
            JSONObject stage = liveStages.optJSONObject(i);
            if (stage != null && id.equals(stage.optString("stage_id"))) return stage;
        }
        return null;
    }

    private void showMindDialog() {
        mindDialogContent = new LinearLayout(this);
        mindDialogContent.setOrientation(LinearLayout.VERTICAL);
        mindDialogContent.setPadding(dp(16), dp(6), dp(16), dp(18));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(mindDialogContent);
        mindDialog = new AlertDialog.Builder(this)
                .setTitle(currentCharacterStore().displayName() + " のダンジョン脳内")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .create();
        mindDialog.setOnDismissListener(dialog -> {
            mindDialog = null;
            mindDialogContent = null;
        });
        mindDialog.show();
        renderMindDialogIfOpen();
    }

    private void renderMindDialogIfOpen() {
        if (mindDialog == null || mindDialogContent == null) return;
        mindDialogContent.removeAllViews();
        TextView stateNote = new TextView(this);
        String note;
        if (brainThinking) {
            note = "10段階の公開用認知診断を更新中。逐語的なchain-of-thoughtではありません。";
        } else if (DungeonMindStore.STATE_BRAIN.equals(brainState)) {
            note = "直近のBrain intent: " + DungeonIntent.modeLabel(
                    currentIntent == null ? DungeonIntent.HOLD : currentIntent.mode);
        } else {
            note = "LOCAL FALLBACK: " + (brainError.isEmpty() ? "Brain未利用" : brainError);
        }
        stateNote.setText(note);
        stateNote.setTextColor(Color.rgb(76, 82, 94));
        stateNote.setTextSize(12);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.bottomMargin = dp(10);
        mindDialogContent.addView(stateNote, noteParams);

        JSONArray stages = brainThinking ? liveStages
                : mindSnapshot == null ? new JSONArray() : mindSnapshot.trace;
        if (stages.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("保存済みのBrain traceはまだありません。");
            empty.setTextSize(13);
            mindDialogContent.addView(empty);
            return;
        }
        for (int i = 0; i < stages.length(); i++) {
            JSONObject stage = stages.optJSONObject(i);
            if (stage != null) mindDialogContent.addView(createTraceCard(stage, i, stages.length()));
        }
    }

    private View createTraceCard(JSONObject stage, int index, int total) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground(Color.rgb(246, 248, 251), Color.rgb(219, 224, 231), 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(7);
        card.setLayoutParams(cp);

        String status = stage.optString("status", "done");
        String statusLabel = "running".equals(status) ? " · 思考中"
                : "waiting".equals(status) ? " · 待機" : "";
        TextView title = new TextView(this);
        title.setText((stage.optInt("current", index + 1)) + "/"
                + stage.optInt("total", total) + "  "
                + stage.optString("stage_label", "認知") + statusLabel);
        title.setTextColor(Color.rgb(31, 39, 50));
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        String summary = stage.optString("summary", "").trim();
        if (!summary.isEmpty()) {
            TextView body = new TextView(this);
            body.setText(summary);
            body.setTextColor(Color.rgb(55, 62, 72));
            body.setTextSize(12);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(4);
            card.addView(body, bp);
        }

        String effect = stage.optString("personality_effect", "").trim();
        if (!effect.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("人格影響: " + effect);
            e.setTextColor(Color.rgb(74, 88, 117));
            e.setTextSize(10);
            card.addView(e);
        }
        if ("done".equals(status) || !summary.isEmpty()) {
            int confidence = (int) Math.round(Math.max(0.0,
                    Math.min(1.0, stage.optDouble("confidence", 0.0))) * 100.0);
            TextView meta = new TextView(this);
            meta.setText(String.format(Locale.JAPAN, "信頼度 %d%% · %s / %s",
                    confidence,
                    stage.optString("model", OpenAiClient.MODEL),
                    ModelSettingsStore.displayLabel(stage.optString(
                            "reasoning_effort", modelSettingsStore.reasoningEffort()))));
            meta.setTextColor(Color.rgb(120, 126, 136));
            meta.setTextSize(9);
            card.addView(meta);
        }
        return card;
    }

    private DungeonPersonalityPolicy.Traits currentTraits() {
        CharacterStateStore character = currentCharacterStore();
        return new DungeonPersonalityPolicy.Traits(
                character.traitPercent(CharacterStateStore.extraversionKey()),
                character.traitPercent(CharacterStateStore.neuroticismKey()),
                character.traitPercent(CharacterStateStore.agreeablenessKey()),
                character.traitPercent(CharacterStateStore.conscientiousnessKey()),
                character.traitPercent(CharacterStateStore.opennessKey()));
    }

    private CharacterStateStore currentCharacterStore() {
        Context app = getApplicationContext();
        Context context = "npc2".equals(selectedNpcId)
                ? new NpcStorageContext(app, "npc2") : app;
        return new CharacterStateStore(context);
    }

    private void openConversation() {
        Intent intent = new Intent(this, DemoActivityV032.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openNpcStatus() {
        Intent intent = new Intent(this, NpcStatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private Button tabButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? Color.WHITE : Color.rgb(181, 199, 221));
        button.setBackground(cardBackground(
                selected ? Color.rgb(37, 88, 151) : Color.rgb(18, 30, 44),
                selected ? Color.rgb(72, 130, 199) : Color.rgb(34, 51, 70), 11));
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
        button.setBackground(selectorBackground(selected));
        button.setMinHeight(dp(48));
        return button;
    }

    private GradientDrawable selectorBackground(boolean selected) {
        return cardBackground(
                selected ? Color.rgb(42, 91, 156) : Color.rgb(20, 32, 47),
                selected ? Color.rgb(80, 139, 207) : Color.rgb(42, 59, 77), 11);
    }

    private Button controlButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.rgb(225, 238, 253));
        button.setBackground(cardBackground(
                Color.rgb(26, 43, 61), Color.rgb(50, 76, 102), 11));
        button.setMinHeight(dp(48));
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private GradientDrawable cardBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private String compactError(String value) {
        String text = value == null ? "Brain失敗" : value.replace('\n', ' ').trim();
        if (text.length() > 72) text = text.substring(0, 72) + "…";
        return text;
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(5, 9, 16));
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    }

    private void applySafeInsets(View root) {
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
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
                view.setPadding(dp(12) + left, dp(8) + top,
                        dp(12) + right, dp(8) + bottom);
                return insets;
            });
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
