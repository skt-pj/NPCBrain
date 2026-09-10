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
    private NpcWorldRuntimeV200 worldRuntime;
    private int startedActivityCount;

    @Override
    public void onCreate() {
        super.onCreate();
        debugBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        new ReplyTimerStore(this).rearmAll();
        NpcSocialMemoryScheduler.schedule(this);
        new DungeonPresenceStore(this).activePresentNpcIds();
        innerLifeRuntime = new NpcInnerLifeRuntime(this);
        // Constructed at process start so canonical world/dungeon observation state exists before
        // any top-level Activity can attempt to display it.
        worldRuntime = new NpcWorldRuntimeV200(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof DemoActivityV032) {
                    demoActivityRef = new WeakReference<>((DemoActivityV032) activity);
                }
                PrimaryUiCoordinator.onCreated(activity, state);
                AppWindowChrome.apply(activity);
                PrimaryFooterBridge.install(activity);
            }

            @Override public void onActivityStarted(Activity activity) {
                startedActivityCount++;
                if (startedActivityCount == 1) {
                    if (innerLifeRuntime != null) innerLifeRuntime.onForeground();
                    if (worldRuntime != null) worldRuntime.start();
                }
                installRuntimeBridges(activity);
                PrimaryUiCoordinator.onStarted(activity);
                AppWindowChrome.apply(activity);
                PrimaryFooterBridge.install(activity);
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
                PrimaryFooterBridge.install(activity);
            }

            @Override public void onActivityPrePaused(Activity activity) {
                if (activity instanceof DungeonActivity) {
                    DungeonWorldObserverBridge.beforeActivityPause((DungeonActivity) activity);
                }
            }

            @Override public void onActivityPaused(Activity activity) {
                if (activity instanceof DungeonActivity) {
                    DungeonWorldObserverBridge.afterActivityPause((DungeonActivity) activity);
                }
                PrimaryUiCoordinator.onPaused(activity);
            }

            @Override public void onActivityStopped(Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
                if (startedActivityCount == 0) {
                    if (innerLifeRuntime != null) innerLifeRuntime.onBackground();
                    if (worldRuntime != null) worldRuntime.stop();
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
            ProcessingQueueDemoBridge.install((DemoActivityV032) activity);
            return;
        }
        if (activity instanceof NpcStatusActivity) {
            NpcInnerLifeUiBridge.install((NpcStatusActivity) activity);
            NpcAiUsageUiBridge.install((NpcStatusActivity) activity);
        }
        if (activity instanceof DungeonActivity) {
            DungeonActivity dungeon = (DungeonActivity) activity;
            // Install observation ownership first. Later UI bridges may read/operate on the same
            // canonical state, but none of them owns autonomous turn execution.
            DungeonWorldObserverBridge.install(dungeon);
            DungeonGoalInputBridge.install(dungeon);
            DungeonAiStaminaBridge.install(dungeon);
            DungeonRosterUiBridge.install(dungeon);
            DungeonModeSwitchBridge.install(dungeon);
        }
    }
}
