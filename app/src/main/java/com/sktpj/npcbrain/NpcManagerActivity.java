package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public final class NpcManagerActivity extends Activity {
    private NpcRegistryStore registry;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!NPCBrainApplication.isDebugBuild()) {
            finish();
            return;
        }
        registry = new NpcRegistryStore(this);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (NPCBrainApplication.isDebugBuild()) renderList();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setBackgroundColor(AppUiTheme.APP_BACKGROUND);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹ 会話");
        back.setAllCaps(false);
        back.setOnClickListener(v -> openConversation());
        header.addView(back, new LinearLayout.LayoutParams(dp(92), dp(46)));

        TextView title = new TextView(this);
        title.setText("NPC管理 · DEBUG");
        title.setTextColor(AppUiTheme.APP_TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(header);

        TextView note = new TextView(this);
        note.setText("Debugビルド専用。NPCの追加、全設定の再編集、削除ができます。");
        note.setTextColor(AppUiTheme.APP_MUTED);
        note.setTextSize(13);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(12);
        root.addView(note, np);

        Button add = new Button(this);
        add.setText("＋ NPCを追加");
        add.setAllCaps(false);
        add.setTextSize(15);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setTextColor(Color.WHITE);
        add.setBackground(cardBackground(Color.rgb(46, 101, 181), Color.rgb(46, 101, 181), 14));
        add.setOnClickListener(v -> addNpc());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        ap.topMargin = dp(16);
        root.addView(add, ap);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void renderList() {
        if (list == null || registry == null) return;
        list.removeAllViews();
        List<String> ids = registry.npcIds();
        for (String npcId : ids) {
            CharacterStateStore store = new CharacterStateStore(NpcContexts.storage(this, npcId));
            list.addView(profileCard(npcId, store));
        }
        if (ids.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("登録NPCはいません。");
            empty.setTextColor(AppUiTheme.APP_MUTED);
            empty.setTextSize(14);
            list.addView(empty);
        }
    }

    private View profileCard(String npcId, CharacterStateStore store) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardBackground(Color.WHITE, Color.rgb(222, 228, 236), 16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(10);
        card.setLayoutParams(cp);

        TextView name = new TextView(this);
        name.setText((store.isDead() ? "死亡 · " : "") + store.displayName() + "   " + npcId);
        name.setTextColor(Color.rgb(25, 34, 48));
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);

        TextView meta = new TextView(this);
        meta.setText("ユーザーとの関係  " + store.relationshipToUser()
                + "\n年齢  " + store.age()
                + "\n職業  " + store.occupation()
                + "\n経歴  " + store.background());
        meta.setTextColor(Color.rgb(78, 92, 111));
        meta.setTextSize(13);
        meta.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.topMargin = dp(8);
        card.addView(meta, mp);

        if (!store.isDead()) {
            Button edit = new Button(this);
            edit.setText("設定を編集");
            edit.setAllCaps(false);
            edit.setOnClickListener(v -> NpcProfileEditor.showDebug(this, npcId, this::renderList));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            ep.topMargin = dp(10);
            card.addView(edit, ep);
        }

        Button delete = new Button(this);
        delete.setText("削除");
        delete.setAllCaps(false);
        delete.setOnClickListener(v -> confirmDelete(npcId, store.displayName()));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, this.dp(48));
        dp.topMargin = this.dp(6);
        card.addView(delete, dp);
        return card;
    }

    private void addNpc() {
        String npcId = registry.createNpcId();
        CharacterStateStore store = new CharacterStateStore(NpcContexts.storage(this, npcId));
        store.saveProfile(npcId.toUpperCase(Locale.US), 50, 50, 50, 50, 50, "");
        NpcProfileEditor.showDebug(this, npcId, this::renderList);
        renderList();
    }

    private void confirmDelete(String npcId, String displayName) {
        new AlertDialog.Builder(this)
                .setTitle("NPCを削除")
                .setMessage(displayName + " (" + npcId + ") をNPC一覧から削除します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除", (dialog, which) -> {
                    registry.removeNpc(npcId);
                    renderList();
                })
                .show();
    }

    private void openConversation() {
        Intent intent = new Intent(this, DemoActivityV032.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
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
