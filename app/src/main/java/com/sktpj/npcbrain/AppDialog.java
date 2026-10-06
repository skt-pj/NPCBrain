package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

/** Shared popup entry point so dialog contrast never depends on the system theme. */
final class AppDialog {
    private AppDialog() {
    }

    static AlertDialog.Builder builder(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        return new ThemedBuilder(context);
    }

    /**
     * AlertDialog's theme does not restyle a custom View that was already created with an
     * Activity context. Normalize transparent custom content here while preserving authored
     * card/badge surfaces that already provide their own contrast.
     */
    private static final class ThemedBuilder extends AlertDialog.Builder {
        ThemedBuilder(Context context) {
            super(context, R.style.NpcBrainAlertDialogTheme);
        }

        @Override
        public AlertDialog.Builder setView(View view) {
            styleCustomContent(view);
            return super.setView(view);
        }

    }

    private static void styleCustomContent(View root) {
        if (root == null) return;
        if (root instanceof ViewGroup) {
            root.setBackgroundColor(AppUiTheme.APP_SURFACE);
        }
        styleNode(root, false, true);
    }

    private static void styleNode(View view, boolean insideExplicitSurface, boolean root) {
        boolean ownContainerSurface = !root
                && view instanceof ViewGroup
                && view.getBackground() != null;
        boolean ownTextSurface = view instanceof TextView
                && !(view instanceof EditText)
                && !(view instanceof CompoundButton)
                && view.getBackground() != null;
        boolean explicitSurface = insideExplicitSurface || ownContainerSurface || ownTextSurface;

        if (view instanceof EditText && !insideExplicitSurface) {
            EditText input = (EditText) view;
            input.setTextColor(AppUiTheme.APP_TEXT);
            input.setHintTextColor(AppUiTheme.APP_MUTED);
            input.setBackgroundTintList(ColorStateList.valueOf(AppUiTheme.APP_MUTED));
        } else if (view instanceof CompoundButton && !insideExplicitSurface) {
            CompoundButton option = (CompoundButton) view;
            option.setTextColor(AppUiTheme.APP_TEXT);
            option.setButtonTintList(new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{}
                    },
                    new int[]{
                            AppUiTheme.APP_ACCENT,
                            AppUiTheme.APP_MUTED
                    }));
        } else if (view instanceof TextView && !explicitSurface) {
            ((TextView) view).setTextColor(AppUiTheme.APP_TEXT);
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            styleNode(group.getChildAt(i), explicitSurface, false);
        }
    }
}
