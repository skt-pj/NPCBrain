package com.sktpj.npcbrain;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.WeakHashMap;

/**
 * Presentation compatibility layer for the legacy roster widget.
 * The widget still owns background multi-NPC execution, but legacy consent words are not part of
 * the current dungeon-area UI contract.
 */
final class DungeonRosterPresentationNeutralizer {
    private static final String ROSTER_TAG = "npcbrain_dungeon_roster_v0419";
    private static final long REFRESH_MS = 180L;
    private static final WeakHashMap<DungeonActivity, Runnable> TASKS = new WeakHashMap<>();

    private DungeonRosterPresentationNeutralizer() {
    }

    static synchronized void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (TASKS.containsKey(activity)) return;
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                View content = activity.findViewById(android.R.id.content);
                View roster = findTagged(content, ROSTER_TAG);
                if (roster != null) scrub(roster);
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        TASKS.put(activity, task);
        handler.post(task);
    }

    private static void scrub(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            CharSequence text = button.getText();
            if (text != null) {
                String clean = text.toString()
                        .replace(" · 参加 · ", " · ")
                        .replace(" · 迷い · ", " · ")
                        .replace(" · 拒否 · ", " · ")
                        .replace(" · 撤回 · ", " · ")
                        .replace(" · 未相談 · ", " · ");
                if (!clean.equals(text.toString())) button.setText(clean);
            }
            CharSequence description = button.getContentDescription();
            if (description != null) {
                String clean = description.toString()
                        .replaceAll("、参加意思 [^、]+、", "、");
                if (!clean.equals(description.toString())) button.setContentDescription(clean);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scrub(group.getChildAt(i));
        }
    }

    private static View findTagged(View view, String tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }
}
