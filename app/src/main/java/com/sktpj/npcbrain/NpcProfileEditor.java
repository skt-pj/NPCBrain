package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

final class NpcProfileEditor {
    interface OnSaved {
        void onSaved();
    }

    private static final class TraitControl {
        final LinearLayout root;
        final SeekBar seek;

        TraitControl(LinearLayout root, SeekBar seek) {
            this.root = root;
            this.seek = seek;
        }
    }

    private NpcProfileEditor() {}

    static void showInvitation(
            Activity activity,
            String npcId,
            Runnable afterMetadataSave,
            OnSaved onSaved
    ) {
        show(activity, npcId, false, afterMetadataSave, onSaved);
    }

    static void showDebug(Activity activity, String npcId, OnSaved onSaved) {
        show(activity, npcId, true, null, onSaved);
    }

    private static void show(
            Activity activity,
            String npcId,
            boolean debugOverride,
            Runnable afterMetadataSave,
            OnSaved onSaved
    ) {
        ContextBundle bundle = new ContextBundle(activity, npcId);
        if (bundle.character.isDead()) {
            Toast.makeText(activity, "死亡したNPCは編集できません。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!debugOverride && bundle.character.identityMetadataLocked()) {
            Toast.makeText(activity, "このNPCの初回設定は確定済みです。", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int side = dp(activity, 18);
        form.setPadding(side, dp(activity, 8), side, dp(activity, 12));

        String currentName = bundle.character.displayName();
        if (currentName == null || currentName.trim().isEmpty() || "NPC".equals(currentName.trim())) {
            currentName = npcId.toUpperCase(Locale.US);
        }
        EditText name = field(activity, "名前", currentName);
        TraitControl extraversion = trait(activity, "外向性", bundle.character.traitPercent(CharacterStateStore.extraversionKey()));
        TraitControl neuroticism = trait(activity, "神経症傾向", bundle.character.traitPercent(CharacterStateStore.neuroticismKey()));
        TraitControl agreeableness = trait(activity, "協調性", bundle.character.traitPercent(CharacterStateStore.agreeablenessKey()));
        TraitControl conscientiousness = trait(activity, "誠実性", bundle.character.traitPercent(CharacterStateStore.conscientiousnessKey()));
        TraitControl openness = trait(activity, "開放性", bundle.character.traitPercent(CharacterStateStore.opennessKey()));
        EditText speech = field(activity, "話し方", bundle.character.speechStyle());
        EditText relationship = field(activity, "ユーザーとの関係", bundle.character.relationshipToUser());
        EditText age = field(activity, "年齢", bundle.character.age());
        EditText occupation = field(activity, "職業", bundle.character.occupation());
        EditText background = field(activity, "経歴", bundle.character.background());
        EditText role = field(activity, "役割・自己像", bundle.memory.profileText("role_identity"));
        EditText values = field(activity, "価値観", bundle.memory.profileText("value"));
        EditText goals = field(activity, "目標", bundle.memory.profileText("goal"));
        EditText fears = field(activity, "恐れ", bundle.memory.profileText("fear"));
        EditText relations = field(activity, "人間関係", bundle.memory.profileText("relationship"));

        form.addView(name);
        form.addView(extraversion.root);
        form.addView(neuroticism.root);
        form.addView(agreeableness.root);
        form.addView(conscientiousness.root);
        form.addView(openness.root);
        form.addView(speech);
        form.addView(relationship);
        form.addView(age);
        form.addView(occupation);
        form.addView(background);
        form.addView(role);
        form.addView(values);
        form.addView(goals);
        form.addView(fears);
        form.addView(relations);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(debugOverride ? "NPC設定を編集" : "NPCを招待")
                .setMessage(debugOverride
                        ? "Debug NPC管理では、確定済み設定も何度でも変更できます。"
                        : "この設定は招待時の初回のみ確定できます。人格と一日の行動へ反映します。")
                .setView(scroll)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton(debugOverride ? "保存" : "招待", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean metadataSaved = debugOverride
                    ? bundle.character.updateIdentityMetadataForDebug(
                            relationship.getText().toString(),
                            age.getText().toString(),
                            occupation.getText().toString(),
                            background.getText().toString())
                    : bundle.character.initializeIdentityMetadata(
                            relationship.getText().toString(),
                            age.getText().toString(),
                            occupation.getText().toString(),
                            background.getText().toString());
            if (!metadataSaved) {
                Toast.makeText(activity, "プロフィールを保存できませんでした。", Toast.LENGTH_SHORT).show();
                return;
            }
            if (afterMetadataSave != null) afterMetadataSave.run();

            bundle.character.saveProfile(
                    safe(name.getText().toString(), npcId.toUpperCase(Locale.US)),
                    extraversion.seek.getProgress(),
                    neuroticism.seek.getProgress(),
                    agreeableness.seek.getProgress(),
                    conscientiousness.seek.getProgress(),
                    openness.seek.getProgress(),
                    speech.getText().toString());
            bundle.memory.replaceProfileAdaptations(
                    role.getText().toString(),
                    values.getText().toString(),
                    goals.getText().toString(),
                    fears.getText().toString(),
                    relations.getText().toString());
            applyProfileSchedule(activity, npcId, bundle.character);
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved();
        }));
        dialog.show();
    }

    private static void applyProfileSchedule(Activity activity, String npcId, CharacterStateStore store) {
        NpcId id = NpcId.of(npcId);
        long now = new WorldClock(activity).now();
        WorldStateStore worldStateStore = new WorldStateStore(activity);
        LifeState current = worldStateStore.lifeState(id, now);
        DailySchedule schedule = DailySchedule.profileFor(id, store.age(), store.occupation());
        worldStateStore.saveLifeState(current.withSchedule(now, schedule.toJson()));
        new WorldRuntimeV040(activity).syncAllNow();
        NPCBrainApplication.requestDemoRoomRefresh();
    }

    private static EditText field(Activity activity, String hint, String value) {
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setSingleLine(false);
        edit.setMinHeight(dp(activity, 52));
        return edit;
    }

    private static TraitControl trait(Activity activity, String label, int value) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        SeekBar seek = new SeekBar(activity);
        seek.setMax(100);
        seek.setProgress(Math.max(0, Math.min(100, value)));
        title.setText(label + "  " + seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                title.setText(label + "  " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(title);
        root.addView(seek);
        return new TraitControl(root, seek);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class ContextBundle {
        final CharacterStateStore character;
        final MemoryStore memory;

        ContextBundle(Activity activity, String npcId) {
            android.content.Context storage = NpcContexts.storage(activity, npcId);
            character = new CharacterStateStore(storage);
            memory = new MemoryStore(storage);
        }
    }
}
