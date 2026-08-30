package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.lang.reflect.Field;

final class DungeonChestOverlayView extends View {
    static final String TAG = "npcbrain_dungeon_chest_overlay_v042";

    private final DungeonBoardView board;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    DungeonChestOverlayView(Context context, DungeonBoardView board) {
        super(context);
        this.board = board;
        density = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        DungeonState state = state();
        if (state == null) return;

        float minCell = 32f * density;
        float ideal = Math.min(getWidth() / 9f, getHeight() / 10.5f);
        float cell = Math.max(minCell, Math.min(52f * density, ideal));
        int cols = oddClamp((int) Math.floor(getWidth() / cell), 7, 13);
        int rows = oddClamp((int) Math.floor(getHeight() / cell), 7, 13);
        cell = Math.min(getWidth() / (float) cols, getHeight() / (float) rows);
        int startX = intField("viewportStartX",
                DungeonViewportPolicy.initialStart(state.playerX, cols, state.width));
        int startY = intField("viewportStartY",
                DungeonViewportPolicy.initialStart(state.playerY, rows, state.height));
        int endX = Math.min(state.width, startX + cols);
        int endY = Math.min(state.height, startY + rows);
        float boardWidth = Math.min(state.width, cols) * cell;
        float boardHeight = Math.min(state.height, rows) * cell;
        float left = (getWidth() - boardWidth) / 2f;
        float top = (getHeight() - boardHeight) / 2f;

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                if (state.tileAt(x, y) != DungeonState.CHEST) continue;
                if (!state.visited[y][x] && !DungeonPerception.isVisible(state, x, y)) continue;
                float l = left + (x - startX) * cell;
                float t = top + (y - startY) * cell;
                drawChest(canvas, l, t, cell, DungeonPerception.isVisible(state, x, y));
            }
        }
    }

    private void drawChest(Canvas canvas, float left, float top, float cell, boolean visible) {
        float inset = cell * 0.22f;
        RectF body = new RectF(left + inset, top + cell * 0.42f,
                left + cell - inset, top + cell * 0.78f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(visible ? Color.rgb(188, 154, 15) : Color.rgb(86, 113, 12));
        canvas.drawRect(body, paint);
        paint.setColor(visible ? Color.rgb(48, 82, 12) : Color.rgb(15, 56, 15));
        canvas.drawRect(left + inset, top + cell * 0.31f,
                left + cell - inset, top + cell * 0.46f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, cell * 0.055f));
        paint.setColor(Color.rgb(15, 56, 15));
        canvas.drawRect(body, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(15, 56, 15));
        canvas.drawRect(left + cell * 0.46f, top + cell * 0.50f,
                left + cell * 0.54f, top + cell * 0.63f, paint);
    }

    private DungeonState state() {
        try {
            Field field = DungeonBoardView.class.getDeclaredField("state");
            field.setAccessible(true);
            Object value = field.get(board);
            return value instanceof DungeonState ? (DungeonState) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int intField(String name, int fallback) {
        try {
            Field field = DungeonBoardView.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(board);
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int oddClamp(int value, int min, int max) {
        int result = Math.max(min, Math.min(max, value));
        if ((result & 1) == 0) result--;
        return Math.max(min, result);
    }
}
