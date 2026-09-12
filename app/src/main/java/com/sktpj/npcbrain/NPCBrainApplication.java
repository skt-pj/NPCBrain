package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import java.lang.ref.WeakReference;

public final class NPCBrainApplication extends Application {
    private static WeakReference<NPCBrainApplication> applicationRef = new WeakReference<>(null);
    private static WeakReference<DemoActivityV032> demoActivityRef = new WeakReference<>(null);
    private static volatile boolean demoRoomRefreshRequested;
    private static volatile boolean debugBuild;

    private NpcWorldRuntimeV200 worldRuntime;
    private int startedActivityCount;

    @Override
    public void onCreate() {
        super.onCreate();
        applicationRef = new WeakReference<>(this);
        debugBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        new ReplyTimerStore(this).rearmAll();
        NpcSocialMemoryScheduler.schedule(this);
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
                if (startedActivityCount == 1 && worldRuntime != null) {
                    worldRuntime.start();
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

            @Override public void onActivityPaused(Activity activity) {
                PrimaryUiCoordinator.onPaused(activity);
            }

            @Override public void onActivityStopped(Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
                if (startedActivityCount == 0 && worldRuntime != null) {
                    worldRuntime.stop();
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

    static WorldKernelV210 worldKernel() {
        NPCBrainApplication app = applicationRef.get();
        return app == null || app.worldRuntime == null ? null : app.worldRuntime.kernel();
    }

    static WorldQueryServiceV210 worldQuery() {
        NPCBrainApplication app = applicationRef.get();
        return app == null || app.worldRuntime == null ? null : app.worldRuntime.query();
    }

    private static boolean consumeDemoRoomRefreshRequest() {
        if (!demoRoomRefreshRequested) return false;
        demoRoomRefreshRequested = false;
        return true;
    }

    private void installRuntimeBridges(Activity activity) {
        if (activity instanceof DemoActivityV032) {
            DemoActivityV032 demo = (DemoActivityV032) activity;
            DynamicConversationUiBridge.install(activity);
            NpcPeerConversationUiBridge.install(demo);
            ConversationSendQueueBridge.install(demo);
            ProcessingQueueDemoBridge.install(demo);
            return;
        }
        if (activity instanceof NpcStatusActivity) {
            NpcInnerLifeUiBridge.install((NpcStatusActivity) activity);
            NpcAiUsageUiBridge.install((NpcStatusActivity) activity);
        }
        // DungeonActivity v2.1.1 is a canonical observer. No legacy dungeon execution bridge is
        // installed here: screen lifecycle/select events must never own turns or persistence.
    }
}
