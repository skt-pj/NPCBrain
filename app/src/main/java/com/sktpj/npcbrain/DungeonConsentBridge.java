package com.sktpj.npcbrain;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
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
        BridgeState state = new BridgeState(
                new DungeonNpcStateCoordinator(activity),
                participationView,
                goalButton,
                pauseButton);
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
        DungeonNpcStateCoordinator.Snapshot snapshot = bridge.coordinator.snapshot(npcId);
        configureGoalButton(activity, bridge.goalButton);
        syncForegroundExecution(activity, bridge, snapshot);
        renderParticipation(bridge.participationView, snapshot.participation);
    }

    private static void syncForegroundExecution(
            DungeonActivity activity,
            BridgeState bridge,
            DungeonNpcStateCoordinator.Snapshot snapshot
    ) {
        boolean accepted = snapshot != null && snapshot.participationAccepted();
        boolean paused = booleanField(activity, "paused");
        if (!accepted) {
            if (!paused) {
                setBooleanField(activity, "paused", true);
                bridge.pauseOwnedByParticipation = true;
            }
            if (bridge.pauseButton != null) {
                bridge.pauseButton.setEnabled(false);
                bridge.pauseButton.setText("再開");
            }
            return;
        }

        if (bridge.pauseButton != null) bridge.pauseButton.setEnabled(true);
        if (bridge.pauseOwnedByParticipation) {
            setBooleanField(activity, "paused", false);
            bridge.pauseOwnedByParticipation = false;
            if (bridge.pauseButton != null) bridge.pauseButton.setText("一時停止");
        } else if (bridge.pauseButton != null) {
            bridge.pauseButton.setText(paused ? "再開" : "一時停止");
        }
    }

    private static void configureGoalButton(DungeonActivity activity, Button button) {
        if (button == null) return;
        if (GOAL_OVERRIDE_TAG.equals(button.getTag())) button.setTag(null);
        DungeonGoalInputBridge.install(activity);
        button.setEnabled(true);
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

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class BridgeState {
        final DungeonNpcStateCoordinator coordinator;
        final TextView participationView;
        final Button goalButton;
        final Button pauseButton;
        boolean pauseOwnedByParticipation;

        BridgeState(
                DungeonNpcStateCoordinator coordinator,
                TextView participationView,
                Button goalButton,
                Button pauseButton
        ) {
            this.coordinator = coordinator;
            this.participationView = participationView;
            this.goalButton = goalButton;
            this.pauseButton = pauseButton;
        }
    }
}
