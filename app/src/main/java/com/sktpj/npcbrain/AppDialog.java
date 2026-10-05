package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.content.Context;

/** Shared popup entry point so dialog contrast never depends on the system theme. */
final class AppDialog {
    private AppDialog() {
    }

    static AlertDialog.Builder builder(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        return new AlertDialog.Builder(context, R.style.NpcBrainAlertDialogTheme);
    }
}
