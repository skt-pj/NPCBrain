package com.sktpj.npcbrain;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;

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
    }
}
