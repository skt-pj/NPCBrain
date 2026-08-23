package com.sktpj.npcbrain;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

final class AppWindowChrome {
    private AppWindowChrome() {}

    static void apply(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        window.setNavigationBarColor(AppUiTheme.NAV_BACKGROUND);
        if (Build.VERSION.SDK_INT >= 26) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            decor.setSystemUiVisibility(flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        if (activity instanceof NpcManagerActivity) {
            hideLegacyPeerBack(activity.findViewById(android.R.id.content));
        }
    }

    private static void hideLegacyPeerBack(View view) {
        if (view == null) return;
        if (view instanceof Button) {
            CharSequence text = ((Button) view).getText();
            if (text != null && "‹ 会話".equals(text.toString().trim())) {
                view.setVisibility(View.GONE);
                return;
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            hideLegacyPeerBack(group.getChildAt(i));
        }
    }
}
