package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

final class DungeonBoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private DungeonState state;

    DungeonBoardView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.rgb(4, 8, 14));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setState(DungeonState state) {
        this.state = state;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == null || state.width <= 0 || state.height <= 0) return;

        float minCell = 32f * density;
        float ideal = Math.min(getWidth() / 9f, getHeight() / 10.5f);
        float cell = Math.max(minCell, Math.min(52f * density, ideal));
        int cols = oddClamp((int) Math.floor(getWidth() / cell), 7, 13);
        int rows = oddClamp((int) Math.floor(getHeight() / cell), 7, 13);
        cell = Math.min(getWidth() / (float) cols, getHeight() / (float) rows);

        int startX = clamp(state.playerX - cols / 2, 0, Math.max(0, state.width - cols));
        int startY = clamp(state.playerY - rows / 2, 0, Math.max(0, state.height - rows));
        int endX = Math.min(state.width, startX + cols);
        int endY = Math.min(state.height, startY + rows);
        float boardWidth = (endX - startX) * cell;
        float boardHeight = (endY - startY) * cell;
        float left = (getWidth() - boardWidth) / 2f;
        float top = (getHeight() - boardHeight) / 2f;

        drawViewport(canvas, startX, startY, endX, endY, left, top, cell);
        drawMiniMap(canvas);
        drawFrame(canvas, left, top, boardWidth, boardHeight);
    }

    private void drawViewport(
            Canvas canvas,
            int startX,
            int startY,
            int endX,
            int endY,
            float left,
            float top,
            float cell
    ) {
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                float l = left + (x - startX) * cell;
                float t = top + (y - startY) * cell;
                boolean explored = state.visited[y][x];
                boolean visible = DungeonPerception.isVisible(state, x, y);
                drawTile(canvas, x, y, l, t, cell, explored, visible);
            }
        }

        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || !DungeonPerception.isVisible(state, enemy.x, enemy.y)) continue;
            if (enemy.x < startX || enemy.x >= endX || enemy.y < startY || enemy.y >= endY) continue;
            float cx = left + (enemy.x - startX + 0.5f) * cell;
            float cy = top + (enemy.y - startY + 0.5f) * cell;
            drawEnemy(canvas, cx, cy, cell, enemy.hp);
        }

        float playerCx = left + (state.playerX - startX + 0.5f) * cell;
        float playerCy = top + (state.playerY - startY + 0.5f) * cell;
        drawPlayer(canvas, playerCx, playerCy, cell);
    }

    private void drawTile(
            Canvas canvas,
            int x,
            int y,
            float left,
            float top,
            float cell,
            boolean explored,
            boolean visible
    ) {
        if (!explored) {
            paint.setColor(Color.rgb(5, 9, 15));
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setStrokeWidth(Math.max(1f, cell * 0.02f));
            paint.setColor(Color.rgb(10, 17, 26));
            canvas.drawLine(left, top + cell, left + cell, top, paint);
            return;
        }

        int tile = state.tileAt(x, y);
        if (tile == DungeonState.WALL) {
            paint.setColor(visible ? Color.rgb(54, 68, 84) : Color.rgb(30, 39, 51));
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setColor(visible ? Color.rgb(78, 96, 116) : Color.rgb(43, 54, 68));
            canvas.drawRect(left, top, left + cell, top + cell * 0.16f, paint);
            paint.setColor(Color.rgb(18, 27, 38));
            canvas.drawRect(left, top + cell * 0.84f, left + cell, top + cell, paint);
        } else {
            int base = ((x + y) & 1) == 0 ? 45 : 49;
            if (!visible) base -= 18;
            paint.setColor(Color.rgb(base, base + 5, base + 9));
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setColor(visible ? Color.rgb(59, 68, 77) : Color.rgb(35, 42, 50));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, cell * 0.018f));
            canvas.drawRect(left + 1, top + 1, left + cell - 1, top + cell - 1, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        if (tile == DungeonState.STAIRS && (visible || explored)) {
            float inset = cell * 0.18f;
            paint.setColor(visible ? Color.rgb(213, 177, 82) : Color.rgb(116, 99, 59));
            canvas.drawRoundRect(new RectF(
                    left + inset, top + inset, left + cell - inset, top + cell - inset),
                    cell * 0.10f, cell * 0.10f, paint);
            textPaint.setColor(visible ? Color.rgb(30, 24, 13) : Color.rgb(220, 208, 163));
            textPaint.setTextSize(Math.max(12f * density, cell * 0.48f));
            canvas.drawText(">", left + cell / 2f, top + cell * 0.69f, textPaint);
        }
    }

    private void drawEnemy(Canvas canvas, float cx, float cy, float cell, int hp) {
        float r = cell * 0.30f;
        paint.setColor(Color.argb(70, 232, 79, 91));
        canvas.drawCircle(cx, cy, cell * 0.43f, paint);
        paint.setColor(Color.rgb(207, 70, 82));
        canvas.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r),
                cell * 0.10f, cell * 0.10f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, cell * 0.05f));
        paint.setColor(Color.rgb(255, 147, 147));
        canvas.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r),
                cell * 0.10f, cell * 0.10f, paint);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(Math.max(10f * density, cell * 0.27f));
        textPaint.setColor(Color.WHITE);
        canvas.drawText("E" + hp, cx, cy + cell * 0.10f, textPaint);
    }

    private void drawPlayer(Canvas canvas, float cx, float cy, float cell) {
        paint.setColor(Color.argb(62, 76, 170, 255));
        canvas.drawCircle(cx, cy, cell * 0.49f, paint);
        paint.setColor(Color.rgb(76, 169, 246));
        canvas.drawCircle(cx, cy, cell * 0.34f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, cell * 0.055f));
        paint.setColor(Color.rgb(196, 233, 255));
        canvas.drawCircle(cx, cy, cell * 0.34f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(231, 249, 255));
        canvas.drawCircle(cx, cy, cell * 0.11f, paint);
    }

    private void drawMiniMap(Canvas canvas) {
        float size = Math.min(112f * density, Math.min(getWidth(), getHeight()) * 0.29f);
        float margin = 10f * density;
        float left = getWidth() - size - margin;
        float top = margin;
        paint.setColor(Color.argb(214, 7, 13, 22));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size),
                10f * density, 10f * density, paint);

        float inset = 8f * density;
        float cell = Math.min((size - inset * 2f) / state.width,
                (size - inset * 2f) / state.height);
        float mapLeft = left + (size - cell * state.width) / 2f;
        float mapTop = top + (size - cell * state.height) / 2f;
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                if (!state.visited[y][x]) continue;
                int tile = state.tileAt(x, y);
                if (tile == DungeonState.WALL) paint.setColor(Color.rgb(67, 81, 96));
                else if (tile == DungeonState.STAIRS) paint.setColor(Color.rgb(223, 183, 84));
                else paint.setColor(Color.rgb(126, 141, 154));
                canvas.drawRect(mapLeft + x * cell, mapTop + y * cell,
                        mapLeft + (x + 1) * cell, mapTop + (y + 1) * cell, paint);
            }
        }
        for (DungeonState.Enemy enemy : state.enemies) {
            if (!enemy.alive() || !DungeonPerception.isVisible(state, enemy.x, enemy.y)) continue;
            paint.setColor(Color.rgb(230, 81, 91));
            canvas.drawCircle(mapLeft + (enemy.x + 0.5f) * cell,
                    mapTop + (enemy.y + 0.5f) * cell,
                    Math.max(2f * density, cell * 0.65f), paint);
        }
        paint.setColor(Color.rgb(83, 185, 255));
        canvas.drawCircle(mapLeft + (state.playerX + 0.5f) * cell,
                mapTop + (state.playerY + 0.5f) * cell,
                Math.max(2.5f * density, cell * 0.85f), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.rgb(94, 119, 147));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size),
                10f * density, 10f * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFrame(Canvas canvas, float left, float top, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(Color.rgb(45, 65, 88));
        canvas.drawRoundRect(new RectF(left, top, left + width, top + height),
                10f * density, 10f * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static int oddClamp(int value, int min, int max) {
        int result = Math.max(min, Math.min(max, value));
        if ((result & 1) == 0) result--;
        return Math.max(min, result);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
