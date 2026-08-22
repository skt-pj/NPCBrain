package com.sktpj.npcbrain;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.TextView;

public final class DungeonActivity extends Activity {
    private static final long TURN_INTERVAL_MS = 700L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable turnTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            advanceTurn();
            handler.postDelayed(this, TURN_INTERVAL_MS);
        }
    };

    private DungeonStore dungeonStore;
    private String selectedNpcId = "npc1";
    private DungeonState state;
    private DungeonBoardView boardView;
    private TextView statusView;
    private TextView personalityView;
    private TextView actionView;
    private Button npc1Button;
    private Button npc2Button;
    private boolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        dungeonStore = new DungeonStore(this);
        setContentView(buildContent());
        loadSelectedNpc();
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        handler.removeCallbacks(turnTask);
        handler.postDelayed(turnTask, TURN_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        running = false;
        handler.removeCallbacks(turnTask);
        if (state != null) dungeonStore.save(selectedNpcId, state);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(turnTask);
        super.onDestroy();
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
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView subtitle = new TextView(this);
        subtitle.setText("自律ダンジョン");
        subtitle.setTextColor(Color.rgb(132, 157, 190));
        subtitle.setTextSize(11);
        header.addView(subtitle);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button conversation = tabButton("会話", false);
        conversation.setOnClickListener(v -> openConversation());
        tabs.addView(conversation, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button npcStatus = tabButton("NPC状況", false);
        npcStatus.setOnClickListener(v -> openNpcStatus());
        LinearLayout.LayoutParams npcStatusParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        npcStatusParams.leftMargin = dp(8);
        tabs.addView(npcStatus, npcStatusParams);

        Button dungeon = tabButton("ダンジョン", true);
        dungeon.setEnabled(false);
        LinearLayout.LayoutParams dungeonParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        dungeonParams.leftMargin = dp(8);
        tabs.addView(dungeon, dungeonParams);
        root.addView(tabs);

        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setPadding(0, dp(8), 0, dp(7));
        npc1Button = selectorButton("NPC1", true);
        npc2Button = selectorButton("NPC2", false);
        npc1Button.setOnClickListener(v -> selectNpc("npc1"));
        npc2Button.setOnClickListener(v -> selectNpc("npc2"));
        selector.addView(npc1Button, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams n2 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        n2.leftMargin = dp(8);
        selector.addView(npc2Button, n2);
        root.addView(selector);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(225, 236, 249));
        statusView.setTextSize(14);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

        personalityView = new TextView(this);
        personalityView.setTextColor(Color.rgb(137, 162, 193));
        personalityView.setTextSize(10);
        personalityView.setMaxLines(2);
        root.addView(personalityView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        actionView = new TextView(this);
        actionView.setTextColor(Color.rgb(191, 211, 235));
        actionView.setTextSize(12);
        actionView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        actionParams.topMargin = dp(3);
        root.addView(actionView, actionParams);

        boardView = new DungeonBoardView(this);
        boardView.setContentDescription("ランダム生成されたターン制ダンジョン。青い丸がNPC、赤い四角が敵、>が階段");
        root.addView(boardView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView legend = new TextView(this);
        legend.setText("青=NPC  赤=敵  >=階段   1行動ごとに敵も1ターン");
        legend.setTextColor(Color.rgb(126, 151, 181));
        legend.setTextSize(10);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(0, dp(6), 0, 0);
        root.addView(legend);
        return root;
    }

    private void selectNpc(String npcId) {
        if (npcId.equals(selectedNpcId)) return;
        if (state != null) dungeonStore.save(selectedNpcId, state);
        selectedNpcId = npcId;
        npc1Button.setBackgroundColor("npc1".equals(npcId)
                ? Color.rgb(42, 91, 156) : Color.rgb(25, 39, 58));
        npc2Button.setBackgroundColor("npc2".equals(npcId)
                ? Color.rgb(42, 91, 156) : Color.rgb(25, 39, 58));
        loadSelectedNpc();
    }

    private void loadSelectedNpc() {
        state = dungeonStore.load(selectedNpcId);
        if (state == null) {
            long seed = System.nanoTime()
                    ^ System.currentTimeMillis()
                    ^ ((long) selectedNpcId.hashCode() << 17);
            state = DungeonGenerator.generate(seed, 1);
            dungeonStore.save(selectedNpcId, state);
        }
        render();
    }

    private void advanceTurn() {
        if (state == null) return;
        DungeonPersonalityPolicy.Traits traits = currentTraits();
        state = DungeonEngine.step(state, traits);
        dungeonStore.save(selectedNpcId, state);
        render();
    }

    private void render() {
        if (state == null || boardView == null) return;
        CharacterStateStore character = currentCharacterStore();
        statusView.setText(character.displayName()
                + "  ·  " + state.floor + "F"
                + "  ·  HP " + state.hp + "/" + state.maxHp
                + "  ·  Turn " + state.turn
                + "  ·  敵 " + state.aliveEnemyCount());
        DungeonPersonalityPolicy.Traits traits = currentTraits();
        personalityView.setText("人格で自律移動  外向性 " + traits.extraversion
                + " / 神経症 " + traits.neuroticism
                + " / 協調性 " + traits.agreeableness
                + " / 誠実性 " + traits.conscientiousness
                + " / 開放性 " + traits.openness);
        actionView.setText("行動: " + state.lastAction);
        boardView.setState(state);
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
        button.setTextColor(selected ? Color.WHITE : Color.rgb(188, 203, 222));
        button.setBackgroundColor(selected ? Color.rgb(42, 91, 156) : Color.rgb(21, 33, 49));
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
        return button;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
