package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class CognitiveGraphTraceTest {
    @Test
    public void actualGraphIsDeepCopiedIntoGlobalWorkspaceStage() throws Exception {
        JSONArray trace = new JSONArray()
                .put(new JSONObject().put("stage_id", "perception"))
                .put(new JSONObject().put("stage_id", "global_workspace"));
        JSONObject graph = new JSONObject()
                .put("schema_version", 1)
                .put("nodes", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "integrated_0")
                                .put("node_type", "integrated")))
                .put("edges", new JSONArray());

        JSONArray persisted = ConversationStore.withCognitiveGraph(trace, graph);
        JSONObject saved = persisted.getJSONObject(1).optJSONObject("cognitive_graph");
        assertNotNull(saved);
        assertEquals("integrated_0",
                saved.getJSONArray("nodes").getJSONObject(0).getString("id"));

        graph.getJSONArray("nodes").getJSONObject(0).put("id", "mutated");
        assertEquals("integrated_0",
                saved.getJSONArray("nodes").getJSONObject(0).getString("id"));
        assertFalse(trace.getJSONObject(1).has("cognitive_graph"));
    }

    @Test
    public void invalidGraphDoesNotInventTraceData() throws Exception {
        JSONArray trace = new JSONArray()
                .put(new JSONObject().put("stage_id", "global_workspace"));
        JSONArray persisted = ConversationStore.withCognitiveGraph(trace, new JSONObject());
        assertFalse(persisted.getJSONObject(0).has("cognitive_graph"));
    }
}
