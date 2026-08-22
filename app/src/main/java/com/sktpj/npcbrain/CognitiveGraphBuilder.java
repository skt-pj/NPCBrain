package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CognitiveGraphBuilder {
    private static final double STAGE_RADIUS = 1.10;
    private static final double FACT_RADIUS = 1.78;
    private static final double GROUNDED_RADIUS = 2.02;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private CognitiveGraphBuilder() {
    }

    /** Legacy v0.4.4 reconstruction. Kept only for compatibility tests/history. */
    static CognitiveGraph build(JSONArray stages, JSONObject grounded) {
        JSONArray safeStages = stages == null ? new JSONArray() : stages;
        JSONObject safeGrounded = grounded == null ? new JSONObject() : grounded;
        List<CognitiveGraph.Node> nodes = new ArrayList<>();
        List<CognitiveGraph.Edge> edges = new ArrayList<>();

        Center center = resolveCenter(safeStages);
        nodes.add(new CognitiveGraph.Node(
                "center",
                "global_workspace",
                "center",
                center.label,
                center.detail,
                0.0,
                0.0,
                0.0,
                center.active ? 1.0 : 0.35,
                center.confidence
        ));

        String previousStageId = "";
        int stageCount = safeStages.length();
        for (int i = 0; i < stageCount; i++) {
            JSONObject stage = safeStages.optJSONObject(i);
            if (stage == null) continue;
            String moduleId = safe(stage.optString("stage_id", "stage_" + i));
            String nodeId = "stage_" + safeId(moduleId) + "_" + i;
            String label = safe(stage.optString("stage_label", moduleId));
            String summary = safe(stage.optString("summary", ""));
            String status = safe(stage.optString("status", ""));
            boolean done = status.isEmpty() || "done".equals(status);
            boolean running = "running".equals(status);
            double confidence = clamp01(stage.optDouble("confidence", done ? 0.5 : 0.0));
            double activation = running ? 1.0 : (done ? 0.82 : 0.22);
            double[] position = spherePoint(i, Math.max(10, stageCount), STAGE_RADIUS, 0.21);

            nodes.add(new CognitiveGraph.Node(
                    nodeId,
                    moduleId,
                    "stage",
                    label,
                    summary.isEmpty() ? statusLabel(status) : summary,
                    position[0],
                    position[1],
                    position[2],
                    activation,
                    confidence
            ));
            edges.add(new CognitiveGraph.Edge(nodeId, "center", "integrates"));
            if (!previousStageId.isEmpty()) {
                edges.add(new CognitiveGraph.Edge(previousStageId, nodeId, "processing_sequence"));
            }
            previousStageId = nodeId;

            JSONArray facts = stage.optJSONArray("salient_facts");
            if (facts == null) continue;
            for (int f = 0; f < facts.length(); f++) {
                String fact = safe(facts.optString(f, ""));
                if (fact.isEmpty()) continue;
                String factId = nodeId + "_fact_" + f;
                double[] factPosition = childPoint(position, i, f, FACT_RADIUS);
                nodes.add(new CognitiveGraph.Node(
                        factId,
                        moduleId,
                        "fact",
                        "根拠",
                        fact,
                        factPosition[0],
                        factPosition[1],
                        factPosition[2],
                        0.58,
                        confidence
                ));
                edges.add(new CognitiveGraph.Edge(factId, nodeId, "supports"));
            }
        }

        addGroundedNodes(nodes, edges, safeGrounded, safeStages);
        return new CognitiveGraph(nodes, edges);
    }

    static boolean isValidSemanticSnapshot(JSONObject snapshot) {
        if (!CognitiveGraphLiveBus.isValid(snapshot)) return false;
        JSONArray nodes = snapshot.optJSONArray("nodes");
        if (nodes == null) return false;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null && !safe(node.optString("id", "")).isEmpty()) return true;
        }
        return false;
    }

    /**
     * Converts the real cognitive semantic graph to 3D coordinates. Node IDs and
     * typed edge endpoints are preserved exactly; only presentation coordinates
     * are added here and never fed back into cognition.
     */
    static CognitiveGraph buildFromSemanticSnapshot(JSONObject snapshot) {
        if (!isValidSemanticSnapshot(snapshot)) return emptyGraph();
        JSONArray rawNodes = snapshot.optJSONArray("nodes");
        JSONArray rawEdges = snapshot.optJSONArray("edges");
        if (rawNodes == null || rawEdges == null) return emptyGraph();

        List<JSONObject> sourceNodes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rawNodes.length(); i++) {
            JSONObject node = rawNodes.optJSONObject(i);
            if (node == null) continue;
            String id = safe(node.optString("id", ""));
            if (id.isEmpty() || !ids.add(id)) continue;
            sourceNodes.add(node);
        }
        if (sourceNodes.isEmpty()) return emptyGraph();

        String centerId = resolveSemanticCenterId(sourceNodes);
        List<CognitiveGraph.Node> nodes = new ArrayList<>();
        for (int i = 0; i < sourceNodes.size(); i++) {
            JSONObject source = sourceNodes.get(i);
            String id = safe(source.optString("id", ""));
            String moduleId = safe(source.optString("module_id", ""));
            String type = safe(source.optString("node_type", ""));
            String label = safe(source.optString("label", id));
            String detail = safe(source.optString("detail", ""));
            double activation = clamp01(source.optDouble("activation", 0.0));
            double confidence = clamp01(source.optDouble("confidence", 0.0));

            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            if (!id.equals(centerId)) {
                double radius = semanticRadius(type);
                int order = source.optInt("order", i);
                double[] position = spherePoint(
                        Math.max(0, order) + 1,
                        Math.max(12, sourceNodes.size() + 3),
                        radius,
                        semanticPhase(type));
                x = position[0];
                y = position[1];
                z = position[2];
            }
            nodes.add(new CognitiveGraph.Node(
                    id,
                    moduleId,
                    type,
                    label,
                    detail,
                    x, y, z,
                    activation,
                    confidence
            ));
        }

        List<CognitiveGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < rawEdges.length(); i++) {
            JSONObject edge = rawEdges.optJSONObject(i);
            if (edge == null) continue;
            String from = safe(edge.optString("from_id", ""));
            String to = safe(edge.optString("to_id", ""));
            String type = safe(edge.optString("type", ""));
            if (from.isEmpty() || to.isEmpty() || !ids.contains(from) || !ids.contains(to)) continue;
            edges.add(new CognitiveGraph.Edge(from, to, type));
        }
        return new CognitiveGraph(nodes, edges);
    }

    private static String resolveSemanticCenterId(List<JSONObject> nodes) {
        String firstId = safe(nodes.get(0).optString("id", ""));
        String latestStageId = "";
        int latestStageOrder = Integer.MIN_VALUE;
        String inputId = "";
        for (JSONObject node : nodes) {
            String id = safe(node.optString("id", ""));
            String type = safe(node.optString("node_type", ""));
            if ("integrated_0".equals(id) || "integrated".equals(type)) return id;
            if ("stage".equals(type)) {
                int order = node.optInt("order", Integer.MIN_VALUE);
                if (latestStageId.isEmpty() || order >= latestStageOrder) {
                    latestStageOrder = order;
                    latestStageId = id;
                }
            }
            if (inputId.isEmpty() && ("input_0".equals(id) || "input".equals(type))) inputId = id;
        }
        if (!latestStageId.isEmpty()) return latestStageId;
        if (!inputId.isEmpty()) return inputId;
        return firstId;
    }

    private static double semanticRadius(String type) {
        if ("stage".equals(type)) return STAGE_RADIUS;
        if ("fact".equals(type)) return FACT_RADIUS;
        if ("input".equals(type)) return GROUNDED_RADIUS;
        return GROUNDED_RADIUS;
    }

    private static double semanticPhase(String type) {
        if ("stage".equals(type)) return 0.21;
        if ("fact".equals(type)) return 0.47;
        return 0.73;
    }

    private static CognitiveGraph emptyGraph() {
        return new CognitiveGraph(new ArrayList<>(), new ArrayList<>());
    }

    private static void addGroundedNodes(
            List<CognitiveGraph.Node> nodes,
            List<CognitiveGraph.Edge> edges,
            JSONObject grounded,
            JSONArray stages
    ) {
        JSONObject life = grounded.optJSONObject("life_state");
        if (life == null) life = new JSONObject();
        JSONObject character = grounded.optJSONObject("character_state");
        if (character == null) character = new JSONObject();

        int index = 0;
        index = addGrounded(nodes, edges, "activity", "現在行動",
                life.optString("current_activity", ""), "perception", index, stages);
        index = addGrounded(nodes, edges, "location", "場所",
                life.optString("location", ""), "perception", index, stages);
        index = addGrounded(nodes, edges, "goal", "目標",
                life.optString("current_goal", ""), "executive_control", index, stages);
        index = addGrounded(nodes, edges, "context", "状況",
                life.optString("active_context", ""), "world_model", index, stages);

        JSONObject currentState = character.optJSONObject("current_state");
        if (currentState != null) {
            String dynamic = "感情価 " + formatSigned(currentState.optDouble("valence", 0.0))
                    + " / 覚醒 " + formatPercent(currentState.optDouble("arousal", 0.0))
                    + " / ストレス " + formatPercent(currentState.optDouble("stress", 0.0));
            addGrounded(nodes, edges, "dynamic_state", "動的状態",
                    dynamic, "valuation", index, stages);
        }
    }

    private static int addGrounded(
            List<CognitiveGraph.Node> nodes,
            List<CognitiveGraph.Edge> edges,
            String id,
            String label,
            String value,
            String preferredModule,
            int index,
            JSONArray stages
    ) {
        String detail = safe(value);
        if (detail.isEmpty() || "unknown".equalsIgnoreCase(detail)) return index;
        double[] position = spherePoint(index + 11, 18, GROUNDED_RADIUS, 0.47);
        String nodeId = "grounded_" + id;
        nodes.add(new CognitiveGraph.Node(
                nodeId,
                preferredModule,
                "grounded",
                label,
                detail,
                position[0], position[1], position[2],
                0.66,
                1.0
        ));
        String stageId = findStageNodeId(stages, preferredModule);
        edges.add(new CognitiveGraph.Edge(nodeId, stageId.isEmpty() ? "center" : stageId, "grounded_input"));
        return index + 1;
    }

    private static String findStageNodeId(JSONArray stages, String moduleId) {
        for (int i = 0; i < stages.length(); i++) {
            JSONObject stage = stages.optJSONObject(i);
            if (stage == null) continue;
            if (moduleId.equals(stage.optString("stage_id", ""))) {
                return "stage_" + safeId(moduleId) + "_" + i;
            }
        }
        return "";
    }

    private static Center resolveCenter(JSONArray stages) {
        JSONObject latestDone = null;
        for (int i = 0; i < stages.length(); i++) {
            JSONObject stage = stages.optJSONObject(i);
            if (stage == null || !isDone(stage)) continue;
            String summary = safe(stage.optString("summary", ""));
            if (summary.isEmpty()) continue;
            latestDone = stage;
            if ("global_workspace".equals(stage.optString("stage_id", ""))) {
                return new Center(
                        "現在の統合状態",
                        summary,
                        clamp01(stage.optDouble("confidence", 0.5)),
                        true
                );
            }
        }
        if (latestDone != null) {
            return new Center(
                    "統合中",
                    latestDone.optString("summary", ""),
                    clamp01(latestDone.optDouble("confidence", 0.5)),
                    true
            );
        }
        return new Center("待機中", "現在、公開済みの認知処理はありません。", 1.0, false);
    }

    private static boolean isDone(JSONObject stage) {
        String status = safe(stage.optString("status", ""));
        return status.isEmpty() || "done".equals(status);
    }

    private static double[] spherePoint(int index, int count, double radius, double phase) {
        int safeCount = Math.max(2, count);
        int normalizedIndex = Math.floorMod(index, safeCount);
        double y = 1.0 - (2.0 * (normalizedIndex + 0.5) / safeCount);
        y = Math.max(-0.98, Math.min(0.98, y));
        double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double theta = normalizedIndex * GOLDEN_ANGLE + phase;
        return new double[]{
                radius * Math.cos(theta) * ring,
                radius * y,
                radius * Math.sin(theta) * ring
        };
    }

    private static double[] childPoint(double[] parent, int stageIndex, int factIndex, double radius) {
        double px = parent[0];
        double py = parent[1];
        double pz = parent[2];
        double norm = Math.max(0.0001, Math.sqrt(px * px + py * py + pz * pz));
        double ux = px / norm;
        double uy = py / norm;
        double uz = pz / norm;
        double angle = (factIndex + 1) * 1.37 + stageIndex * 0.31;
        double dx = 0.16 * Math.cos(angle);
        double dy = 0.12 * Math.sin(angle * 0.7);
        double dz = 0.16 * Math.sin(angle);
        double x = ux + dx;
        double y = uy + dy;
        double z = uz + dz;
        double n = Math.max(0.0001, Math.sqrt(x * x + y * y + z * z));
        return new double[]{radius * x / n, radius * y / n, radius * z / n};
    }

    private static String statusLabel(String status) {
        if ("running".equals(status)) return "思考中";
        if ("waiting".equals(status)) return "待機";
        return "公開要約なし";
    }

    private static String formatSigned(double value) {
        int percent = (int) Math.round(Math.max(-1.0, Math.min(1.0, value)) * 100.0);
        return percent > 0 ? "+" + percent : Integer.toString(percent);
    }

    private static String formatPercent(double value) {
        return Integer.toString((int) Math.round(clamp01(value) * 100.0));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeId(String value) {
        String safe = safe(value).toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9_-]", "_");
        return safe.isEmpty() ? "node" : safe;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class Center {
        final String label;
        final String detail;
        final double confidence;
        final boolean active;

        Center(String label, String detail, double confidence, boolean active) {
            this.label = label;
            this.detail = detail;
            this.confidence = confidence;
            this.active = active;
        }
    }
}
