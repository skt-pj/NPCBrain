package com.sktpj.npcbrain;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.PopupMenu;

/** Shared popup-menu entry point with the NPCBrain dark popup theme. */
final class AppPopupMenu {
    private AppPopupMenu() {
    }

    static PopupMenu create(Context context, View anchor) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (anchor == null) throw new IllegalArgumentException("anchor is required");
        Context themed = new ContextThemeWrapper(context, R.style.NpcBrainPopupTheme);
        return new PopupMenu(themed, anchor);
    }
}
