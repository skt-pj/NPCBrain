package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CognitiveGraphBuilderTest {
    @Test
    public void globalWorkspaceSummaryBecomesCenter() throws Exception {
        JSONArray stages = new JSONArray()
                .put(stage("perception", "知覚", "事実を確認", 0.8, "done"))
                .put(stage("global_workspace", "Global Workspace", "短く気遣って返す", 0.91, "done"));
        CognitiveGraph graph = CognitiveGraphBuilder.build(stages, new JSONObject());

        CognitiveGraph.Node center = graph.nodeById("center");
        assertNotNull(center);
        assertEquals("現在の統合状態", center.label);
        assertEquals("短く気遣って返す", center.detail);
        assertEquals(0.0, center.radius(), 0.0001);
    }

    @Test
    public void latestCompletedStageIsTemporaryCenterWhileThinking() throws Exception {
        JSONArray stages = new JSONArray()
                .put(stage("perception", "知覚", "受信内容を確認", 0.7, "done"))
                .put(stage("salience", "注意・重要度", "", 0.0, "running"))
                .put(stage("global_workspace", "Global Workspace", "", 0.0, "waiting"));

        CognitiveGraph.Node center = CognitiveGraphBuilder.build(stages, new JSONObject()).nodeById("center");
        assertNotNull(center);
        assertEquals("統合中", center.label);
        assertEquals("受信内容を確認", center.detail);
    }

    @Test
    public void idleGraphUsesOnlyGroundedInputsWhenTraceMissing() throws Exception {
        JSONObject life = new JSONObject()
                .put("current_activity", "work")
                .put("location", "office")
                .put("current_goal", "finish_report")
                .put("active_context", "deadline_today");
        JSONObject character = new JSONObject()
                .put("current_state", new JSONObject()
                        .put("valence", -0.2)
                        .put("arousal", 0.6)
                        .put("stress", 0.7));
        JSONObject grounded = new JSONObject()
                .put("life_state", life)
                .put("character_state", character);

        CognitiveGraph graph = CognitiveGraphBuilder.build(new JSONArray(), grounded);
        assertEquals("待機中", graph.nodeById("center").label);
        assertNotNull(graph.nodeById("grounded_activity"));
        assertNotNull(graph.nodeById("grounded_location"));
        assertNotNull(graph.nodeById("grounded_goal"));
        assertNotNull(graph.nodeById("grounded_context"));
        assertNotNull(graph.nodeById("grounded_dynamic_state"));
        assertEquals("work", graph.nodeById("grounded_activity").detail);
    }

    @Test
    public void factsConnectToStageAndStagesConnectToCenter() throws Exception {
        JSONObject perception = stage("perception", "知覚", "事実を抽出", 0.82, "done");
        perception.put("salient_facts", new JSONArray().put("ユーザーが疲れたと言った"));
        JSONArray stages = new JSONArray().put(perception);
        CognitiveGraph graph = CognitiveGraphBuilder.build(stages, new JSONObject());

        CognitiveGraph.Node stage = findByType(graph, "stage");
        CognitiveGraph.Node fact = findByType(graph, "fact");
        assertNotNull(stage);
        assertNotNull(fact);
        assertEquals("ユーザーが疲れたと言った", fact.detail);
        assertTrue(hasEdge(graph, fact.id, stage.id, "supports"));
        assertTrue(hasEdge(graph, stage.id, "center", "integrates"));
    }

    @Test
    public void pointsUseTrueThreeDimensionalShells() throws Exception {
        JSONArray stages = new JSONArray();
        String[] ids = {"perception", "salience", "episodic_memory", "semantic_memory", "world_model",
                "executive_control", "valuation", "error_monitor", "action_selection", "global_workspace"};
        for (int i = 0; i < ids.length; i++) {
            JSONObject stage = stage(ids[i], ids[i], "summary_" + i, 0.7, "done");
            stage.put("salient_facts", new JSONArray().put("fact_" + i));
            stages.put(stage);
        }
        CognitiveGraph graph = CognitiveGraphBuilder.build(stages, new JSONObject());
        Set<Integer> roundedZ = new HashSet<>();
        double maxStageRadius = 0.0;
        double minFactRadius = Double.MAX_VALUE;
        for (CognitiveGraph.Node node : graph.nodes()) {
            if (!"center".equals(node.type)) roundedZ.add((int) Math.round(node.z * 100));
            if ("stage".equals(node.type)) maxStageRadius = Math.max(maxStageRadius, node.radius());
            if ("fact".equals(node.type)) minFactRadius = Math.min(minFactRadius, node.radius());
        }
        assertTrue(roundedZ.size() > 4);
        assertTrue(maxStageRadius > 0.0);
        assertTrue(minFactRadius > maxStageRadius);
    }

    @Test
    public void builderDoesNotInventFactText() throws Exception {
        JSONObject stage = stage("perception", "知覚", "summary only", 0.5, "done");
        CognitiveGraph graph = CognitiveGraphBuilder.build(new JSONArray().put(stage), new JSONObject());
        assertFalse(containsType(graph, "fact"));
    }

    private static JSONObject stage(
            String id,
            String label,
            String summary,
            double confidence,
            String status
    ) throws Exception {
        return new JSONObject()
                .put("stage_id", id)
                .put("stage_label", label)
                .put("summary", summary)
                .put("confidence", confidence)
                .put("status", status)
                .put("salient_facts", new JSONArray());
    }

    private static CognitiveGraph.Node findByType(CognitiveGraph graph, String type) {
        for (CognitiveGraph.Node node : graph.nodes()) {
            if (type.equals(node.type)) return node;
        }
        return null;
    }

    private static boolean containsType(CognitiveGraph graph, String type) {
        return findByType(graph, type) != null;
    }

    private static boolean hasEdge(CognitiveGraph graph, String from, String to, String type) {
        for (CognitiveGraph.Edge edge : graph.edges()) {
            if (from.equals(edge.fromId) && to.equals(edge.toId) && type.equals(edge.type)) return true;
        }
        return false;
    }
}
