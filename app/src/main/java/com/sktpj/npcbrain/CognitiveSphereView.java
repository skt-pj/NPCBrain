package com.sktpj.npcbrain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CognitiveSphereView extends View {
    interface NodeListener {
        void onNodeSelected(CognitiveGraph.Node node);
    }

    private static final double CAMERA_DISTANCE = 5.0;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private CognitiveGraph graph = new CognitiveGraph(new ArrayList<>(), new ArrayList<>());
    private NodeListener nodeListener;
    private double yaw = -0.42;
    private double pitch = 0.24;
    private double zoom = 1.0;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private boolean dragging;
    private List<ProjectedNode> lastProjected = new ArrayList<>();
    private String selectedId = "";

    CognitiveSphereView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(6, 12, 24));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoom *= detector.getScaleFactor();
                zoom = Math.max(0.62, Math.min(2.3, zoom));
                invalidate();
                return true;
            }
        });
    }

    void setGraph(CognitiveGraph graph) {
        this.graph = graph == null
                ? new CognitiveGraph(new ArrayList<>(), new ArrayList<>())
                : graph;
        if (this.graph.nodeById(selectedId) == null) selectedId = "";
        invalidate();
    }

    void setNodeListener(NodeListener listener) {
        nodeListener = listener;
    }

    void resetView() {
        yaw = -0.42;
        pitch = 0.24;
        zoom = 1.0;
        selectedId = "";
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        drawSphereGuides(canvas);
        List<ProjectedNode> projected = projectNodes();
        Map<String, ProjectedNode> byId = new HashMap<>();
        for (ProjectedNode p : projected) byId.put(p.node.id, p);
        drawEdges(canvas, byId);
        Collections.sort(projected, Comparator.comparingDouble(p -> p.depth));
        for (ProjectedNode p : projected) drawNode(canvas, p);
        lastProjected = projected;
    }

    private void drawSphereGuides(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float base = Math.min(getWidth(), getHeight()) * 0.37f * (float) zoom;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(52, 64, 139, 210));
        for (int i = 1; i <= 3; i++) {
            float radius = base * i / 3f;
            canvas.drawCircle(cx, cy, radius, paint);
        }
        paint.setColor(Color.argb(38, 86, 187, 238));
        canvas.drawOval(new RectF(cx - base, cy - base * 0.34f, cx + base, cy + base * 0.34f), paint);
        canvas.drawOval(new RectF(cx - base * 0.34f, cy - base, cx + base * 0.34f, cy + base), paint);
    }

    private List<ProjectedNode> projectNodes() {
        List<ProjectedNode> result = new ArrayList<>();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        double unit = Math.min(getWidth(), getHeight()) * 0.175 * zoom;
        double cyaw = Math.cos(yaw);
        double syaw = Math.sin(yaw);
        double cpitch = Math.cos(pitch);
        double spitch = Math.sin(pitch);

        for (CognitiveGraph.Node node : graph.nodes()) {
            double x1 = cyaw * node.x + syaw * node.z;
            double z1 = -syaw * node.x + cyaw * node.z;
            double y2 = cpitch * node.y - spitch * z1;
            double z2 = spitch * node.y + cpitch * z1;
            double perspective = CAMERA_DISTANCE / Math.max(1.2, CAMERA_DISTANCE - z2);
            float sx = (float) (cx + x1 * unit * perspective);
            float sy = (float) (cy - y2 * unit * perspective);
            result.add(new ProjectedNode(node, sx, sy, z2, perspective));
        }
        return result;
    }

    private void drawEdges(Canvas canvas, Map<String, ProjectedNode> byId) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        for (CognitiveGraph.Edge edge : graph.edges()) {
            ProjectedNode from = byId.get(edge.fromId);
            ProjectedNode to = byId.get(edge.toId);
            if (from == null || to == null) continue;
            boolean selected = selectedId.equals(edge.fromId) || selectedId.equals(edge.toId);
            int alpha = selected ? 205 : depthAlpha((from.depth + to.depth) / 2.0, 34, 116);
            int color = selected ? Color.rgb(192, 220, 255) : moduleColor(from.node.moduleId);
            paint.setColor(withAlpha(color, alpha));
            paint.setStrokeWidth(selected ? dp(2) : dp(1));
            canvas.drawLine(from.x, from.y, to.x, to.y, paint);
        }
    }

    private void drawNode(Canvas canvas, ProjectedNode p) {
        boolean center = "center".equals(p.node.type);
        boolean stage = "stage".equals(p.node.type);
        boolean grounded = "grounded".equals(p.node.type);
        boolean selected = p.node.id.equals(selectedId);
        int color = center ? Color.rgb(177, 108, 255) : moduleColor(p.node.moduleId);
        float baseRadius = center ? dp(13) : (stage ? dp(7) : (grounded ? dp(5) : dp(3)));
        float radius = (float) (baseRadius * Math.max(0.65, Math.min(1.45, p.perspective))
                * (0.72 + 0.45 * p.node.activation));
        int alpha = depthAlpha(p.depth, 90, 255);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, Math.min(90, alpha / 2)));
        canvas.drawCircle(p.x, p.y, radius * (selected ? 2.8f : 2.0f), paint);
        paint.setColor(withAlpha(color, alpha));
        canvas.drawCircle(p.x, p.y, selected ? radius * 1.35f : radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(withAlpha(Color.WHITE, Math.min(220, alpha)));
        canvas.drawCircle(p.x, p.y, selected ? radius * 1.35f : radius, paint);

        if (center || stage || selected) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(center ? sp(14) : sp(10));
            paint.setColor(withAlpha(Color.WHITE, Math.max(150, alpha)));
            paint.setTextAlign(Paint.Align.CENTER);
            String text = center ? p.node.label : p.node.label;
            float ty = p.y - radius - dp(7);
            if (center) ty = p.y + dp(5);
            canvas.drawText(shorten(text, center ? 16 : 12), p.x, ty, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = downX = event.getX();
                lastY = downY = event.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    if (Math.hypot(event.getX() - downX, event.getY() - downY) > dp(5)) dragging = true;
                    yaw += dx * 0.009;
                    pitch += dy * 0.009;
                    pitch = Math.max(-1.35, Math.min(1.35, pitch));
                    lastX = event.getX();
                    lastY = event.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && !scaleDetector.isInProgress()) selectNearest(event.getX(), event.getY());
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void selectNearest(float x, float y) {
        ProjectedNode best = null;
        double bestDistance = dp(34);
        for (ProjectedNode p : lastProjected) {
            double distance = Math.hypot(p.x - x, p.y - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = p;
            }
        }
        if (best == null) return;
        selectedId = best.node.id;
        invalidate();
        if (nodeListener != null) nodeListener.onNodeSelected(best.node);
    }

    private int moduleColor(String moduleId) {
        String id = moduleId == null ? "" : moduleId;
        if (id.contains("perception")) return Color.rgb(82, 232, 159);
        if (id.contains("salience")) return Color.rgb(171, 101, 255);
        if (id.contains("episodic")) return Color.rgb(47, 216, 255);
        if (id.contains("semantic")) return Color.rgb(58, 161, 255);
        if (id.contains("world")) return Color.rgb(91, 225, 159);
        if (id.contains("executive")) return Color.rgb(255, 208, 84);
        if (id.contains("valuation")) return Color.rgb(255, 111, 96);
        if (id.contains("error")) return Color.rgb(188, 103, 255);
        if (id.contains("action")) return Color.rgb(255, 137, 86);
        if (id.contains("global")) return Color.rgb(102, 155, 255);
        return Color.rgb(91, 174, 255);
    }

    private int depthAlpha(double depth, int min, int max) {
        double normalized = (depth + 2.4) / 4.8;
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        return (int) Math.round(min + (max - min) * normalized);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class ProjectedNode {
        final CognitiveGraph.Node node;
        final float x;
        final float y;
        final double depth;
        final double perspective;

        ProjectedNode(CognitiveGraph.Node node, float x, float y, double depth, double perspective) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.depth = depth;
            this.perspective = perspective;
        }
    }
}
