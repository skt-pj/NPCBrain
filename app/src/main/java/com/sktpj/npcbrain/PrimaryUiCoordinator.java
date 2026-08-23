package com.sktpj.npcbrain;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PrimaryUiCoordinator {
    private static final String NAV_TAG = "npcbrain_primary_nav_v0420";
    private static final String SPACER_TAG = "npcbrain_primary_nav_spacer_v0420";
    private static final String STATE_STATUS_NPC = "npcbrain.ui.statusNpc";
    private static final String STATE_DUNGEON_NPC = "npcbrain.ui.dungeonNpc";
    private static final String STATE_ROOM = "npcbrain.ui.room";
    private static final String STATE_CODEX_NPC = "npcbrain.ui.codexNpc";

    private PrimaryUiCoordinator() {}

    static void onCreated(Activity activity, Bundle savedState) {
        if (!isTopLevel(activity)) return;
        installNavigation(activity);
        restore(activity, savedState);
    }

    static void onStarted(Activity activity) {
        if (!isTopLevel(activity)) return;
        installNavigation(activity);
    }

    static void onResumed(Activity activity) {
        if (!isTopLevel(activity)) return;
        installNavigation(activity);
        restore(activity, null);
    }

    static void onPaused(Activity activity) {
        if (isTopLevel(activity)) capture(activity, null);
    }

    static void onSaveInstanceState(Activity activity, Bundle outState) {
        if (isTopLevel(activity)) capture(activity, outState);
    }

    private static boolean isTopLevel(Activity activity) {
        return activity instanceof DemoActivityV032
                || activity instanceof NpcStatusActivity
                || activity instanceof DungeonActivity
                || activity instanceof CodexActivity
                || activity instanceof NpcManagerActivity;
    }

    private static void installNavigation(Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View rootView = content.getChildAt(0);
        if (!(rootView instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) rootView;

        hideLegacyNavigationRows(root);
        ensureContentSpacer(activity, root);

        View existing = content.findViewWithTag(NAV_TAG);
        if (existing != null) return;

        LinearLayout shell = new LinearLayout(activity);
        shell.setTag(NAV_TAG);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(AppUiTheme.NAV_BACKGROUND);
        shell.setElevation(dp(activity, 8));

        View divider = new View(activity);
        divider.setBackgroundColor(AppUiTheme.NAV_DIVIDER);
        shell.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 1)));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        shell.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, AppUiTheme.NAV_HEIGHT_DP)));

        String selected = destinationFor(activity);
        for (String id : PrimaryNavigationPolicy.destinationIds()) {
            Button button = navigationButton(activity, id, id.equals(selected));
            button.setOnClickListener(v -> navigate(activity, id));
            row.addView(button, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }

        if (Build.VERSION.SDK_INT >= 23) {
            shell.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(0, 0, 0, Math.max(0, insets.getSystemWindowInsetBottom()));
                return insets;
            });
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        content.addView(shell, params);
        shell.requestApplyInsets();
    }

    private static void hideLegacyNavigationRows(LinearLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View candidate = root.getChildAt(i);
            if (!(candidate instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) candidate;
            int destinationButtons = 0;
            for (int j = 0; j < row.getChildCount(); j++) {
                View child = row.getChildAt(j);
                if (!(child instanceof Button)) continue;
                String label = ((Button) child).getText() == null
                        ? "" : ((Button) child).getText().toString().trim();
                if (PrimaryNavigationPolicy.labels().contains(label)) destinationButtons++;
            }
            if (destinationButtons >= 2) row.setVisibility(View.GONE);
        }
    }

    private static void ensureContentSpacer(Activity activity, LinearLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            if (SPACER_TAG.equals(root.getChildAt(i).getTag())) return;
        }
        View spacer = new View(activity);
        spacer.setTag(SPACER_TAG);
        root.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, AppUiTheme.NAV_HEIGHT_DP + 1)));
    }

    private static Button navigationButton(Activity activity, String id, boolean selected) {
        Button button = new Button(activity);
        button.setText(PrimaryNavigationPolicy.labelFor(id));
        button.setAllCaps(false);
        button.setTextSize(10.5f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(selected ? AppUiTheme.NAV_TEXT : AppUiTheme.NAV_MUTED);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 2), 0, dp(activity, 2), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(navBackground(selected));
        button.setContentDescription(PrimaryNavigationPolicy.labelFor(id)
                + (selected ? "、選択中" : ""));
        return button;
    }

    private static GradientDrawable navBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? AppUiTheme.NAV_SELECTED : AppUiTheme.NAV_BACKGROUND);
        return drawable;
    }

    private static void navigate(Activity current, String destination) {
        if (!PrimaryNavigationPolicy.isDestination(destination)) return;
        if (destination.equals(destinationFor(current))) return;
        capture(current, null);
        Class<? extends Activity> target = activityFor(destination);
        if (target == null) return;
        Intent intent = new Intent(current, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        current.startActivity(intent);
    }

    private static Class<? extends Activity> activityFor(String destination) {
        if (PrimaryNavigationPolicy.CONVERSATION.equals(destination)) return DemoActivityV032.class;
        if (PrimaryNavigationPolicy.STATUS.equals(destination)) return NpcStatusActivity.class;
        if (PrimaryNavigationPolicy.DUNGEON.equals(destination)) return DungeonActivity.class;
        if (PrimaryNavigationPolicy.CODEX.equals(destination)) return CodexActivity.class;
        if (PrimaryNavigationPolicy.MANAGER.equals(destination)) return NpcManagerActivity.class;
        return null;
    }

    private static String destinationFor(Activity activity) {
        if (activity instanceof DemoActivityV032) return PrimaryNavigationPolicy.CONVERSATION;
        if (activity instanceof NpcStatusActivity) return PrimaryNavigationPolicy.STATUS;
        if (activity instanceof DungeonActivity) return PrimaryNavigationPolicy.DUNGEON;
        if (activity instanceof CodexActivity) return PrimaryNavigationPolicy.CODEX;
        if (activity instanceof NpcManagerActivity) return PrimaryNavigationPolicy.MANAGER;
        return "";
    }

    private static void capture(Activity activity, Bundle outState) {
        AppUiStateStore store = new AppUiStateStore(activity);
        if (activity instanceof DemoActivityV032) {
            String room = stringField(activity, "currentRoomId");
            store.saveConversationRoomId(room);
            if (outState != null) outState.putString(STATE_ROOM, room);
            return;
        }
        if (activity instanceof NpcStatusActivity) {
            String npcId = normalizedNpc(stringField(activity, "selectedNpcId"));
            store.saveFocusedNpcId(npcId);
            if (outState != null) outState.putString(STATE_STATUS_NPC, npcId);
            return;
        }
        if (activity instanceof DungeonActivity) {
            String npcId = normalizedNpc(stringField(activity, "selectedNpcId"));
            store.saveDungeonNpcId(npcId);
            store.saveFocusedNpcId(npcId);
            if (outState != null) outState.putString(STATE_DUNGEON_NPC, npcId);
            return;
        }
        if (activity instanceof CodexActivity) {
            String npcId = normalizedNpc(stringField(activity, "selectedNpcId"));
            store.saveCodexNpcId(npcId);
            if (outState != null) outState.putString(STATE_CODEX_NPC, npcId);
        }
    }

    private static void restore(Activity activity, Bundle savedState) {
        AppUiStateStore store = new AppUiStateStore(activity);
        if (activity instanceof DemoActivityV032) {
            List<String> rooms = conversationRooms(activity);
            String saved = savedState == null ? "" : savedState.getString(STATE_ROOM, "");
            String desired = UiSelectionPolicy.resolveRoom(saved, store.conversationRoomId(), rooms);
            String current = stringField(activity, "currentRoomId");
            if (!desired.isEmpty() && !desired.equals(current)) {
                invoke(activity, "openRoom", new Class<?>[]{String.class}, desired);
            } else if (desired.isEmpty() && !current.isEmpty() && rooms.contains(current)) {
                desired = current;
            } else if (desired.isEmpty() && !current.isEmpty()) {
                invoke(activity, "showRoomList", new Class<?>[0]);
            }
            store.saveConversationRoomId(desired);
            return;
        }
        if (activity instanceof NpcStatusActivity) {
            List<String> candidates = new NpcRegistryStore(activity).npcIds();
            String saved = savedState == null ? "" : savedState.getString(STATE_STATUS_NPC, "");
            String desired = UiSelectionPolicy.resolve(saved, store.focusedNpcId(), candidates);
            if (!desired.isEmpty() && !desired.equals(stringField(activity, "selectedNpcId"))) {
                invoke(activity, "selectNpc", new Class<?>[]{String.class}, desired);
            }
            if (!desired.isEmpty()) store.saveFocusedNpcId(desired);
            return;
        }
        if (activity instanceof DungeonActivity) {
            List<String> active = new DungeonRosterStore(activity).activeNpcIds();
            String saved = savedState == null ? "" : savedState.getString(STATE_DUNGEON_NPC, "");
            String desired = UiSelectionPolicy.resolveDungeon(
                    saved, store.dungeonNpcId(), store.focusedNpcId(), active);
            if (!desired.isEmpty() && !desired.equals(stringField(activity, "selectedNpcId"))) {
                invoke(activity, "selectNpc", new Class<?>[]{String.class}, desired);
            }
            if (!desired.isEmpty()) {
                store.saveDungeonNpcId(desired);
                store.saveFocusedNpcId(desired);
            }
            return;
        }
        if (activity instanceof CodexActivity) {
            List<String> records = archiveNpcIds(activity);
            String saved = savedState == null ? "" : savedState.getString(STATE_CODEX_NPC, "");
            String desired = UiSelectionPolicy.resolve(saved, store.codexNpcId(), records);
            if (!desired.isEmpty() && !desired.equals(stringField(activity, "selectedNpcId"))) {
                setField(activity, "selectedNpcId", desired);
                invoke(activity, "renderArchive", new Class<?>[0]);
            }
            if (!desired.isEmpty()) store.saveCodexNpcId(desired);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> conversationRooms(Activity activity) {
        Object runtime = field(activity, "demoRuntime");
        if (runtime == null) return Collections.emptyList();
        try {
            Method method = runtime.getClass().getDeclaredMethod("roomIds");
            method.setAccessible(true);
            Object value = method.invoke(runtime);
            if (value instanceof List) return new ArrayList<>((List<String>) value);
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private static List<String> archiveNpcIds(Activity activity) {
        List<String> ids = new ArrayList<>();
        try {
            for (NpcArchiveStore.Record record : new NpcArchiveStore(activity).records()) {
                if (record != null && record.npcId != null && !record.npcId.trim().isEmpty()) {
                    ids.add(record.npcId.trim());
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private static String normalizedNpc(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            return NpcId.of(value).value();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringField(Object target, String name) {
        Object value = field(target, name);
        return value == null ? "" : value.toString();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {
        }
    }

    private static void invoke(Object target, String methodName, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, types);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (Exception ignored) {
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
