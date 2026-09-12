package com.sktpj.npcbrain;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Legacy route kept as a canonical, read-only eight-NPC dungeon monitor. */
public final class IndividualDungeonActivity extends Activity {
    private static final long REFRESH_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Panel> panels = new ArrayList<>();
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            renderPanels();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private WorldQueryServiceV210 query;
    private NpcRegistryStore registry;

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
        setContentView(buildContent());
        renderPanels();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackgroundColor(AppUiTheme.APP_BACKGROUND);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("ONE WORLD · DUNGEON", 9, Color.rgb(116, 156, 197), true));
        heading.addView(text("最大8人 同時監視", 18, Color.WHITE, true));
        header.addView(heading, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button back = modeButton("戻る");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(82), dp(42)));
        root.addView(header);

        TextView note = text(
                "Canonical Worldの同じダンジョン状態を同時観測します。この画面は生成・ターン進行・保存を行いません。",
                10, AppUiTheme.APP_MUTED, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(4);
        noteParams.bottomMargin = dp(4);
        root.addView(note, noteParams);

        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(false);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int index = 0; index < IndividualDungeonPolicy.MAX_SLOTS; index++) {
            Panel panel = new Panel();
            panels.add(panel);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(220);
            params.columnSpec = GridLayout.spec(index % 2, 1f);
            int margin = dp(3);
            params.setMargins(margin, margin, margin, margin);
            grid.addView(panel.root, params);
        }
        return root;
    }

    private void renderPanels() {
        if (query == null || registry == null) return;
        List<String> present = new ArrayList<>();
        for (String npcId : registry.activeNpcIds()) {
            JSONObject npc = query.snapshot(npcId).optJSONObject("npc");
            if (npc != null && npc.optBoolean("dungeon_present", false)
                    && !npc.optBoolean("dead", false)) {
                present.add(npcId);
            }
        }
        for (int i = 0; i < panels.size(); i++) {
            if (i >= present.size()) {
                panels.get(i).bindEmpty(i + 1);
                continue;
            }
            String npcId = present.get(i);
            JSONObject snapshot = query.snapshot(npcId);
            JSONObject npc = snapshot.optJSONObject("npc");
            DungeonState state = npc == null ? null : DungeonState.fromJson(npc.optJSONObject("dungeon_actor"));
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
            String status = state == null
                    ? "開始待ち · WORLD REV " + snapshot.optLong("revision", 0L)
                    : "HP " + state.hp + "/" + state.maxHp + " · " + state.floor + "F · T" + state.turn;
            panels.get(i).bind(character.displayName(), status, state);
        }
    }

    private final class Panel {
        final LinearLayout root;
        final TextView name;
        final TextView status;
        final DungeonBoardView board;

        Panel() {
            root = new LinearLayout(IndividualDungeonActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(6), dp(5), dp(6), dp(6));
            root.setBackground(panelBackground());
            name = text("", 11, AppUiTheme.APP_TEXT, true);
            root.addView(name);
            status = text("", 8, AppUiTheme.APP_MUTED, false);
            root.addView(status);
            board = new DungeonBoardView(IndividualDungeonActivity.this);
            board.setClickable(false);
            board.setFocusable(false);
            LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            boardParams.topMargin = dp(3);
            root.addView(board, boardParams);
        }

        void bind(String displayName, String stateText, DungeonState state) {
            name.setText(displayName);
            status.setText(stateText);
            board.setState(state);
        }

        void bindEmpty(int slot) {
            name.setText("空き " + slot);
            status.setText("探索中NPCなし");
            board.setState(null);
        }
    }

    private Button modeButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(10);
        button.setTextColor(Color.WHITE);
        button.setBackground(panelBackground());
        return button;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(9));
        bg.setColor(AppUiTheme.APP_SURFACE);
        bg.setStroke(dp(1), AppUiTheme.APP_BORDER);
        return bg;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
