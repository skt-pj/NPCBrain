package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Keeps legacy DemoActivity home controls registry-driven without changing cognition/runtime ownership. */
final class DynamicConversationUiBridge {
    private DynamicConversationUiBridge() {}

    static void install(Activity activity) {
        if (!(activity instanceof DemoActivityV032)) return;
        Object candidate = field(activity, "menuButton");
        if (!(candidate instanceof Button)) return;
        Button menuButton = (Button) candidate;
        menuButton.setOnClickListener(v -> showMenu(activity, v));
    }

    private static void showMenu(Activity activity, View anchor) {
        String currentRoomId = stringField(activity, "currentRoomId");
        if (!currentRoomId.isEmpty()) return;
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenu().add("AI設定");
        popup.getMenu().add("NPC人格設定");
        popup.getMenu().add("長期記憶を見る");
        popup.getMenu().add("会話履歴を消去");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("AI設定".equals(title)) {
                invoke(activity, "showAiSettingsDialog", new Class<?>[0]);
            } else if ("NPC人格設定".equals(title)) {
                showNpcPicker(activity, "人格を設定するNPC", "showPersonalityDialog");
            } else if ("長期記憶を見る".equals(title)) {
                showNpcPicker(activity, "記憶を見るNPC", "showMemoryDialog");
            } else if ("会話履歴を消去".equals(title)) {
                invoke(activity, "confirmClearConversations", new Class<?>[0]);
            }
            return true;
        });
        popup.show();
    }

    private static void showNpcPicker(Activity activity, String title, String methodName) {
        List<String> ids = new NpcRegistryStore(activity).activeNpcIds();
        if (ids.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage("利用できるNPCがいません。")
                    .setPositiveButton("閉じる", null)
                    .show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (String npcId : ids) {
            String name = new CharacterStateStore(NpcContexts.storage(activity, npcId)).displayName();
            if (name == null || name.trim().isEmpty() || "NPC".equals(name.trim())) {
                name = npcId.toUpperCase(java.util.Locale.US);
            }
            labels.add(name.trim() + "  (" + npcId + ")");
        }
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < ids.size()) {
                        invoke(activity, methodName, new Class<?>[]{String.class}, ids.get(which));
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
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
        return value == null ? "" : value.toString().trim();
    }

    private static void invoke(Object target, String methodName, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, types);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (Exception ignored) {
        }
    }
}