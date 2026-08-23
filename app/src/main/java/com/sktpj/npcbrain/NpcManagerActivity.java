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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class NpcManagerActivity extends Activity {
    private NpcRegistryStore registry;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registry = new NpcRegistryStore(this);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setBackgroundColor(Color.rgb(247, 249, 252));
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
        title.setText("NPC管理");
        title.setTextColor(Color.rgb(23, 31, 44));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(header);

        TextView note = new TextView(this);
        note.setText("関係・年齢・経歴は初回確定時だけ編集できます。名前や人格はあとから変更できます。");
        note.setTextColor(Color.rgb(96, 108, 124));
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
        add.setOnClickListener(v -> showCreateDialog());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        ap.topMargin = dp(16);
        root.addView(add, ap);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(list);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, sp);
        return root;
    }

    private void renderList() {
        if (list == null) return;
        list.removeAllViews();
        List<String> ids = registry.npcIds();
        for (String npcId : ids) {
            CharacterStateStore store = new CharacterStateStore(NpcContexts.storage(this, npcId));
            list.addView(profileCard(npcId, store));
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
                + "\n経歴  " + store.background());
        meta.setTextColor(Color.rgb(78, 92, 111));
        meta.setTextSize(13);
        meta.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.topMargin = dp(8);
        card.addView(meta, mp);

        if (!store.isDead() && !store.identityMetadataLocked()) {
            Button init = new Button(this);
            init.setText("初期プロフィールを確定");
            init.setAllCaps(false);
            init.setOnClickListener(v -> showMetadataDialog(npcId, store));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            ip.topMargin = dp(10);
            card.addView(init, ip);
        } else if (!store.isDead()) {
            TextView locked = new TextView(this);
            locked.setText("初期プロフィール確定済み");
            locked.setTextColor(Color.rgb(126, 137, 151));
            locked.setTextSize(11);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            card.addView(locked, lp);
        }
        return card;
    }

    private void showCreateDialog() {
        String suggestedId = NpcRegistryStore.nextNpcId(registry.npcIds());
        LinearLayout form = form();
        EditText name = field("名前", suggestedId.toUpperCase());
        EditText relationship = field("ユーザーとの関係", CharacterStateStore.DEFAULT_RELATIONSHIP);
        EditText age = field("年齢", CharacterStateStore.DEFAULT_AGE);
        EditText background = field("経歴", CharacterStateStore.DEFAULT_BACKGROUND);
        form.addView(name);
        form.addView(relationship);
        form.addView(age);
        form.addView(background);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("NPCを追加")
                .setMessage("この3項目は保存後に編集できません。")
                .setView(form)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("追加", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String npcId = registry.createNpcId();
            CharacterStateStore store = new CharacterStateStore(NpcContexts.storage(this, npcId));
            store.saveProfile(safe(name.getText().toString(), npcId.toUpperCase()), 50, 50, 50, 50, 50, "");
            store.initializeIdentityMetadata(
                    relationship.getText().toString(),
                    age.getText().toString(),
                    background.getText().toString());
            dialog.dismiss();
            renderList();
        }));
        dialog.show();
    }

    private void showMetadataDialog(String npcId, CharacterStateStore store) {
        if (store.identityMetadataLocked() || store.isDead()) return;
        LinearLayout form = form();
        EditText relationship = field("ユーザーとの関係", store.relationshipToUser());
        EditText age = field("年齢", store.age());
        EditText background = field("経歴", store.background());
        form.addView(relationship);
        form.addView(age);
        form.addView(background);
        new AlertDialog.Builder(this)
                .setTitle(store.displayName() + " の初期プロフィール")
                .setMessage("確定後は編集できません。")
                .setView(form)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("確定", (dialog, which) -> {
                    store.initializeIdentityMetadata(
                            relationship.getText().toString(),
                            age.getText().toString(),
                            background.getText().toString());
                    renderList();
                })
                .show();
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int side = dp(20);
        form.setPadding(side, dp(4), side, 0);
        return form;
    }

    private EditText field(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(false);
        edit.setMinHeight(dp(52));
        return edit;
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

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
