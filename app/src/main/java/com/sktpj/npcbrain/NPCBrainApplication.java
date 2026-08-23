package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;

public final class NPCBrainApplication extends Application {
    private static final String TAB_TAG = "npcbrain_top_tabs_v047";
    private static WeakReference<DemoActivityV032> demoActivityRef = new WeakReference<>(null);

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof DemoActivityV032) {
                    demoActivityRef = new WeakReference<>((DemoActivityV032) activity);
                }
            }

            @Override public void onActivityStarted(Activity activity) {
                injectPrimaryTabs(activity);
            }

            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof DemoActivityV032) {
                    demoActivityRef = new WeakReference<>((DemoActivityV032) activity);
                }
                injectPrimaryTabs(activity);
            }

            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

            @Override public void onActivityDestroyed(Activity activity) {
                DemoActivityV032 current = demoActivityRef.get();
                if (activity == current) demoActivityRef = new WeakReference<>(null);
            }
        });
    }

    static DemoActivityV032 currentDemoActivity() {
        return demoActivityRef.get();
    }

    private void injectPrimaryTabs(Activity activity) {
        if (activity instanceof DemoActivityV032) {
            DemoActivityV032 demo = (DemoActivityV032) activity;
            injectDemoTabs(demo);
            DungeonParticipationChatBridge.install(demo);
        } else if (activity instanceof NpcStatusActivity) {
            injectDungeonIntoStatus((NpcStatusActivity) activity);
        } else if (activity instanceof DungeonActivity) {
            DungeonActivity dungeon = (DungeonActivity) activity;
            DungeonGoalInputBridge.install(dungeon);
            DungeonConsentBridge.install(dungeon);
        }
    }

    private void injectDemoTabs(DemoActivityV032 activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View child = content.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) child;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (TAB_TAG.equals(root.getChildAt(i).getTag())) return;
        }

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setTag(TAB_TAG);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, dp(activity, 4), 0, dp(activity, 8));

        Button conversation = lightTabButton(activity, "会話", true);
        conversation.setEnabled(false);
        tabs.addView(conversation, new LinearLayout.LayoutParams(0, dp(activity, 42), 1f));

        Button status = lightTabButton(activity, "NPC状況", false);
        status.setOnClickListener(v -> activity.startActivity(new Intent(activity, NpcStatusActivity.class)));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
        statusParams.leftMargin = dp(activity, 8);
        tabs.addView(status, statusParams);

        Button dungeon = lightTabButton(activity, "ダンジョン", false);
        dungeon.setOnClickListener(v -> activity.startActivity(new Intent(activity, DungeonActivity.class)));
        LinearLayout.LayoutParams dungeonParams = new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
        dungeonParams.leftMargin = dp(activity, 8);
        tabs.addView(dungeon, dungeonParams);

        int insertAt = Math.min(1, root.getChildCount());
        root.addView(tabs, insertAt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void injectDungeonIntoStatus(NpcStatusActivity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View child = content.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) child;

        for (int i = 0; i < root.getChildCount(); i++) {
            View candidate = root.getChildAt(i);
            if (!(candidate instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) candidate;
            boolean hasConversation = false;
            boolean hasStatus = false;
            boolean hasDungeon = false;
            for (int j = 0; j < row.getChildCount(); j++) {
                View item = row.getChildAt(j);
                if (!(item instanceof Button)) continue;
                String label = ((Button) item).getText().toString();
                if ("会話".equals(label)) hasConversation = true;
                if ("NPC状況".equals(label)) hasStatus = true;
                if ("ダンジョン".equals(label)) hasDungeon = true;
            }
            if (!hasConversation || !hasStatus || hasDungeon) continue;

            Button dungeon = darkTabButton(activity, "ダンジョン", false);
            dungeon.setOnClickListener(v -> activity.startActivity(new Intent(activity, DungeonActivity.class)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
            params.leftMargin = dp(activity, 8);
            row.addView(dungeon, params);
            return;
        }
    }

    private Button lightTabButton(Activity activity, String label, boolean selected) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? Color.rgb(26, 78, 145) : Color.rgb(70, 74, 84));
        button.setBackgroundColor(selected ? Color.rgb(225, 236, 252) : Color.rgb(241, 243, 247));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
        return button;
    }

    private Button darkTabButton(Activity activity, String label, boolean selected) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? Color.WHITE : Color.rgb(188, 203, 222));
        button.setBackgroundColor(selected ? Color.rgb(42, 91, 156) : Color.rgb(21, 33, 49));
        return button;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
