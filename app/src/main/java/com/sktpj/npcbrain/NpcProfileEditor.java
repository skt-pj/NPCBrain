package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

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
            Runnable afterProfileSaved,
            OnSaved onSaved
    ) {
        show(activity, npcId, false, afterProfileSaved, onSaved);
    }

    static void showDebug(Activity activity, String npcId, OnSaved onSaved) {
        show(activity, npcId, true, null, onSaved);
    }

    private static void show(
            Activity activity,
            String npcId,
            boolean debugOverride,
            Runnable afterProfileSaved,
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

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int side = dp(activity, 18);
        form.setPadding(side, dp(activity, 6), side, dp(activity, 20));
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

        TextView explanation = new TextView(activity);
        explanation.setText(debugOverride
                ? "全項目をLLMで整合し、人格・認知入力・一日の予定へ反映します。明示入力そのものは勝手に書き換えません。"
                : "全項目をLLMで整合してから招待を確定します。この初回設定が人格・認知入力・一日の予定へ反映されます。");
        explanation.setTextSize(13);
        explanation.setTextColor(Color.rgb(80, 88, 102));
        explanation.setPadding(side, dp(activity, 10), side, dp(activity, 4));

        TextView status = new TextView(activity);
        status.setText("入力後、下のボタンで保存してください。");
        status.setTextSize(13);
        status.setTextColor(Color.rgb(50, 85, 135));
        status.setPadding(side, dp(activity, 4), side, dp(activity, 8));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(form);

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(side, dp(activity, 10), side, dp(activity, 12));

        Button cancel = new Button(activity);
        cancel.setText("キャンセル");
        cancel.setAllCaps(false);
        cancel.setMinHeight(dp(activity, 48));

        Button save = new Button(activity);
        save.setText(debugOverride ? "保存" : "招待");
        save.setAllCaps(false);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setMinHeight(dp(activity, 48));

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(activity, 52), 1f);
        cancelParams.rightMargin = dp(activity, 6);
        footer.addView(cancel, cancelParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(activity, 52), 1f);
        saveParams.leftMargin = dp(activity, 6);
        footer.addView(save, saveParams);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(explanation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        shell.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(debugOverride ? "NPC設定を編集" : "NPCを招待")
                .setView(shell)
                .create();
        AtomicBoolean sessionActive = new AtomicBoolean(true);
        dialog.setOnDismissListener(ignored -> sessionActive.set(false));
        cancel.setOnClickListener(v -> dialog.dismiss());

        save.setOnClickListener(v -> {
            if (!sessionActive.get() || !dialog.isShowing() || !save.isEnabled()) return;
            NpcProfileDraft draft = new NpcProfileDraft(
                    safe(name.getText().toString(), npcId.toUpperCase(Locale.US)),
                    extraversion.seek.getProgress(),
                    neuroticism.seek.getProgress(),
                    agreeableness.seek.getProgress(),
                    conscientiousness.seek.getProgress(),
                    openness.seek.getProgress(),
                    speech.getText().toString(),
                    relationship.getText().toString(),
                    age.getText().toString(),
                    occupation.getText().toString(),
                    background.getText().toString(),
                    role.getText().toString(),
                    values.getText().toString(),
                    goals.getText().toString(),
                    fears.getText().toString(),
                    relations.getText().toString());

            save.setEnabled(false);
            status.setTextColor(Color.rgb(50, 85, 135));
            status.setText("プロフィールをLLMで整合中…");

            new Thread(() -> {
                try {
                    NpcProfileReconciler.Result result =
                            new NpcProfileReconciler(activity).reconcile(npcId, draft);
                    activity.runOnUiThread(() -> {
                        if (!isActive(activity, dialog, sessionActive)) return;
                        try {
                            persistValidatedProfile(
                                    activity,
                                    npcId,
                                    debugOverride,
                                    draft,
                                    result,
                                    bundle,
                                    afterProfileSaved);
                            status.setTextColor(Color.rgb(35, 115, 70));
                            status.setText("保存しました。");
                            dialog.dismiss();
                            if (onSaved != null) onSaved.onSaved();
                        } catch (Exception error) {
                            showSaveError(status, save, error);
                        }
                    });
                } catch (Exception error) {
                    activity.runOnUiThread(() -> {
                        if (!isActive(activity, dialog, sessionActive)) return;
                        showSaveError(status, save, error);
                    });
                }
            }, "npc-profile-reconcile-" + npcId).start();
        });

        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                int height = Math.max(dp(activity, 360), Math.round(screenHeight * 0.88f));
                height = Math.min(height, screenHeight);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, height);
            }
        });
        dialog.show();
    }

    private static void persistValidatedProfile(
            Activity activity,
            String npcId,
            boolean debugOverride,
            NpcProfileDraft draft,
            NpcProfileReconciler.Result result,
            ContextBundle bundle,
            Runnable afterProfileSaved
    ) {
        if (bundle.character.isDead()) {
            throw new IllegalStateException("死亡したNPCは編集できません。");
        }
        boolean metadataSaved = debugOverride
                ? bundle.character.updateIdentityMetadataForDebug(
                        draft.relationshipToUser(), draft.age(), draft.occupation(), draft.background())
                : bundle.character.initializeIdentityMetadata(
                        draft.relationshipToUser(), draft.age(), draft.occupation(), draft.background());
        if (!metadataSaved) {
            throw new IllegalStateException("プロフィール基本情報を保存できませんでした。");
        }

        boolean characterSaved = bundle.character.saveProfile(
                draft.name(),
                draft.extraversion(),
                draft.neuroticism(),
                draft.agreeableness(),
                draft.conscientiousness(),
                draft.openness(),
                draft.speechStyle());
        if (!characterSaved) {
            throw new IllegalStateException("人格データを保存できませんでした。");
        }

        bundle.memory.replaceProfileAdaptations(
                draft.roleIdentity(),
                draft.values(),
                draft.goals(),
                draft.fears(),
                draft.relationships());

        if (!bundle.synthesis.save(result.synthesis())) {
            throw new IllegalStateException("LLM整合プロフィールを保存できませんでした。");
        }

        NpcId id = NpcId.of(npcId);
        long now = new WorldClock(activity).now();
        WorldStateStore worldStateStore = new WorldStateStore(activity);
        LifeState current = worldStateStore.lifeState(id, now);
        worldStateStore.saveLifeState(current.withSchedule(now, result.schedule().toJson()));

        if (afterProfileSaved != null) afterProfileSaved.run();
        new WorldRuntimeV040(activity).syncAllNow();
        NPCBrainApplication.requestDemoRoomRefresh();
    }

    private static void showSaveError(TextView status, Button save, Exception error) {
        String message = error == null ? "保存できませんでした。" : error.getMessage();
        if (message == null || message.trim().isEmpty()) message = "保存できませんでした。";
        status.setTextColor(Color.rgb(178, 54, 54));
        status.setText("保存できませんでした: " + message.trim());
        save.setEnabled(true);
    }

    private static boolean isActive(
            Activity activity,
            AlertDialog dialog,
            AtomicBoolean sessionActive
    ) {
        if (!sessionActive.get() || !dialog.isShowing() || activity.isFinishing()) return false;
        return Build.VERSION.SDK_INT < 17 || !activity.isDestroyed();
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
        final NpcProfileSynthesisStore synthesis;

        ContextBundle(Activity activity, String npcId) {
            android.content.Context storage = NpcContexts.storage(activity, npcId);
            character = new CharacterStateStore(storage);
            memory = new MemoryStore(storage);
            synthesis = new NpcProfileSynthesisStore(storage);
        }
    }
}
