package com.sktpj.npcbrain;

import android.content.Context;
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
import java.util.List;

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

            FrameLayout filter = filteredSurface(activity, board, true);
            filter.setTag(TAG);
            parent.addView(filter, index, outerParams);
        } catch (Exception ignored) {
        }
    }

    static FrameLayout filteredSurface(
            Context context,
            DungeonBoardView board,
            boolean refreshSharedState
    ) {
        if (context == null || board == null) {
            throw new IllegalArgumentException("context and board are required");
        }
        ViewParent parent = board.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(board);
        }
        FilterLayout filter = new FilterLayout(context, board, refreshSharedState);
        filter.setClipChildren(true);
        filter.setClipToPadding(true);
        filter.addView(board, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return filter;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int intField(Object target, String name, int fallback) {
        try {
            Object value = field(target, name);
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class FilterLayout extends FrameLayout {
        private final Paint nearestPaint = new Paint();
        private final Paint peerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect filteredRect = new Rect(
                0, 0, DungeonGameBoyFilter.TARGET_WIDTH, DungeonGameBoyFilter.TARGET_HEIGHT);
        private final Rect outputRect = new Rect();
        private final int[] pixels = new int[
                DungeonGameBoyFilter.TARGET_WIDTH * DungeonGameBoyFilter.TARGET_HEIGHT];
        private final DungeonBoardView board;
        private final boolean refreshSharedState;

        private Bitmap sourceBitmap;
        private Canvas sourceCanvas;
        private final Bitmap filteredBitmap;
        private final Canvas filteredCanvas;

        FilterLayout(Context context, DungeonBoardView board, boolean refreshSharedState) {
            super(context);
            this.board = board;
            this.refreshSharedState = refreshSharedState;
            nearestPaint.setAntiAlias(false);
            nearestPaint.setFilterBitmap(false);
            nearestPaint.setDither(false);
            peerPaint.setStyle(Paint.Style.FILL);
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
            if (refreshSharedState) refreshSharedState();

            sourceBitmap.eraseColor(Color.TRANSPARENT);
            super.dispatchDraw(sourceCanvas);
            drawVisiblePeers(sourceCanvas, width, height);

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

        private void refreshSharedState() {
            try {
                Object value = field(board, "state");
                if (value instanceof DungeonState) {
                    DungeonStore.refreshSharedWorldForTurn((DungeonState) value);
                }
            } catch (Exception ignored) {
            }
        }

        private void drawVisiblePeers(Canvas canvas, int width, int height) {
            DungeonState state;
            try {
                Object value = field(board, "state");
                if (!(value instanceof DungeonState)) return;
                state = (DungeonState) value;
            } catch (Exception ignored) {
                return;
            }
            List<DungeonActorContext> peers = DungeonStore.visiblePeerCandidates(state);
            if (peers.isEmpty()) return;

            float density = getResources().getDisplayMetrics().density;
            float minCell = 32f * density;
            float ideal = Math.min(width / 9f, height / 10.5f);
            float cell = Math.max(minCell, Math.min(52f * density, ideal));
            int cols = oddClamp((int) Math.floor(width / cell), 7, 13);
            int rows = oddClamp((int) Math.floor(height / cell), 7, 13);
            cell = Math.min(width / (float) cols, height / (float) rows);
            int startX = intField(board, "viewportStartX",
                    DungeonViewportPolicy.initialStart(state.playerX, cols, state.width));
            int startY = intField(board, "viewportStartY",
                    DungeonViewportPolicy.initialStart(state.playerY, rows, state.height));
            int endX = Math.min(state.width, startX + cols);
            int endY = Math.min(state.height, startY + rows);
            float boardWidth = Math.min(state.width, cols) * cell;
            float boardHeight = Math.min(state.height, rows) * cell;
            float left = (width - boardWidth) / 2f;
            float top = (height - boardHeight) / 2f;

            peerPaint.setColor(Color.rgb(155, 188, 15));
            for (DungeonActorContext peer : peers) {
                if (!peer.alive() || peer.floor != state.floor) continue;
                if (!DungeonPerception.isVisible(state, peer.x, peer.y)) continue;
                if (peer.x < startX || peer.x >= endX || peer.y < startY || peer.y >= endY) continue;
                float cx = left + (peer.x - startX + 0.5f) * cell;
                float cy = top + (peer.y - startY + 0.5f) * cell;
                float radius = Math.max(3f, cell * 0.23f);
                canvas.drawCircle(cx, cy, radius, peerPaint);
                peerPaint.setStyle(Paint.Style.STROKE);
                peerPaint.setStrokeWidth(Math.max(2f, cell * 0.07f));
                peerPaint.setColor(Color.rgb(15, 56, 15));
                canvas.drawCircle(cx, cy, radius, peerPaint);
                peerPaint.setStyle(Paint.Style.FILL);
                peerPaint.setColor(Color.rgb(155, 188, 15));
            }
        }

        private static int oddClamp(int value, int min, int max) {
            int result = Math.max(min, Math.min(max, value));
            if ((result & 1) == 0) result--;
            return Math.max(min, result);
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
