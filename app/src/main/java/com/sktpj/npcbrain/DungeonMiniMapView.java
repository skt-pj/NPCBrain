package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Compact read-only dungeon viewport used by the eight-way monitor. */
final class DungeonMiniMapView extends View {
    private static final int VIEW_CELLS = 7;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DungeonState state;
    private List<DungeonActorContext> peers = new ArrayList<>();

    DungeonMiniMapView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(4, 8, 14));
    }

    void setSnapshot(DungeonMonitorSnapshot snapshot) {
        state = snapshot == null ? null : snapshot.state;
        peers = snapshot == null || snapshot.peers == null
                ? new ArrayList<>() : new ArrayList<>(snapshot.peers);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == null || state.width <= 0 || state.height <= 0) {
            drawEmpty(canvas);
            return;
        }

        int cols = Math.min(VIEW_CELLS, state.width);
        int rows = Math.min(VIEW_CELLS, state.height);
        int startX = clamp(state.playerX - cols / 2, 0, Math.max(0, state.width - cols));
        int startY = clamp(state.playerY - rows / 2, 0, Math.max(0, state.height - rows));
        float cell = Math.min(getWidth() / (float) cols, getHeight() / (float) rows);
        float boardW = cols * cell;
        float boardH = rows * cell;
        float left = (getWidth() - boardW) / 2f;
        float top = (getHeight() - boardH) / 2f;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int mapX = startX + x;
                int mapY = startY + y;
                float l = left + x * cell;
                float t = top + y * cell;
                drawTile(canvas, mapX, mapY, l, t, cell);
            }
        }

        for (DungeonState.Enemy enemy : state.enemies) {
            if (enemy == null || !enemy.alive()) continue;
            if (!DungeonPerception.isVisible(state, enemy.x, enemy.y)) continue;
            drawActor(canvas, enemy.x, enemy.y, startX, startY, cols, rows,
                    left, top, cell, Color.rgb(207, 70, 82), 0.24f);
        }
        for (DungeonActorContext peer : peers) {
            if (peer == null || !peer.alive() || peer.floor != state.floor) continue;
            if (!DungeonPerception.isVisible(state, peer.x, peer.y)) continue;
            drawActor(canvas, peer.x, peer.y, startX, startY, cols, rows,
                    left, top, cell, Color.rgb(155, 188, 15), 0.25f);
        }
        drawActor(canvas, state.playerX, state.playerY, startX, startY, cols, rows,
                left, top, cell, Color.rgb(76, 169, 246), 0.29f);
    }

    private void drawTile(Canvas canvas, int x, int y, float l, float t, float cell) {
        boolean explored = state.visited != null
                && y >= 0 && y < state.visited.length
                && state.visited[y] != null
                && x >= 0 && x < state.visited[y].length
                && state.visited[y][x];
        boolean visible = DungeonPerception.isVisible(state, x, y);
        if (!explored && !visible) {
            paint.setColor(Color.rgb(8, 13, 20));
            canvas.drawRect(l, t, l + cell, t + cell, paint);
            return;
        }

        int tile = state.tileAt(x, y);
        if (tile == DungeonState.WALL) {
            paint.setColor(visible ? Color.rgb(55, 70, 77) : Color.rgb(31, 40, 46));
        } else {
            paint.setColor(visible ? Color.rgb(45, 55, 49) : Color.rgb(28, 34, 31));
        }
        canvas.drawRect(l, t, l + cell, t + cell, paint);

        if (tile == DungeonState.STAIRS) {
            paint.setColor(Color.rgb(213, 177, 82));
            canvas.drawRect(l + cell * 0.30f, t + cell * 0.30f,
                    l + cell * 0.70f, t + cell * 0.70f, paint);
        } else if (tile == DungeonState.CHEST) {
            paint.setColor(Color.rgb(184, 129, 55));
            canvas.drawRect(l + cell * 0.24f, t + cell * 0.30f,
                    l + cell * 0.76f, t + cell * 0.72f, paint);
        }
    }

    private void drawActor(
            Canvas canvas,
            int mapX,
            int mapY,
            int startX,
            int startY,
            int cols,
            int rows,
            float left,
            float top,
            float cell,
            int color,
            float radiusFactor
    ) {
        int localX = mapX - startX;
        int localY = mapY - startY;
        if (localX < 0 || localX >= cols || localY < 0 || localY >= rows) return;
        float cx = left + (localX + 0.5f) * cell;
        float cy = top + (localY + 0.5f) * cell;
        paint.setColor(color);
        canvas.drawCircle(cx, cy, Math.max(2f, cell * radiusFactor), paint);
    }

    private void drawEmpty(Canvas canvas) {
        paint.setColor(Color.rgb(95, 112, 132));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(18f, getHeight() * 0.18f));
        canvas.drawText("—", getWidth() / 2f, getHeight() / 2f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
