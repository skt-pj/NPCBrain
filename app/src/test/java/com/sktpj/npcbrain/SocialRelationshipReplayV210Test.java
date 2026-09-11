package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class SocialRelationshipReplayV210Test {
    private SocialRelationshipStore store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        context.getSharedPreferences("npcbrain_social_relationships_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new SocialRelationshipStore(context);
    }

    @Test
    public void sameEvidenceRangeDoesNotApplyRelationshipDeltaTwice() {
        JSONObject first = store.applyUpdate(
                "npc1", "npc2", 0.10, 0.12, -0.08, "first", 2, 5000L, 6000L);
        JSONObject replay = store.applyUpdate(
                "npc1", "npc2", 0.10, 0.12, -0.08, "different retry output", 2, 5000L, 7000L);
        assertEquals(first.toString(), replay.toString());
        assertEquals(2, replay.optInt("interaction_count"));
        assertEquals(0.10, replay.optDouble("familiarity"), 0.0001);
        assertEquals(0.12, replay.optDouble("trust"), 0.0001);
        assertEquals(-0.08, replay.optDouble("affinity"), 0.0001);
    }

    @Test
    public void newerEvidenceCanAdvanceRelationshipOnce() {
        store.applyUpdate("npc1", "npc2", 0.05, 0.05, 0.05, "one", 1, 5000L, 6000L);
        JSONObject next = store.applyUpdate(
                "npc1", "npc2", 0.05, -0.02, 0.03, "two", 1, 8000L, 9000L);
        assertEquals(2, next.optInt("interaction_count"));
        assertEquals(8000L, next.optLong("last_interaction_ms"));
        assertEquals(0.10, next.optDouble("familiarity"), 0.0001);
        assertEquals(0.03, next.optDouble("trust"), 0.0001);
        assertEquals(0.08, next.optDouble("affinity"), 0.0001);
    }
}
