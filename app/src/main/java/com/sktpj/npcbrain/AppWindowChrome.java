package com.sktpj.npcbrain;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;

import java.util.WeakHashMap;

final class AppWindowChrome {
    private static final String PRIMARY_NAV_TAG = "npcbrain_primary_nav_v0420";
    private static final WeakHashMap<View, BasePadding> BASE_PADDING = new WeakHashMap<>();

    private AppWindowChrome() {}

    static void apply(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        window.setStatusBarColor(AppUiTheme.APP_BACKGROUND);
        window.setNavigationBarColor(AppUiTheme.APP_BACKGROUND);

        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);

        if (!usesLocalSafeAreaOwner(activity)) {
            normalizeRootFitsSystemWindows(activity);
            applySafeArea(activity);
            disableLegacyPrimaryNavigationInset(activity);
        }

        if (activity instanceof NpcManagerActivity) {
            hideLegacyPeerBack(activity.findViewById(android.R.id.content));
        }
    }

    private static boolean usesLocalSafeAreaOwner(Activity activity) {
        return activity instanceof DemoActivityV032
                || activity instanceof NpcStatusActivity
                || activity instanceof DungeonActivity
                || activity instanceof CodexActivity;
    }

    private static void normalizeRootFitsSystemWindows(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) content;
        if (group.getChildCount() == 0) return;
        View root = group.getChildAt(0);
        if (root != null && root.getFitsSystemWindows()) root.setFitsSystemWindows(false);
    }

    private static void applySafeArea(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        final BasePadding base;
        synchronized (BASE_PADDING) {
            BasePadding existing = BASE_PADDING.get(content);
            if (existing == null) {
                existing = new BasePadding(
                        content.getPaddingLeft(),
                        content.getPaddingTop(),
                        content.getPaddingRight(),
                        content.getPaddingBottom());
                BASE_PADDING.put(content, existing);
            }
            base = existing;
        }

        if (Build.VERSION.SDK_INT < 20) {
            applyPadding(content, base, 0, 0, 0, 0);
            return;
        }

        content.setOnApplyWindowInsetsListener((view, insets) -> {
            int[] safe = safeInsets(insets);
            applyPadding(view, base, safe[0], safe[1], safe[2], safe[3]);
            return insets;
        });
        content.requestApplyInsets();
    }

    private static void applyPadding(
            View view,
            BasePadding base,
            int safeLeft,
            int safeTop,
            int safeRight,
            int safeBottom
    ) {
        SafeAreaInsetsPolicy.Padding resolved = SafeAreaInsetsPolicy.resolve(
                base.left,
                base.top,
                base.right,
                base.bottom,
                safeLeft,
                safeTop,
                safeRight,
                safeBottom);
        if (view.getPaddingLeft() == resolved.left
                && view.getPaddingTop() == resolved.top
                && view.getPaddingRight() == resolved.right
                && view.getPaddingBottom() == resolved.bottom) {
            return;
        }
        view.setPadding(resolved.left, resolved.top, resolved.right, resolved.bottom);
    }

    @SuppressWarnings("deprecation")
    private static int[] safeInsets(WindowInsets insets) {
        if (insets == null) return new int[]{0, 0, 0, 0};
        if (Build.VERSION.SDK_INT >= 30) return Api30.safeInsets(insets);

        int left = Math.max(0, insets.getSystemWindowInsetLeft());
        int top = Math.max(0, insets.getSystemWindowInsetTop());
        int right = Math.max(0, insets.getSystemWindowInsetRight());
        int bottom = Math.max(0, insets.getSystemWindowInsetBottom());
        if (Build.VERSION.SDK_INT >= 28) {
            int[] cutout = Api28.cutoutInsets(insets);
            left = Math.max(left, cutout[0]);
            top = Math.max(top, cutout[1]);
            right = Math.max(right, cutout[2]);
            bottom = Math.max(bottom, cutout[3]);
        }
        return new int[]{left, top, right, bottom};
    }

    private static void disableLegacyPrimaryNavigationInset(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        View nav = content.findViewWithTag(PRIMARY_NAV_TAG);
        if (nav == null) return;
        if (Build.VERSION.SDK_INT >= 20) nav.setOnApplyWindowInsetsListener(null);
        if (nav.getPaddingBottom() != 0) {
            nav.setPadding(
                    nav.getPaddingLeft(),
                    nav.getPaddingTop(),
                    nav.getPaddingRight(),
                    0);
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

    private static final class BasePadding {
        final int left;
        final int top;
        final int right;
        final int bottom;

        BasePadding(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static final class Api28 {
        private Api28() {}

        static int[] cutoutInsets(WindowInsets insets) {
            android.view.DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout == null) return new int[]{0, 0, 0, 0};
            return new int[]{
                    Math.max(0, cutout.getSafeInsetLeft()),
                    Math.max(0, cutout.getSafeInsetTop()),
                    Math.max(0, cutout.getSafeInsetRight()),
                    Math.max(0, cutout.getSafeInsetBottom())};
        }
    }

    private static final class Api30 {
        private Api30() {}

        static int[] safeInsets(WindowInsets insets) {
            android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[]{
                    Math.max(0, safe.left),
                    Math.max(0, safe.top),
                    Math.max(0, safe.right),
                    Math.max(0, safe.bottom)};
        }
    }
}
