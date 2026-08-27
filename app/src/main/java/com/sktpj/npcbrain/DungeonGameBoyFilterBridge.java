package com.sktpj.npcbrain;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

final class DungeonGameBoyFilterBridge {
    private static final String TAG = "npcbrain_dungeon_gb_filter_v0423";

    private DungeonGameBoyFilterBridge() {
    }

    static void install(DungeonActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Object value = field(activity, "boardView");
            if (!(value instanceof DungeonBoardView)) return;
            DungeonBoardView board = (DungeonBoardView) value;
            ViewParent currentParent = board.getParent();
            if (currentParent instanceof FilterLayout) return;
            if (!(currentParent instanceof ViewGroup)) return;

            ViewGroup parent = (ViewGroup) currentParent;
            int index = parent.indexOfChild(board);
            if (index < 0) return;
            ViewGroup.LayoutParams outerParams = board.getLayoutParams();
            parent.removeViewAt(index);

            FilterLayout filter = new FilterLayout(activity);
            filter.setTag(TAG);
            filter.setClipChildren(true);
            filter.setClipToPadding(true);
            parent.addView(filter, index, outerParams);
            filter.addView(board, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception ignored) {
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class FilterLayout extends FrameLayout {
        private final Paint nearestPaint = new Paint();
        private final Rect filteredRect = new Rect(
                0, 0, DungeonGameBoyFilter.TARGET_WIDTH, DungeonGameBoyFilter.TARGET_HEIGHT);
        private final Rect outputRect = new Rect();
        private final int[] pixels = new int[
                DungeonGameBoyFilter.TARGET_WIDTH * DungeonGameBoyFilter.TARGET_HEIGHT];

        private Bitmap sourceBitmap;
        private Canvas sourceCanvas;
        private final Bitmap filteredBitmap;
        private final Canvas filteredCanvas;

        FilterLayout(DungeonActivity activity) {
            super(activity);
            nearestPaint.setAntiAlias(false);
            nearestPaint.setFilterBitmap(false);
            nearestPaint.setDither(false);
            filteredBitmap = Bitmap.createBitmap(
                    DungeonGameBoyFilter.TARGET_WIDTH,
                    DungeonGameBoyFilter.TARGET_HEIGHT,
                    Bitmap.Config.ARGB_8888);
            filteredCanvas = new Canvas(filteredBitmap);
        }

        @Override
        public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
            ViewParent parent = super.invalidateChildInParent(location, dirty);
            postInvalidateOnAnimation();
            return parent;
        }

        @Override
        public void onDescendantInvalidated(View child, View target) {
            super.onDescendantInvalidated(child, target);
            postInvalidateOnAnimation();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                super.dispatchDraw(canvas);
                return;
            }
            ensureSource(width, height);

            sourceBitmap.eraseColor(Color.TRANSPARENT);
            super.dispatchDraw(sourceCanvas);

            filteredBitmap.eraseColor(Color.TRANSPARENT);
            filteredCanvas.drawBitmap(sourceBitmap, null, filteredRect, nearestPaint);
            filteredBitmap.getPixels(
                    pixels,
                    0,
                    DungeonGameBoyFilter.TARGET_WIDTH,
                    0,
                    0,
                    DungeonGameBoyFilter.TARGET_WIDTH,
                    DungeonGameBoyFilter.TARGET_HEIGHT);
            DungeonGameBoyFilter.apply(
                    pixels,
                    DungeonGameBoyFilter.TARGET_WIDTH,
                    DungeonGameBoyFilter.TARGET_HEIGHT);
            filteredBitmap.setPixels(
                    pixels,
                    0,
                    DungeonGameBoyFilter.TARGET_WIDTH,
                    0,
                    0,
                    DungeonGameBoyFilter.TARGET_WIDTH,
                    DungeonGameBoyFilter.TARGET_HEIGHT);

            outputRect.set(0, 0, width, height);
            canvas.drawBitmap(filteredBitmap, filteredRect, outputRect, nearestPaint);
        }

        private void ensureSource(int width, int height) {
            if (sourceBitmap != null
                    && sourceBitmap.getWidth() == width
                    && sourceBitmap.getHeight() == height) {
                return;
            }
            if (sourceBitmap != null) sourceBitmap.recycle();
            sourceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            sourceCanvas = new Canvas(sourceBitmap);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (sourceBitmap != null) {
                sourceBitmap.recycle();
                sourceBitmap = null;
                sourceCanvas = null;
            }
        }
    }
}
