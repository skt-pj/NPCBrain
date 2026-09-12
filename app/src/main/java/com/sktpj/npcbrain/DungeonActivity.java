package com.sktpj.npcbrain;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Canonical dungeon observer.
 *
 * This Activity never generates a dungeon, advances a turn, or persists DungeonState. The sole
 * owner of dungeon simulation is WorldSimulationDriverV210 -> DungeonDomainAdapterV210 ->
 * WorldKernelV210. Opening/resuming/selecting this screen only reads WorldQueryServiceV210.
 */
public final class DungeonActivity extends Activity {
    private static final long REFRESH_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!observing) return;
            renderSnapshot();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private WorldQueryServiceV210 query;
    private NpcRegistryStore registry;
    private String selectedNpcId = "";
    private boolean observing;
    private long lastRevision = Long.MIN_VALUE;

    private LinearLayout selector;
    private TextView titleView;
    private TextView worldView;
    private TextView stateView;
    private TextView actionView;
    private DungeonBoardView boardView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registry = new NpcRegistryStore(this);
        query = NPCBrainApplication.worldQuery();
        if (query == null) {
            WorldKernelV210 kernel = WorldKernelV210.get(getApplicationContext());
            new LegacyWorldImporterV210(getApplicationContext(), kernel.database()).importIfNeeded();
            query = new WorldQueryServiceV210(kernel.database());
        }
        List<String> active = registry.activeNpcIds();
        if (!active.isEmpty()) selectedNpcId = active.get(0);
        setContentView(buildContent());
        rebuildSelector();
        renderSnapshot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        observing = true;
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        observing = false;
        handler.removeCallbacks(refreshTask);
        if (boardView != null) boardView.clearEffects();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        observing = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(5, 9, 16));

        TextView header = new TextView(this);
        header.setText("DUNGEON · WORLD OBSERVER");
        header.setTextColor(Color.WHITE);
        header.setTextSize(19);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(header);

        TextView note = new TextView(this);
        note.setText("進行主体: Canonical World Runtime  /  この画面は観測のみ");
        note.setTextColor(Color.rgb(126, 156, 186));
        note.setTextSize(11);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(3);
        noteParams.bottomMargin = dp(8);
        root.addView(note, noteParams);

        HorizontalScrollView selectorScroll = new HorizontalScrollView(this);
        selectorScroll.setHorizontalScrollBarEnabled(false);
        selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selectorScroll.addView(selector);
        root.addView(selectorScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground(Color.rgb(15, 25, 38), Color.rgb(37, 62, 88), 14));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(8);
        root.addView(card, cardParams);

        titleView = label(15, Color.rgb(236, 244, 255), true);
        card.addView(titleView);
        worldView = label(11, Color.rgb(150, 177, 204), false);
        card.addView(worldView);
        stateView = label(12, Color.rgb(205, 222, 240), false);
        card.addView(stateView);
        actionView = label(11, Color.rgb(151, 193, 166), false);
        card.addView(actionView);

        boardView = new DungeonBoardView(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardParams.topMargin = dp(8);
        root.addView(boardView, boardParams);
        return root;
    }

    private void rebuildSelector() {
        if (selector == null) return;
        selector.removeAllViews();
        List<String> active = registry.activeNpcIds();
        if (active.isEmpty()) {
            selectedNpcId = "";
            TextView empty = label(12, Color.rgb(150, 170, 190), false);
            empty.setText("観測できるNPCがいません");
            selector.addView(empty);
            return;
        }
        if (!active.contains(selectedNpcId)) selectedNpcId = active.get(0);
        for (String npcId : active) {
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
            String name = character.displayName();
            if (name == null || name.trim().isEmpty() || "NPC".equals(name.trim())) name = npcId;
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(name);
            button.setTextSize(12);
            button.setTextColor(Color.WHITE);
            button.setBackground(cardBackground(
                    npcId.equals(selectedNpcId) ? Color.rgb(42, 89, 121) : Color.rgb(22, 35, 50),
                    Color.rgb(58, 82, 105), 12));
            button.setOnClickListener(v -> selectNpc(npcId));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
            p.rightMargin = dp(6);
            selector.addView(button, p);
        }
    }

    private void selectNpc(String npcId) {
        String id = NpcId.of(npcId).value();
        if (id.equals(selectedNpcId)) return;
        selectedNpcId = id;
        lastRevision = Long.MIN_VALUE;
        rebuildSelector();
        renderSnapshot();
    }

    private void renderSnapshot() {
        if (query == null || selectedNpcId.isEmpty()) {
            showNoNpc();
            return;
        }
        JSONObject snapshot = query.snapshot(selectedNpcId);
        long revision = snapshot.optLong("revision", 0L);
        // A selection change forces a render; otherwise avoid rebuilding an identical frame.
        if (revision == lastRevision && boardView != null) return;
        lastRevision = revision;

        JSONObject npc = snapshot.optJSONObject("npc");
        if (npc == null) {
            showNoNpc();
            return;
        }
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, selectedNpcId));
        String name = character.displayName();
        titleView.setText(name + (npc.optBoolean("dead", false) ? " · 死亡" : ""));
        long worldTime = snapshot.optLong("world_time_ms", 0L);
        worldView.setText("World rev " + revision + " · " + formatWorldTime(worldTime));

        boolean present = npc.optBoolean("dungeon_present", false) && !npc.optBoolean("dead", false);
        JSONObject actorJson = npc.optJSONObject("dungeon_actor");
        DungeonState dungeonState = actorJson == null ? null : DungeonState.fromJson(actorJson);
        if (!present || dungeonState == null) {
            boardView.setState(null);
            String location = npc.optString("location", "unknown");
            String activity = npc.optString("activity", "idle");
            String goal = npc.optString("goal", "");
            stateView.setText("ダンジョン外 · " + location + " · " + activity);
            actionView.setText(goal.isEmpty() ? "現在の目的: —" : "現在の目的: " + goal);
            return;
        }

        boardView.setState(dungeonState);
        int hpPercent = dungeonState.maxHp <= 0 ? 0
                : (int) Math.round(100.0 * dungeonState.hp / dungeonState.maxHp);
        stateView.setText("" + dungeonState.floor + "F · turn " + dungeonState.turn
                + " · HP " + dungeonState.hp + "/" + dungeonState.maxHp
                + " (" + hpPercent + "%)");
        String action = dungeonState.lastAction == null ? "" : dungeonState.lastAction.trim();
        actionView.setText(action.isEmpty() ? "行動: 観測待ち" : "行動: " + action);
    }

    private void showNoNpc() {
        if (titleView != null) titleView.setText("NPCなし");
        if (worldView != null) worldView.setText("Canonical Worldを観測できません");
        if (stateView != null) stateView.setText("");
        if (actionView != null) actionView.setText("");
        if (boardView != null) boardView.setState(null);
    }

    private TextView label(float size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private static String formatWorldTime(long millis) {
        if (millis <= 0L) return "world time 未初期化";
        return new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(millis));
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
}
