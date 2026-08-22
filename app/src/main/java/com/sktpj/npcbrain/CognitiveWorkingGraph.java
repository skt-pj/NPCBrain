package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CognitiveWorkingGraph {
    static final int MAX_NODES = 48;
    static final int MAX_EDGES = 96;
    static final int MAX_FOCUS_NODES = 12;
    static final int MAX_FOCUS_EDGES = 24;
    private static final int MAX_FACTS_PER_STAGE = 3;
    private static final int MAX_USED_IDS = 8;

    private static final class Node {
        final String id;
        final String moduleId;
        final String type;
        final String label;
        String detail;
        double confidence;
        double activation;
        final int order;

        Node(
                String id,
                String moduleId,
                String type,
                String label,
                String detail,
                double confidence,
                double activation,
                int order
        ) {
            this.id = id;
            this.moduleId = moduleId;
            this.type = type;
            this.label = label;
            this.detail = detail;
            this.confidence = clamp01(confidence);
            this.activation = clamp01(activation);
            this.order = order;
        }
    }

    private static final class Edge {
        final String fromId;
        final String toId;
        final String type;
        final double weight;

        Edge(String fromId, String toId, String type, double weight) {
            this.fromId = fromId;
            this.toId = toId;
            this.type = type;
            this.weight = clamp01(weight);
        }
    }

    private static final class ScoredNode {
        final Node node;
        final double score;

        ScoredNode(Node node, double score) {
            this.node = node;
            this.score = score;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Set<String> edgeKeys = new HashSet<>();
    private int nextOrder;
    private String lastStageId = "";

    CognitiveWorkingGraph(String input) {
        String text = safe(input);
        addNode(new Node(
                "input_0",
                "perception",
                "input",
                "入力イベント",
                text,
                1.0,
                0.82,
                nextOrder++
        ));
    }

    synchronized void completeStage(
            String moduleId,
            String label,
            String summary,
            double confidence,
            JSONArray salientFacts,
            JSONArray usedNodeIds
    ) {
        String module = safeId(moduleId);
        String stageId = "stage_" + module;
        Node stage = new Node(
                stageId,
                module,
                "stage",
                safe(label),
                safe(summary),
                confidence,
                1.0,
                nextOrder++
        );
        addNode(stage);

        List<String> validUsed = validUsedNodeIds(usedNodeIds);
        String relation = relationFor(module);
        for (String usedId : validUsed) {
            addEdge(usedId, stageId, relation, 0.9);
            Node used = nodes.get(usedId);
            if (used != null) used.activation = Math.max(used.activation, 0.92);
        }
        if (validUsed.isEmpty()) {
            if (!lastStageId.isEmpty() && nodes.containsKey(lastStageId)) {
                addEdge(lastStageId, stageId, "feeds", 0.72);
            } else if (nodes.containsKey("input_0")) {
                addEdge("input_0", stageId, "feeds", 0.72);
            }
        }

        JSONArray facts = salientFacts == null ? new JSONArray() : salientFacts;
        int factCount = Math.min(MAX_FACTS_PER_STAGE, facts.length());
        for (int i = 0; i < factCount; i++) {
            String fact = safe(facts.optString(i, ""));
            if (fact.isEmpty()) continue;
            String factId = "fact_" + module + "_" + i;
            Node factNode = new Node(
                    factId,
                    module,
                    "fact",
                    "注目事実",
                    fact,
                    confidence,
                    Math.max(0.45, clamp01(confidence) * 0.78),
                    nextOrder++
            );
            if (addNode(factNode)) {
                addEdge(factId, stageId, "supports", 0.82);
            }
        }

        lastStageId = stageId;
        List<String> seeds = new ArrayList<>(validUsed);
        seeds.add(stageId);
        propagateActivation(seeds);
    }

    synchronized void completeIntegration(
            String summary,
            double confidence,
            JSONArray usedNodeIds
    ) {
        Node integrated = new Node(
                "integrated_0",
                "global_workspace",
                "integrated",
                "現在の統合状態",
                safe(summary),
                confidence,
                1.0,
                nextOrder++
        );
        addNode(integrated);
        List<String> validUsed = validUsedNodeIds(usedNodeIds);
        for (String usedId : validUsed) {
            addEdge(usedId, "integrated_0", "integrates", 1.0);
            Node used = nodes.get(usedId);
            if (used != null) used.activation = Math.max(used.activation, 0.95);
        }
        if (validUsed.isEmpty() && !lastStageId.isEmpty()) {
            addEdge(lastStageId, "integrated_0", "integrates", 0.86);
        }
        List<String> seeds = new ArrayList<>(validUsed);
        seeds.add("integrated_0");
        propagateActivation(seeds);
    }

    synchronized JSONObject focusFor(String moduleId, int requestedMaxNodes) {
        int maxNodes = Math.max(1, Math.min(MAX_FOCUS_NODES, requestedMaxNodes));
        List<ScoredNode> scored = new ArrayList<>();
        int maxOrder = Math.max(1, nextOrder - 1);
        for (Node node : nodes.values()) {
            double recency = (double) node.order / (double) maxOrder;
            double affinity = moduleAffinity(moduleId, node);
            double score = node.activation * 0.55
                    + node.confidence * 0.25
                    + affinity * 0.15
                    + recency * 0.05;
            scored.add(new ScoredNode(node, score));
        }
        scored.sort(Comparator
                .comparingDouble((ScoredNode item) -> item.score).reversed()
                .thenComparingInt(item -> item.node.order));

        List<Node> selected = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        for (ScoredNode item : scored) {
            if (selected.size() >= maxNodes) break;
            selected.add(item.node);
            selectedIds.add(item.node.id);
        }

        JSONArray nodeArray = new JSONArray();
        for (Node node : selected) nodeArray.put(nodeJson(node));
        JSONArray edgeArray = new JSONArray();
        for (Edge edge : edges) {
            if (edgeArray.length() >= MAX_FOCUS_EDGES) break;
            if (selectedIds.contains(edge.fromId) && selectedIds.contains(edge.toId)) {
                edgeArray.put(edgeJson(edge));
            }
        }

        JSONObject result = new JSONObject();
        try {
            result.put("nodes", nodeArray);
            result.put("edges", edgeArray);
            result.put("policy",
                    "Activation means attention priority, not truth. Preserve grounded facts and hard constraints even when activation is low.");
        } catch (Exception ignored) {
        }
        publishSnapshot();
        return result;
    }

    synchronized JSONObject snapshot() {
        JSONObject result = snapshotJson();
        CognitiveGraphLiveBus.publish(result);
        return copy(result);
    }

    synchronized int nodeCount() {
        return nodes.size();
    }

    synchronized int edgeCount() {
        return edges.size();
    }

    synchronized boolean hasNode(String id) {
        return nodes.containsKey(safe(id));
    }

    private void publishSnapshot() {
        CognitiveGraphLiveBus.publish(snapshotJson());
    }

    private JSONObject snapshotJson() {
        JSONArray nodeArray = new JSONArray();
        for (Node node : nodes.values()) nodeArray.put(nodeJson(node));
        JSONArray edgeArray = new JSONArray();
        for (Edge edge : edges) edgeArray.put(edgeJson(edge));
        JSONObject result = new JSONObject();
        try {
            result.put("schema_version", 1);
            result.put("nodes", nodeArray);
            result.put("edges", edgeArray);
        } catch (Exception ignored) {
        }
        return result;
    }

    private boolean addNode(Node node) {
        if (node == null || node.id.isEmpty()) return false;
        Node existing = nodes.get(node.id);
        if (existing != null) {
            existing.detail = node.detail;
            existing.confidence = node.confidence;
            existing.activation = Math.max(existing.activation, node.activation);
            return false;
        }
        if (nodes.size() >= MAX_NODES) return false;
        nodes.put(node.id, node);
        return true;
    }

    private void addEdge(String fromId, String toId, String type, double weight) {
        if (edges.size() >= MAX_EDGES) return;
        String from = safe(fromId);
        String to = safe(toId);
        if (!nodes.containsKey(from) || !nodes.containsKey(to) || from.equals(to)) return;
        String relation = safe(type);
        String key = from + "\u0000" + to + "\u0000" + relation;
        if (!edgeKeys.add(key)) return;
        edges.add(new Edge(from, to, relation, weight));
    }

    private List<String> validUsedNodeIds(JSONArray raw) {
        if (raw == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < raw.length() && result.size() < MAX_USED_IDS; i++) {
            String id = safe(raw.optString(i, ""));
            if (id.isEmpty() || !nodes.containsKey(id) || !seen.add(id)) continue;
            result.add(id);
        }
        return result;
    }

    private void propagateActivation(List<String> seeds) {
        if (seeds != null) {
            for (String id : seeds) {
                Node node = nodes.get(id);
                if (node != null) node.activation = 1.0;
            }
        }
        Node input = nodes.get("input_0");
        if (input != null) input.activation = Math.max(input.activation, 0.65);

        for (int pass = 0; pass < 2; pass++) {
            Map<String, Double> next = new LinkedHashMap<>();
            for (Node node : nodes.values()) next.put(node.id, node.activation);
            for (Edge edge : edges) {
                Node from = nodes.get(edge.fromId);
                Node to = nodes.get(edge.toId);
                if (from == null || to == null) continue;
                double decay = edge.weight * 0.55;
                next.put(to.id, Math.max(next.get(to.id), from.activation * decay));
                next.put(from.id, Math.max(next.get(from.id), to.activation * decay * 0.82));
            }
            for (Map.Entry<String, Double> entry : next.entrySet()) {
                Node node = nodes.get(entry.getKey());
                if (node != null) node.activation = clamp01(entry.getValue());
            }
        }
    }

    private static double moduleAffinity(String targetModuleId, Node node) {
        String target = safeId(targetModuleId);
        if (node.type.equals("input")) return 0.88;
        if (node.moduleId.equals(target)) return 1.0;
        if (node.type.equals("integrated")) return 0.95;
        int targetIndex = stageIndex(target);
        int sourceIndex = stageIndex(node.moduleId);
        if (targetIndex >= 0 && sourceIndex >= 0) {
            int distance = targetIndex - sourceIndex;
            if (distance == 1) return 0.90;
            if (distance == 2) return 0.78;
            if (distance > 2) return 0.58;
            if (distance < 0) return 0.34;
        }
        return node.type.equals("fact") ? 0.62 : 0.48;
    }

    private static int stageIndex(String moduleId) {
        String id = safeId(moduleId);
        String[] order = new String[]{
                "perception", "salience", "episodic_memory", "semantic_memory",
                "world_model", "executive_control", "valuation", "error_monitor",
                "action_selection", "global_workspace"
        };
        for (int i = 0; i < order.length; i++) if (order[i].equals(id)) return i;
        return -1;
    }

    private static String relationFor(String moduleId) {
        switch (safeId(moduleId)) {
            case "salience": return "prioritizes";
            case "episodic_memory": return "retrieves";
            case "semantic_memory": return "contextualizes";
            case "world_model": return "predicts_from";
            case "executive_control": return "plans_from";
            case "valuation": return "evaluates";
            case "error_monitor": return "checks";
            case "action_selection": return "selects_from";
            case "global_workspace": return "integrates";
            case "perception":
            default: return "observes";
        }
    }

    private static JSONObject nodeJson(Node node) {
        JSONObject result = new JSONObject();
        try {
            result.put("id", node.id);
            result.put("module_id", node.moduleId);
            result.put("node_type", node.type);
            result.put("label", node.label);
            result.put("detail", node.detail);
            result.put("confidence", node.confidence);
            result.put("activation", node.activation);
            result.put("order", node.order);
        } catch (Exception ignored) {
        }
        return result;
    }

    private static JSONObject edgeJson(Edge edge) {
        JSONObject result = new JSONObject();
        try {
            result.put("from_id", edge.fromId);
            result.put("to_id", edge.toId);
            result.put("type", edge.type);
            result.put("weight", edge.weight);
        } catch (Exception ignored) {
        }
        return result;
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return source == null ? new JSONObject() : new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeId(String value) {
        String id = safe(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9_-]", "_");
        return id.isEmpty() ? "unknown" : id;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
