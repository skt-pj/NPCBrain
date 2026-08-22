package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CognitiveGraph {
    static final class Node {
        final String id;
        final String moduleId;
        final String type;
        final String label;
        final String detail;
        final double x;
        final double y;
        final double z;
        final double activation;
        final double confidence;

        Node(
                String id,
                String moduleId,
                String type,
                String label,
                String detail,
                double x,
                double y,
                double z,
                double activation,
                double confidence
        ) {
            this.id = safe(id);
            this.moduleId = safe(moduleId);
            this.type = safe(type);
            this.label = safe(label);
            this.detail = safe(detail);
            this.x = x;
            this.y = y;
            this.z = z;
            this.activation = clamp01(activation);
            this.confidence = clamp01(confidence);
        }

        double radius() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    static final class Edge {
        final String fromId;
        final String toId;
        final String type;

        Edge(String fromId, String toId, String type) {
            this.fromId = safe(fromId);
            this.toId = safe(toId);
            this.type = safe(type);
        }
    }

    private final List<Node> nodes;
    private final List<Edge> edges;

    CognitiveGraph(List<Node> nodes, List<Edge> edges) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
    }

    List<Node> nodes() {
        return nodes;
    }

    List<Edge> edges() {
        return edges;
    }

    Node nodeById(String id) {
        if (id == null) return null;
        for (Node node : nodes) {
            if (id.equals(node.id)) return node;
        }
        return null;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
