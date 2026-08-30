package com.sktpj.npcbrain;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

final class DungeonEconomyBridge {
    private static final String TAG = "npcbrain_dungeon_economy_v042";
    private static final long REFRESH_MS = 350L;
    private static final long PARTY_RECONCILE_MS = 900L;
    private static final long AUTONOMY_CHECK_MS = 10_000L;

    private DungeonEconomyBridge() {
    }

    static void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            installChestOverlay(activity);
            Object hpValue = field(activity, "hpBar");
            if (!(hpValue instanceof android.widget.ProgressBar)) return;
            android.widget.ProgressBar hpBar = (android.widget.ProgressBar) hpValue;
            ViewParent parent = hpBar.getParent();
            if (!(parent instanceof LinearLayout)) return;
            LinearLayout hud = (LinearLayout) parent;
            for (int i = 0; i < hud.getChildCount(); i++) {
                if (TAG.equals(hud.getChildAt(i).getTag())) return;
            }

            LinearLayout panel = new LinearLayout(activity);
            panel.setTag(TAG);
            panel.setOrientation(LinearLayout.VERTICAL);

            TextView summary = new TextView(activity);
            summary.setTextColor(Color.rgb(222, 231, 197));
            summary.setTextSize(10);
            summary.setTypeface(Typeface.DEFAULT_BOLD);
            panel.addView(summary, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView latest = new TextView(activity);
            latest.setTextColor(Color.rgb(158, 181, 141));
            latest.setTextSize(9);
            latest.setMaxLines(1);
            LinearLayout.LayoutParams latestParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            latestParams.topMargin = dp(activity, 2);
            panel.addView(latest, latestParams);

            int hpIndex = hud.indexOfChild(hpBar);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(activity, 5);
            hud.addView(panel, Math.min(hpIndex + 1, hud.getChildCount()), params);

            DungeonInventoryStore inventory = new DungeonInventoryStore(activity);
            NpcWalletStore wallet = new NpcWalletStore(activity);
            DungeonStore dungeonStore = new DungeonStore(activity);
            DungeonRosterStore roster = new DungeonRosterStore(activity);
            DungeonEconomyRuntime economy = new DungeonEconomyRuntime(activity);
            DungeonPartyCoordinator party = new DungeonPartyCoordinator(activity);
            DungeonAutonomyRuntime autonomy = new DungeonAutonomyRuntime(activity);

            Runnable refresh = new Runnable() {
                long lastPartyMs;
                long lastAutonomyMs;

                @Override
                public void run() {
                    if (panel.getParent() == null || activity.isFinishing() || activity.isDestroyed()) return;
                    long now = System.currentTimeMillis();
                    String selected = selectedNpcId(activity);
                    if (now - lastAutonomyMs >= AUTONOMY_CHECK_MS) {
                        autonomy.evaluateAndJoin(now);
                        lastAutonomyMs = now;
                    }
                    if (now - lastPartyMs >= PARTY_RECONCILE_MS) {
                        party.reconcile(selected);
                        lastPartyMs = now;
                    }

                    boolean selectedChanged = false;
                    List<String> members = roster.activeNpcIds();
                    for (String npcId : members) {
                        DungeonState member = npcId.equals(selected)
                                ? selectedState(activity) : dungeonStore.load(npcId);
                        boolean changed = economy.process(npcId, member, now);
                        if (changed && npcId.equals(selected)) selectedChanged = true;
                    }

                    List<DungeonItem> items = inventory.load(selected);
                    long money = wallet.balance(selected);
                    long appraisal = inventory.totalAppraisedValue(selected);
                    summary.setText("所持金 " + yen(money)
                            + " · 所持品 " + items.size() + "件"
                            + " · 評価額 " + yen(appraisal));
                    DungeonItem last = inventory.latest(selected);
                    latest.setText(last == null
                            ? "直近取得: なし"
                            : "直近取得: " + last.name + "（" + yen(last.value) + "）");
                    panel.setContentDescription(summary.getText() + "。" + latest.getText());
                    if (selectedChanged) invokeRender(activity);
                    invalidateChestOverlay(activity);
                    panel.postDelayed(this, REFRESH_MS);
                }
            };
            panel.post(refresh);
        } catch (Exception ignored) {
        }
    }

    private static void installChestOverlay(DungeonActivity activity) {
        try {
            Object value = field(activity, "boardView");
            if (!(value instanceof DungeonBoardView)) return;
            DungeonBoardView board = (DungeonBoardView) value;
            ViewParent parent = board.getParent();
            if (!(parent instanceof FrameLayout)) return;
            FrameLayout frame = (FrameLayout) parent;
            for (int i = 0; i < frame.getChildCount(); i++) {
                if (DungeonChestOverlayView.TAG.equals(frame.getChildAt(i).getTag())) return;
            }
            DungeonChestOverlayView overlay = new DungeonChestOverlayView(activity, board);
            overlay.setTag(DungeonChestOverlayView.TAG);
            frame.addView(overlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception ignored) {
        }
    }

    private static void invalidateChestOverlay(DungeonActivity activity) {
        try {
            Object value = field(activity, "boardView");
            if (!(value instanceof DungeonBoardView)) return;
            ViewParent parent = ((DungeonBoardView) value).getParent();
            if (!(parent instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (DungeonChestOverlayView.TAG.equals(child.getTag())) child.invalidate();
            }
        } catch (Exception ignored) {
        }
    }

    private static String yen(long value) {
        return "¥" + String.format(Locale.JAPAN, "%,d", Math.max(0L, value));
    }

    private static String selectedNpcId(DungeonActivity activity) {
        try {
            Object value = field(activity, "selectedNpcId");
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return NpcId.of((String) value).value();
            }
        } catch (Exception ignored) {
        }
        return "npc1";
    }

    private static DungeonState selectedState(DungeonActivity activity) {
        try {
            Object value = field(activity, "state");
            return value instanceof DungeonState ? (DungeonState) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void invokeRender(DungeonActivity activity) {
        try {
            Method method = DungeonActivity.class.getDeclaredMethod("render");
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Exception ignored) {
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int dp(DungeonActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
