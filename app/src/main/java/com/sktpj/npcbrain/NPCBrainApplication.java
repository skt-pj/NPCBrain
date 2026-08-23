package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;

public final class NPCBrainApplication extends Application {
    private static WeakReference<DemoActivityV032> demoActivityRef = new WeakReference<>(null);
    private static volatile boolean demoRoomRefreshRequested;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof DemoActivityV032) {
                    demoActivityRef = new WeakReference<>((DemoActivityV032) activity);
                }
                PrimaryUiCoordinator.onCreated(activity, state);
            }

            @Override public void onActivityStarted(Activity activity) {
                installRuntimeBridges(activity);
                PrimaryUiCoordinator.onStarted(activity);
            }

            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof DemoActivityV032) {
                    demoActivityRef = new WeakReference<>((DemoActivityV032) activity);
                    if (consumeDemoRoomRefreshRequest()) {
                        activity.recreate();
                        return;
                    }
                }
                installRuntimeBridges(activity);
                PrimaryUiCoordinator.onResumed(activity);
            }

            @Override public void onActivityPaused(Activity activity) {
                PrimaryUiCoordinator.onPaused(activity);
            }

            @Override public void onActivityStopped(Activity activity) {}

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
                PrimaryUiCoordinator.onSaveInstanceState(activity, state);
            }

            @Override public void onActivityDestroyed(Activity activity) {
                DemoActivityV032 current = demoActivityRef.get();
                if (activity == current) demoActivityRef = new WeakReference<>(null);
            }
        });
    }

    static DemoActivityV032 currentDemoActivity() {
        return demoActivityRef.get();
    }

    static void requestDemoRoomRefresh() {
        demoRoomRefreshRequested = true;
    }

    private static boolean consumeDemoRoomRefreshRequest() {
        if (!demoRoomRefreshRequested) return false;
        demoRoomRefreshRequested = false;
        return true;
    }

    private void installRuntimeBridges(Activity activity) {
        if (activity instanceof DemoActivityV032) {
            DungeonParticipationChatBridge.install((DemoActivityV032) activity);
            return;
        }
        if (activity instanceof DungeonActivity) {
            DungeonActivity dungeon = (DungeonActivity) activity;
            DungeonGoalInputBridge.install(dungeon);
            DungeonConsentBridge.install(dungeon);
            DungeonAiStaminaBridge.install(dungeon);
            DungeonRosterBridge.install(dungeon);
        }
    }
}
