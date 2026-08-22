package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CognitiveWorkingGraphTest {
    @Test
    public void instancesAreIndependentAndStartWithInput() {
        CognitiveWorkingGraph first = new CognitiveWorkingGraph("first");
        CognitiveWorkingGraph second = new CognitiveWorkingGraph("second");
        first.completeStage(
                "perception", "知覚", "first summary", 0.8,
                new JSONArray().put("fact a"), new JSONArray().put("input_0"));

        assertTrue(first.hasNode("stage_perception"));
        assertFalse(second.hasNode("stage_perception"));
        assertEquals(1, second.nodeCount());
    }

    @Test
    public void validUsedIdsCreateTypedEdgesAndInvalidIdsAreIgnored() {
        CognitiveWorkingGraph graph = new CognitiveWorkingGraph("hello");
        graph.completeStage(
                "perception", "知覚", "observed", 0.9,
                new JSONArray().put("hello"), new JSONArray().put("input_0"));
        int before = graph.edgeCount();
        graph.completeStage(
                "salience", "注意・重要度", "focus", 0.8,
                new JSONArray().put("important"),
                new JSONArray().put("stage_perception").put("missing").put("stage_perception"));

        JSONObject snapshot = graph.snapshot();
        JSONArray edges = snapshot.optJSONArray("edges");
        assertNotNull(edges);
        assertTrue(graph.edgeCount() > before);
        assertTrue(hasEdge(edges, "stage_perception", "stage_salience", "prioritizes"));
        assertFalse(hasFrom(edges, "missing"));
    }

    @Test
    public void emptyUsedIdsFallbackToStageSequence() {
        CognitiveWorkingGraph graph = new CognitiveWorkingGraph("hello");
        graph.completeStage(
                "perception", "知覚", "observed", 0.7,
                new JSONArray(), new JSONArray());
        graph.completeStage(
                "salience", "注意・重要度", "focus", 0.7,
                new JSONArray(), new JSONArray());

        assertTrue(hasEdge(
                graph.snapshot().optJSONArray("edges"),
                "stage_perception", "stage_salience", "feeds"));
    }

    @Test
    public void focusIsBoundedAndHasNoDisplayCoordinates() {
        CognitiveWorkingGraph graph = new CognitiveWorkingGraph("input");
        String[] modules = new String[]{
                "perception", "salience", "episodic_memory", "semantic_memory",
                "world_model", "executive_control", "valuation", "error_monitor",
                "action_selection"
        };
        for (String module : modules) {
            graph.completeStage(
                    module, module, module + " summary", 0.8,
                    new JSONArray().put(module + " fact1").put(module + " fact2").put(module + " fact3"),
                    new JSONArray());
        }

        JSONObject focus = graph.focusFor("global_workspace", 99);
        JSONArray nodes = focus.optJSONArray("nodes");
        JSONArray edges = focus.optJSONArray("edges");
        assertNotNull(nodes);
        assertNotNull(edges);
        assertTrue(nodes.length() <= CognitiveWorkingGraph.MAX_FOCUS_NODES);
        assertTrue(edges.length() <= CognitiveWorkingGraph.MAX_FOCUS_EDGES);
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            assertNotNull(node);
            assertFalse(node.has("x"));
            assertFalse(node.has("y"));
            assertFalse(node.has("z"));
            double activation = node.optDouble("activation", -1.0);
            assertTrue(activation >= 0.0 && activation <= 1.0);
        }
    }

    @Test
    public void integrationCreatesCenterCandidateAndIntegratesEdge() {
        CognitiveWorkingGraph graph = new CognitiveWorkingGraph("input");
        graph.completeStage(
                "perception", "知覚", "observed", 0.8,
                new JSONArray(), new JSONArray().put("input_0"));
        graph.completeIntegration(
                "final state", 0.9,
                new JSONArray().put("stage_perception").put("unknown"));

        assertTrue(graph.hasNode("integrated_0"));
        assertTrue(hasEdge(
                graph.snapshot().optJSONArray("edges"),
                "stage_perception", "integrated_0", "integrates"));
    }

    @Test
    public void snapshotIsDeepCopy() throws Exception {
        CognitiveWorkingGraph graph = new CognitiveWorkingGraph("input");
        JSONObject first = graph.snapshot();
        first.getJSONArray("nodes").getJSONObject(0).put("detail", "mutated");
        JSONObject second = graph.snapshot();
        assertEquals("input", second.getJSONArray("nodes").getJSONObject(0).getString("detail"));
    }

    private static boolean hasEdge(JSONArray edges, String from, String to, String type) {
        if (edges == null) return false;
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            if (edge == null) continue;
            if (from.equals(edge.optString("from_id"))
                    && to.equals(edge.optString("to_id"))
                    && type.equals(edge.optString("type"))) return true;
        }
        return false;
    }

    private static boolean hasFrom(JSONArray edges, String from) {
        if (edges == null) return false;
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            if (edge != null && from.equals(edge.optString("from_id"))) return true;
        }
        return false;
    }
}
