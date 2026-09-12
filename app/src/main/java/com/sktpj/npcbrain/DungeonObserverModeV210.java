package com.sktpj.npcbrain;

import android.widget.Button;

import java.lang.reflect.Field;

/** Prevents the legacy DungeonActivity handler/roster bridge from owning simulation turns. */
final class DungeonObserverModeV210 {
    private DungeonObserverModeV210() {}

    static void install(DungeonActivity activity) {
        if (activity == null) return;
        setBoolean(activity, "paused", true);
        Button pause = button(activity, "pauseButton");
        if (pause != null) {
            pause.setText("世界Runtimeで進行");
            pause.setEnabled(false);
        }
        Button speed = button(activity, "speedButton");
        if (speed != null) {
            speed.setText("共通速度");
            speed.setEnabled(false);
        }
    }

    private static void setBoolean(Object target, String name, boolean value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (Exception ignored) {
        }
    }

    private static Button button(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Button ? (Button) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
