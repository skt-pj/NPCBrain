package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import java.lang.ref.WeakReference;

public final class NPCBrainApplication extends Application {
    private static WeakReference<DemoActivityV032> demoActivityRef = new WeakReference<>(null);
    private static volatile boolean demoRoomRefreshRequested;
    private static volatile boolean debugBuild;

    private NpcInnerLifeRuntime innerLifeRuntime;
    private int startedActivityCount;

    @Override
    public void onCreate() {
        super.onCreate();
        debugBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        new ReplyTimerStore(this).rearmAll();
        NpcSocialMemoryScheduler.schedule(this);
        // Snapshot legacy dungeon participants before DungeonActivity can generate a new selected
        // actor. From v0.4.43 presence is independent from the user-invited party roster.
        new DungeonPresenceStore(this).activePresentNpcIds();
        innerLifeRuntime = new NpcInnerLifeRuntime(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof DemoActivityV032) {
                    demoActivityRef = new WeakReference<>((DemoActivityV032) activity);
                }
                PrimaryUiCoordinator.onCreated(activity, state);
                AppWindowChrome.apply(activity);
            }

            @Override public void onActivityStarted(Activity activity) {
                startedActivityCount++;
                if (startedActivityCount == 1 && innerLifeRuntime != null) {
                    innerLifeRuntime.onForeground();
                }
                installRuntimeBridges(activity);
                PrimaryUiCoordinator.onStarted(activity);
                AppWindowChrome.apply(activity);
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
                AppWindowChrome.apply(activity);
            }

            @Override public void onActivityPaused(Activity activity) {
                PrimaryUiCoordinator.onPaused(activity);
            }

            @Override public void onActivityStopped(Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
                if (startedActivityCount == 0 && innerLifeRuntime != null) {
                    innerLifeRuntime.onBackground();
                }
            }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
                PrimaryUiCoordinator.onSaveInstanceState(activity, state);
            }

            @Override public void onActivityDestroyed(Activity activity) {
                DemoActivityV032 current = demoActivityRef.get();
                if (activity == current) demoActivityRef = new WeakReference<>(null);
                if (activity instanceof NpcStatusActivity) {
                    NpcAiUsageUiBridge.uninstall((NpcStatusActivity) activity);
                }
            }
        });
    }

    static boolean isDebugBuild() {
        return debugBuild;
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
            DynamicConversationUiBridge.install(activity);
            ConversationSendQueueBridge.install((DemoActivityV032) activity);
            return;
        }
        if (activity instanceof NpcStatusActivity) {
            NpcInnerLifeUiBridge.install((NpcStatusActivity) activity);
            NpcAiUsageUiBridge.install((NpcStatusActivity) activity);
        }
        if (activity instanceof DungeonActivity) {
            DungeonActivity dungeon = (DungeonActivity) activity;
            DungeonGoalInputBridge.install(dungeon);
            DungeonAiStaminaBridge.install(dungeon);
            DungeonRosterBridge.install(dungeon);
            DungeonModeSwitchBridge.install(dungeon);
            DungeonSoloProgressBridge.install(dungeon);
        }
    }
}
