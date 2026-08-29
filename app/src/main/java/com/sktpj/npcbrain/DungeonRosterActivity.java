package com.sktpj.npcbrain;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ordinary dungeon-area member selection with no consent/reluctance state. */
public final class DungeonRosterActivity extends Activity {
    private DungeonRosterStore rosterStore;
    private NpcAiStaminaStore staminaStore;
    private final List<String> working = new ArrayList<>();
    private LinearLayout list;
    private TextView count;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rosterStore = new DungeonRosterStore(this);
        staminaStore = new NpcAiStaminaStore(this);
        working.addAll(rosterStore.activeNpcIds());
        setContentView(buildView());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private ScrollView buildView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(7, 12, 22));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ダンジョンメンバー", 20, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(this);
        close.setText("完了");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setBackgroundColor(Color.rgb(25, 50, 78));
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(88), dp(46)));
        root.addView(header);

        TextView note = text(
                "通常の行動先として探索するNPCを選択します。最大"
                        + DungeonRosterPolicy.MAX_ACTIVE + "人。",
                12,
                Color.rgb(150, 171, 198),
                false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(6);
        root.addView(note, noteParams);

        count = text("", 12, Color.rgb(196, 216, 241), true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = dp(12);
        root.addView(count, countParams);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(6);
        root.addView(list, listParams);
        return scroll;
    }

    private void render() {
        if (list == null || count == null) return;
        List<String> candidates = rosterStore.candidates();
        working.retainAll(candidates);
        rosterStore.save(working);
        count.setText("探索中 " + working.size() + "/" + DungeonRosterPolicy.MAX_ACTIVE);
        list.removeAllViews();

        for (String npcId : candidates) {
            CharacterStateStore character =
                    new CharacterStateStore(NpcContexts.storage(this, npcId));
            DungeonState dungeon = new DungeonStore(this).load(npcId);
            NpcAiStaminaStore.Snapshot stamina = staminaStore.snapshot(npcId);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setBackgroundColor(Color.rgb(14, 23, 36));

            CheckBox check = new CheckBox(this);
            check.setChecked(working.contains(npcId));
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(94, 153, 224)));
            row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));

            String floor = dungeon == null ? "未開始" : dungeon.floor + "F";
            TextView detail = text(
                    displayName(character, npcId) + "\n"
                            + floor
                            + " · AI STAMINA " + stamina.remainingPercent + "%"
                            + " · 消費 " + NpcAiUsageDisplayPolicy.formatSpentJpy(stamina.spentJpy),
                    13,
                    Color.rgb(228, 237, 249),
                    true);
            row.addView(detail, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            check.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    if (!working.contains(npcId)) {
                        if (working.size() >= DungeonRosterPolicy.MAX_ACTIVE) {
                            button.setChecked(false);
                            Toast.makeText(this,
                                    "最大" + DungeonRosterPolicy.MAX_ACTIVE + "人です",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        working.add(npcId);
                    }
                } else {
                    working.remove(npcId);
                }
                rosterStore.save(working);
                count.setText("探索中 " + working.size() + "/" + DungeonRosterPolicy.MAX_ACTIVE);
            });

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(8);
            list.addView(row, rowParams);
        }
    }

    private static String displayName(CharacterStateStore character, String npcId) {
        String name = character.displayName();
        if (name == null || name.trim().isEmpty() || "NPC".equals(name.trim())) {
            return npcId.toUpperCase(Locale.US);
        }
        return name.trim() + "  (" + npcId + ")";
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
