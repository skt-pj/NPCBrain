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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IndividualDungeonActivity extends Activity {
    private static final long REFRESH_MS = 400L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Panel> panels = new ArrayList<>();
    private DungeonPresenceStore presenceStore;
    private DungeonRosterStore rosterStore;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            renderPanels();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenceStore = new DungeonPresenceStore(this);
        rosterStore = new DungeonRosterStore(this);
        setContentView(buildContent());
        renderPanels();
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

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackgroundColor(Color.rgb(5, 9, 16));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("各自ダンジョン", 18, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button party = modeButton("パーティ", false);
        party.setOnClickListener(v -> finish());
        header.addView(party, new LinearLayout.LayoutParams(dp(100), dp(42)));

        Button individual = modeButton("各自 8画面", true);
        individual.setEnabled(false);
        LinearLayout.LayoutParams individualParams = new LinearLayout.LayoutParams(dp(112), dp(42));
        individualParams.leftMargin = dp(6);
        header.addView(individual, individualParams);
        root.addView(header);

        TextView note = text(
                "同じダンジョン世界をNPCごとの視点で同時監視 · party外の単独探索も表示",
                10,
                Color.rgb(137, 161, 187),
                false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(3);
        noteParams.bottomMargin = dp(5);
        root.addView(note, noteParams);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setRowCount(4);
        grid.setUseDefaultMargins(false);
        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int index = 0; index < IndividualDungeonPolicy.MAX_SLOTS; index++) {
            Panel panel = new Panel(index);
            panels.add(panel);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(index % 2, 1f);
            params.rowSpec = GridLayout.spec(index / 2, 1f);
            int margin = dp(3);
            params.setMargins(margin, margin, margin, margin);
            grid.addView(panel.root, params);
        }
        return root;
    }

    private void renderPanels() {
        List<String> ids = IndividualDungeonPolicy.visibleNpcIds(
                presenceStore.activePresentNpcIds());
        List<String> party = rosterStore.activeNpcIds();
        for (int i = 0; i < panels.size(); i++) {
            if (i >= ids.size()) {
                panels.get(i).bindEmpty(i + 1);
                continue;
            }
            String npcId = ids.get(i);
            DungeonMonitorSnapshot snapshot = DungeonMonitorSnapshot.load(this, npcId);
            CharacterStateStore character =
                    new CharacterStateStore(NpcContexts.storage(this, npcId));
            String name = character.displayName();
            if (name == null || name.trim().isEmpty() || "NPC".equals(name.trim())) {
                name = npcId.toUpperCase(Locale.US);
            }
            DungeonState state = snapshot.state;
            String mode = party.contains(npcId) ? "PARTY" : "SOLO";
            String status = state == null
                    ? mode + " · 開始待ち"
                    : "HP " + state.hp + "/" + state.maxHp
                    + " · " + state.floor + "F"
                    + " · T" + state.turn
                    + " · " + mode;
            panels.get(i).bind(name, status, snapshot);
        }
    }

    private final class Panel {
        final LinearLayout root;
        final TextView name;
        final TextView status;
        final DungeonMiniMapView map;

        Panel(int index) {
            root = new LinearLayout(IndividualDungeonActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(5), dp(4), dp(5), dp(5));
            root.setBackground(panelBackground());

            name = text("", 11, Color.rgb(232, 240, 250), true);
            name.setMaxLines(1);
            root.addView(name, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            status = text("", 8, Color.rgb(151, 178, 204), false);
            status.setMaxLines(1);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            statusParams.topMargin = dp(1);
            root.addView(status, statusParams);

            map = new DungeonMiniMapView(IndividualDungeonActivity.this);
            LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            mapParams.topMargin = dp(3);
            root.addView(map, mapParams);
        }

        void bind(String displayName, String stateText, DungeonMonitorSnapshot snapshot) {
            name.setText(displayName);
            status.setText(stateText);
            map.setSnapshot(snapshot);
            root.setContentDescription(displayName + "、" + stateText);
        }

        void bindEmpty(int slot) {
            name.setText("空き " + slot);
            status.setText("探索中NPCなし");
            map.setSnapshot(null);
            root.setContentDescription("空きスロット " + slot);
        }
    }

    private Button modeButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(selected ? Color.rgb(45, 94, 137) : Color.rgb(19, 34, 50));
        bg.setStroke(dp(1), selected ? Color.rgb(90, 157, 214) : Color.rgb(47, 69, 91));
        button.setBackground(bg);
        return button;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(Color.rgb(12, 20, 31));
        bg.setStroke(dp(1), Color.rgb(35, 55, 76));
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
