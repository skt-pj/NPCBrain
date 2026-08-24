package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

final class DungeonConsentBridge {
    private static final String GOAL_OVERRIDE_TAG = "npcbrain_consent_goal_override_v0413";
    private static final long REFRESH_MS = 300L;
    private static final WeakHashMap<DungeonActivity, BridgeState> STATES = new WeakHashMap<>();

    private DungeonConsentBridge() {
    }

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        BridgeState existing = STATES.get(activity);
        if (existing != null) {
            refresh(activity, existing);
            return;
        }
        TextView participationView = ensureParticipationView(activity);
        Button goalButton = findButtonByText(activity.findViewById(android.R.id.content), "目的設定");
        Button pauseButton = buttonField(activity, "pauseButton");
        BridgeState state = new BridgeState(participationView, goalButton, pauseButton);
        STATES.put(activity, state);
        refresh(activity, state);
        if (participationView != null) {
            participationView.post(new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing()) return;
                    if (participationView.getWindowToken() == null) {
                        participationView.postDelayed(this, REFRESH_MS);
                        return;
                    }
                    refresh(activity, state);
                    participationView.postDelayed(this, REFRESH_MS);
                }
            });
        }
    }

    private static void refresh(DungeonActivity activity, BridgeState bridge) {
        String npcId = stringField(activity, "selectedNpcId");
        if (npcId.isEmpty()) return;
        DungeonParticipationStore participationStore = DungeonParticipationStore.forNpc(activity, npcId);
        DungeonParticipationState participation = participationStore.load();
        DungeonObjective objective = new DungeonObjectiveStore(activity).load(npcId);
        DungeonState dungeonState = dungeonState(activity);

        if (participation.isAccepted() && emergencyDanger(dungeonState)) {
            participation = DungeonParticipationPolicy.emergencyWithdraw(
                    participation,
                    System.currentTimeMillis(),
                    "死にたくない。今の傷と目の前の危険では、これ以上進むのは無理だと判断した。");
            participationStore.save(participation);
        }

        boolean canExecute = DungeonParticipationPolicy.canAutoExecute(participation, objective);
        boolean paused = booleanField(activity, "paused");
        if (!canExecute) {
            if (!paused) {
                setBooleanField(activity, "paused", true);
                bridge.forcedPause = true;
            }
        } else if (bridge.forcedPause) {
            setBooleanField(activity, "paused", false);
            bridge.forcedPause = false;
        }

        if (bridge.pauseButton != null) bridge.pauseButton.setEnabled(canExecute);
        configureGoalButton(activity, bridge.goalButton, participation);
        renderParticipation(bridge.participationView, participation);
        if (!canExecute) {
            TextView action = textField(activity, "actionView");
            if (action != null) {
                if (!participation.isAccepted()) {
                    action.setText("会話でダンジョン参加について相談してください");
                } else {
                    action.setText("参加了承済み · 目的を設定すると攻略を開始します");
                }
            }
        }
    }

    private static boolean emergencyDanger(DungeonState state) {
        if (state == null || state.maxHp <= 0) return false;
        double hpRate = state.hp / (double) state.maxHp;
        if (hpRate > 0.20) return false;
        int enemyDistance = DungeonPerception.nearestVisibleEnemyDistance(
                state, state.playerX, state.playerY);
        return enemyDistance <= 2;
    }

    private static void configureGoalButton(
            DungeonActivity activity,
            Button button,
            DungeonParticipationState participation
    ) {
        if (button == null) return;
        if (participation != null && participation.isAccepted()) {
            if (GOAL_OVERRIDE_TAG.equals(button.getTag())) {
                button.setTag(null);
                DungeonGoalInputBridge.install(activity);
            }
            button.setEnabled(true);
            return;
        }
        if (!GOAL_OVERRIDE_TAG.equals(button.getTag())) {
            button.setTag(GOAL_OVERRIDE_TAG);
            button.setOnClickListener(v -> showConsentNotice(activity, participation));
        }
        button.setEnabled(true);
    }

    private static void showConsentNotice(
            DungeonActivity activity,
            DungeonParticipationState participation
    ) {
        String stance = participation == null ? "未相談" : participation.label();
        String reason = participation == null ? "" : participation.personalReason;
        String message = "このNPCはまだダンジョン参加に同意していません。\n"
                + "現在: " + stance
                + (reason.isEmpty() ? "" : "\n本人の考え: " + reason)
                + "\n\n目的を入力して強制するのではなく、会話で誘い、本人が行く理由を持てるか相談してください。";
        new AlertDialog.Builder(activity)
                .setTitle("参加意思が必要です")
                .setMessage(message)
                .setPositiveButton("会話へ", (dialog, which) -> openConversationWithInvitation(activity))
                .setNegativeButton("閉じる", null)
                .show();
    }

    private static void openConversationWithInvitation(DungeonActivity activity) {
        String npcId = stringField(activity, "selectedNpcId");
        if (!npcId.isEmpty()) {
            new AppUiStateStore(activity).saveConversationRoomId("direct_" + npcId);
            DungeonState state = dungeonState(activity);
            if (state != null) {
                DungeonObjective objective = new DungeonObjectiveStore(activity).load(npcId);
                DungeonInvitationContext invitation = DungeonInvitationContext.fromDungeon(
                        npcId,
                        state,
                        objective,
                        System.currentTimeMillis());
                if (invitation != null) {
                    new DungeonInvitationContextStore(
                            NpcContexts.storage(activity, npcId)).save(invitation);
                }
            }
        }
        invokeNoArgs(activity, "openConversation");
    }

    private static void renderParticipation(TextView view, DungeonParticipationState state) {
        if (view == null) return;
        DungeonParticipationState safe = state == null ? DungeonParticipationState.initial() : state;
        StringBuilder text = new StringBuilder("参加意思: ").append(safe.label());
        if (!safe.personalReason.isEmpty()) {
            text.append("\n本人の理由: ").append(safe.personalReason);
        } else if (DungeonParticipationState.NOT_ASKED.equals(safe.stance)) {
            text.append("\n本人の理由: まだダンジョンへ行く話をしていない");
        }
        view.setText(text.toString());
    }

    private static TextView ensureParticipationView(DungeonActivity activity) {
        try {
            TextView objective = textField(activity, "objectiveView");
            if (objective == null || !(objective.getParent() instanceof LinearLayout)) return null;
            LinearLayout parent = (LinearLayout) objective.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof TextView && "npcbrain_participation_v0413".equals(child.getTag())) {
                    return (TextView) child;
                }
            }
            TextView view = new TextView(activity);
            view.setTag("npcbrain_participation_v0413");
            view.setTextColor(Color.rgb(255, 210, 128));
            view.setTextSize(12);
            view.setMaxLines(3);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(activity, 5);
            int index = Math.max(0, parent.indexOfChild(objective));
            parent.addView(view, index, params);
            return view;
        } catch (Exception ignored) {
            return null;
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

    private static DungeonState dungeonState(DungeonActivity activity) {
        try {
            Field field = DungeonActivity.class.getDeclaredField("state");
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof DungeonState ? (DungeonState) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TextView textField(DungeonActivity activity, String name) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof TextView ? (TextView) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Button buttonField(DungeonActivity activity, String name) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof Button ? (Button) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringField(DungeonActivity activity, String name) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
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

    private static void setBooleanField(DungeonActivity activity, String name, boolean value) {
        try {
            Field field = DungeonActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(activity, value);
        } catch (Exception ignored) {
        }
    }

    private static void invokeNoArgs(DungeonActivity activity, String methodName) {
        try {
            Method method = DungeonActivity.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Exception ignored) {
        }
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class BridgeState {
        final TextView participationView;
        final Button goalButton;
        final Button pauseButton;
        boolean forcedPause;

        BridgeState(TextView participationView, Button goalButton, Button pauseButton) {
            this.participationView = participationView;
            this.goalButton = goalButton;
            this.pauseButton = pauseButton;
        }
    }
}
