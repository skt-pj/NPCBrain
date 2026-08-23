package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class DungeonGoalInputBridge {
    private static final String BUTTON_TAG = "npcbrain_user_goal_button_v0412";
    private static final long REFRESH_MS = 450L;

    private DungeonGoalInputBridge() {
    }

    static void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            String npcId = selectedNpcId(activity);
            if (npcId.isEmpty()) return;
            Button button = findButtonByText(activity.findViewById(android.R.id.content), "目的設定");
            if (button != null && !BUTTON_TAG.equals(button.getTag())) {
                button.setTag(BUTTON_TAG);
                button.setOnClickListener(v -> showGoalDialog(activity));
            }
            TextView interpretation = ensureInterpretationView(activity);
            if (interpretation != null && interpretation.getTag() instanceof RefreshTag) {
                RefreshTag tag = (RefreshTag) interpretation.getTag();
                if (!tag.running) {
                    tag.running = true;
                    scheduleRefresh(activity, interpretation, tag);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void showGoalDialog(DungeonActivity activity) {
        final String npcId = selectedNpcId(activity);
        if (npcId.isEmpty()) return;
        DungeonObjectiveStore store = new DungeonObjectiveStore(activity);
        DungeonObjective current = store.load(npcId);

        EditText input = new EditText(activity);
        input.setHint("例: できるだけ戦わず最上階へ");
        input.setText(current.isActive() ? current.rawUserText() : "");
        input.setSelection(input.getText().length());
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(DungeonObjective.MAX_USER_TEXT_LENGTH)
        });
        int pad = dp(activity, 20);
        input.setPadding(pad, dp(activity, 12), pad, dp(activity, 12));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ダンジョンの目的")
                .setMessage("自然な言葉で目的を入力してください。NPCが人格と状況に合わせて解釈し、行動方針へ変換します。")
                .setView(input)
                .setPositiveButton("設定", null)
                .setNeutralButton("解除", (d, which) -> clearGoal(activity, npcId))
                .setNegativeButton("キャンセル", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String normalized = DungeonObjective.normalizeUserText(input.getText().toString());
                if (normalized.isEmpty()) {
                    input.setError("目的を入力してください");
                    return;
                }
                DungeonObjective now = store.load(npcId);
                DungeonObjective next = DungeonObjective.fromUserText(
                        normalized,
                        System.currentTimeMillis());
                if (DungeonObjective.sameGoal(now, next)) {
                    dialog.dismiss();
                    return;
                }
                store.save(npcId, next);
                reloadSelectedNpc(activity);
                requestBrain(activity, DungeonCognitionGate.OBJECTIVE_CHANGED);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private static void clearGoal(DungeonActivity activity, String npcId) {
        if (npcId == null || npcId.isEmpty()) return;
        new DungeonObjectiveStore(activity).save(npcId, DungeonObjective.none());
        reloadSelectedNpc(activity);
    }

    private static TextView ensureInterpretationView(DungeonActivity activity) {
        try {
            Object raw = objectField(activity, "objectiveView");
            if (!(raw instanceof TextView)) return null;
            TextView objective = (TextView) raw;
            if (!(objective.getParent() instanceof LinearLayout)) return null;
            LinearLayout parent = (LinearLayout) objective.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                Object tag = child.getTag();
                if (child instanceof TextView && tag instanceof RefreshTag) {
                    return (TextView) child;
                }
            }

            TextView interpretation = new TextView(activity);
            interpretation.setTextColor(Color.rgb(177, 199, 224));
            interpretation.setTextSize(11);
            interpretation.setMaxLines(3);
            RefreshTag tag = new RefreshTag();
            interpretation.setTag(tag);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(activity, 3);
            int index = parent.indexOfChild(objective);
            parent.addView(interpretation, Math.min(parent.getChildCount(), index + 1), params);
            return interpretation;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void scheduleRefresh(
            DungeonActivity activity,
            TextView view,
            RefreshTag tag
    ) {
        view.post(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || view.getWindowToken() == null) {
                    tag.running = false;
                    return;
                }
                refreshInterpretation(activity, view);
                view.postDelayed(this, REFRESH_MS);
            }
        });
    }

    private static void refreshInterpretation(DungeonActivity activity, TextView view) {
        try {
            String npcId = selectedNpcId(activity);
            if (npcId.isEmpty()) return;
            DungeonObjectiveStore objectiveStore = new DungeonObjectiveStore(activity);
            DungeonObjective objective = objectiveStore.load(npcId);
            DungeonMindStore.Snapshot mind = new DungeonMindStore(activity).load(npcId);
            DungeonPlan plan = mind == null ? null : mind.plan;

            if (objective.isCustom()
                    && objective.targetFloor == 0
                    && plan != null
                    && DungeonPlan.SOURCE_BRAIN.equals(plan.source)
                    && plan.matches(objective)
                    && plan.targetFloor > 0) {
                DungeonObjective interpreted = DungeonObjective.customWithTarget(
                        objective.rawUserText(),
                        plan.targetFloor,
                        objective.createdTimeMs);
                objectiveStore.save(npcId, interpreted);
                reloadSelectedNpc(activity);
                objective = interpreted;
                mind = new DungeonMindStore(activity).load(npcId);
                plan = mind == null ? null : mind.plan;
            }

            refreshEffectiveTactic(activity);
            boolean thinking = booleanField(activity, "brainThinking")
                    && npcId.equals(stringField(activity, "activeBrainNpcId"));
            if (!objective.isActive()) {
                view.setText("解釈: 目的未設定");
                return;
            }
            if (thinking) {
                view.setText("解釈: Brainが目的を解釈中…");
                return;
            }
            if (plan != null && plan.matches(objective)) {
                String interpretation = plan.interpretation;
                if (interpretation == null || interpretation.trim().isEmpty()) {
                    interpretation = DungeonPlan.SOURCE_BRAIN.equals(plan.source)
                            ? "Brain計画を適用中" : "Brainの解釈待ち。Local planで継続";
                }
                String target = plan.targetFloor > 0 && objective.isCustom()
                        ? " · 目標 " + plan.targetFloor + "F" : "";
                String strategy = DungeonPlan.SOURCE_BRAIN.equals(plan.source)
                        ? " · " + DungeonPlan.strategyLabel(plan.strategy) : "";
                view.setText("解釈: " + interpretation + target + strategy);
                return;
            }
            String error = mind == null ? "" : mind.error;
            view.setText(error == null || error.trim().isEmpty()
                    ? "解釈: 未確定 · Local planで継続"
                    : "解釈: 未確定 · " + compact(error));
        } catch (Exception ignored) {
            view.setText("解釈: Local planで継続");
        }
    }

    private static void refreshEffectiveTactic(DungeonActivity activity) {
        try {
            Object rawState = objectField(activity, "state");
            Object rawIntent = objectField(activity, "currentIntent");
            Object rawPlan = objectField(activity, "currentPlan");
            Object rawView = objectField(activity, "intentView");
            if (!(rawState instanceof DungeonState) || !(rawView instanceof TextView)) return;
            DungeonState state = (DungeonState) rawState;
            DungeonIntent intent = rawIntent instanceof DungeonIntent ? (DungeonIntent) rawIntent : null;
            DungeonPlan plan = rawPlan instanceof DungeonPlan ? (DungeonPlan) rawPlan : null;
            String mode = DungeonPersonalityPolicy.effectiveMode(state, intent, plan);
            ((TextView) rawView).setText(
                    "戦術: " + DungeonIntent.modeLabel(mode) + " · LOCAL EXECUTION");
        } catch (Exception ignored) {
        }
    }

    private static Button findButtonByText(View root, String label) {
        if (root instanceof Button && label.contentEquals(((Button) root).getText())) {
            return (Button) root;
        }
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findButtonByText(group.getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }

    private static String selectedNpcId(DungeonActivity activity) {
        return stringField(activity, "selectedNpcId");
    }

    private static Object objectField(DungeonActivity activity, String name) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringField(DungeonActivity activity, String name) {
        Object value = objectField(activity, name);
        return value == null ? "" : value.toString();
    }

    private static boolean booleanField(DungeonActivity activity, String name) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void reloadSelectedNpc(DungeonActivity activity) {
        invoke(activity, "loadSelectedNpc", new Class<?>[0], new Object[0]);
    }

    private static void requestBrain(DungeonActivity activity, String reason) {
        invoke(activity, "requestBrain", new Class<?>[]{String.class}, new Object[]{reason});
    }

    private static void invoke(
            DungeonActivity activity,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] args
    ) {
        try {
            Method method = DungeonActivity.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(activity, args);
        } catch (Exception ignored) {
        }
    }

    private static String compact(String value) {
        String text = value == null ? "" : value.replace('\n', ' ').trim();
        return text.length() > 48 ? text.substring(0, 48) + "…" : text;
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class RefreshTag {
        boolean running;
    }
}
