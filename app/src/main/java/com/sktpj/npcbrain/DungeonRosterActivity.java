package com.sktpj.npcbrain;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DungeonRosterActivity extends Activity {
    private DungeonRosterStore rosterStore;
    private NpcAiStaminaStore staminaStore;
    private final List<String> working = new ArrayList<>();
    private final List<String> candidates = new ArrayList<>();

    private TextView countView;
    private LinearLayout listView;
    private Button saveButton;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        rosterStore = new DungeonRosterStore(this);
        staminaStore = new NpcAiStaminaStore(this);
        candidates.addAll(rosterStore.candidates());
        working.addAll(rosterStore.activeNpcIds());
        setContentView(buildContent());
        render();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 9, 16));
        applySafeInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = compactButton("戻る");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(72), dp(44)));

        TextView title = new TextView(this);
        title.setText("探索メンバー選択");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        titleParams.leftMargin = dp(10);
        header.addView(title, titleParams);
        root.addView(header);

        TextView lead = new TextView(this);
        lead.setText("同時探索は最大3人。各NPCは別々のダンジョンを進み、進行状況は個別に保持されます。");
        lead.setTextColor(Color.rgb(163, 184, 208));
        lead.setTextSize(12);
        LinearLayout.LayoutParams leadParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        leadParams.topMargin = dp(8);
        root.addView(lead, leadParams);

        countView = new TextView(this);
        countView.setTextColor(Color.rgb(224, 237, 252));
        countView.setTextSize(14);
        countView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = dp(12);
        root.addView(countView, countParams);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("名前 / NPC IDで検索");
        search.setHintTextColor(Color.rgb(112, 132, 155));
        search.setTextColor(Color.rgb(232, 240, 250));
        search.setTextSize(13);
        search.setBackground(fieldBackground());
        search.setPadding(dp(12), 0, dp(12), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                renderList();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        searchParams.topMargin = dp(10);
        root.addView(search, searchParams);

        ScrollView scroll = new ScrollView(this);
        listView = new LinearLayout(this);
        listView.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(10);
        root.addView(scroll, scrollParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(8), 0, 0);

        Button cancel = actionButton("キャンセル", false);
        cancel.setOnClickListener(v -> finish());
        bottom.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1f));

        saveButton = actionButton("このメンバーで確定", true);
        saveButton.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(50), 1.35f);
        saveParams.leftMargin = dp(8);
        bottom.addView(saveButton, saveParams);
        root.addView(bottom);

        return root;
    }

    private void render() {
        renderCount();
        renderList();
    }

    private void renderCount() {
        countView.setText("探索枠  " + working.size() + " / " + DungeonRosterPolicy.MAX_ACTIVE);
        saveButton.setEnabled(!working.isEmpty());
        saveButton.setAlpha(working.isEmpty() ? 0.45f : 1f);
    }

    private void renderList() {
        if (listView == null) return;
        listView.removeAllViews();
        boolean any = false;
        for (String npcId : candidates) {
            CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(this, npcId));
            String name = character.displayName();
            if (!matchesQuery(name, npcId)) continue;
            any = true;
            listView.addView(buildNpcRow(npcId, name));
        }
        if (!any) {
            TextView empty = new TextView(this);
            empty.setText(candidates.isEmpty() ? "登録済みNPCがいません。" : "一致するNPCがいません。");
            empty.setTextColor(Color.rgb(145, 165, 188));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, dp(32));
            listView.addView(empty);
        }
    }

    private View buildNpcRow(String npcId, String name) {
        boolean selected = working.contains(npcId);
        boolean canAdd = selected || working.size() < DungeonRosterPolicy.MAX_ACTIVE;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(rowBackground(selected));

        CheckBox check = new CheckBox(this);
        check.setChecked(selected);
        check.setEnabled(canAdd);
        check.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{
                        Color.rgb(86, 152, 224),
                        Color.rgb(77, 91, 108),
                        Color.rgb(139, 160, 184)
                }));
        row.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(6);

        TextView primary = new TextView(this);
        primary.setText(name + "   " + npcId);
        primary.setTextColor(Color.rgb(238, 245, 253));
        primary.setTextSize(14);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        text.addView(primary);

        DungeonState dungeon = new DungeonStore(this).load(npcId);
        DungeonParticipationState participation = DungeonParticipationStore.forNpc(this, npcId).load();
        NpcAiStaminaStore.Snapshot stamina = staminaStore.snapshot(npcId);
        String floor = dungeon == null ? "未開始" : dungeon.floor + "F";
        String progress = participation.isAccepted() ? "参加可" : participation.label();

        TextView secondary = new TextView(this);
        secondary.setText(progress + "  ·  " + floor + "  ·  AI STAMINA " + stamina.remainingPercent + "%");
        secondary.setTextColor(participation.isAccepted()
                ? Color.rgb(154, 199, 171) : Color.rgb(169, 183, 201));
        secondary.setTextSize(11);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        secondaryParams.topMargin = dp(3);
        text.addView(secondary, secondaryParams);

        if (!participation.isAccepted()) {
            TextView note = new TextView(this);
            note.setText("探索は会話で参加を決めるまで待機します");
            note.setTextColor(Color.rgb(137, 153, 174));
            note.setTextSize(10);
            text.addView(note);
        }

        row.addView(text, textParams);
        row.setContentDescription(name + "、" + (selected ? "選択中" : "未選択") + "、"
                + participation.label() + "、" + floor + "、AI STAMINA " + stamina.remainingPercent + "%");

        View.OnClickListener toggle = v -> {
            if (working.contains(npcId)) {
                if (working.size() == 1) {
                    Toast.makeText(this, "探索メンバーは1人以上必要です", Toast.LENGTH_SHORT).show();
                    return;
                }
                working.remove(npcId);
            } else {
                if (working.size() >= DungeonRosterPolicy.MAX_ACTIVE) {
                    Toast.makeText(this, "同時探索は最大3人です", Toast.LENGTH_SHORT).show();
                    return;
                }
                working.add(npcId);
            }
            render();
        };
        row.setOnClickListener(toggle);
        check.setOnClickListener(toggle);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(7);
        row.setLayoutParams(params);
        return row;
    }

    private boolean matchesQuery(String name, String npcId) {
        if (query.isEmpty()) return true;
        String haystack = ((name == null ? "" : name) + " " + npcId).toLowerCase(Locale.ROOT);
        return haystack.contains(query);
    }

    private void save() {
        if (working.isEmpty()) {
            Toast.makeText(this, "探索メンバーは1人以上必要です", Toast.LENGTH_SHORT).show();
            return;
        }
        rosterStore.save(working);
        setResult(RESULT_OK);
        finish();
    }

    private Button compactButton(String label) {
        return actionButton(label, false);
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(cardBackground(
                primary ? Color.rgb(42, 91, 156) : Color.rgb(22, 34, 49),
                primary ? Color.rgb(80, 139, 207) : Color.rgb(48, 65, 84),
                11));
        button.setMinHeight(dp(44));
        return button;
    }

    private GradientDrawable rowBackground(boolean selected) {
        return cardBackground(
                selected ? Color.rgb(18, 36, 57) : Color.rgb(12, 20, 31),
                selected ? Color.rgb(73, 126, 190) : Color.rgb(36, 53, 72),
                12);
    }

    private GradientDrawable fieldBackground() {
        return cardBackground(Color.rgb(12, 20, 31), Color.rgb(42, 59, 77), 11);
    }

    private GradientDrawable cardBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(5, 9, 16));
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    }

    private void applySafeInsets(View root) {
        root.setPadding(dp(12), dp(8), dp(12), dp(10));
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
                        dp(12) + right, dp(10) + bottom);
                return insets;
            });
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
