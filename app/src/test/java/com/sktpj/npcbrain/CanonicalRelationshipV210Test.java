package com.sktpj.npcbrain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class CanonicalRelationshipV210Test {
    private Context context;
    private WorldKernelV210 kernel;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        resetKernel();
        context.deleteDatabase(WorldDatabaseV210.DB_NAME);
        context.getSharedPreferences("npcbrain_social_relationships_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        kernel = WorldKernelV210.get(context);
        WorldDatabaseV210 database = kernel.database();
        SQLiteDatabase db = database.getWritableDatabase();
        database.putMeta(db, WorldDatabaseV210.META_MIGRATION_STATE, "COMPLETED");
        database.upsertNpc(db, CanonicalNpcStateV210.empty("npc1"), 0L);
        database.upsertNpc(db, CanonicalNpcStateV210.empty("npc2"), 0L);
        WorldProjectionRunnerV210 projections = new WorldProjectionRunnerV210(context, database);
        kernel.attachProjectionRunner(projections);
    }

    @After
    public void tearDown() throws Exception {
        resetKernel();
        context.deleteDatabase(WorldDatabaseV210.DB_NAME);
    }

    @Test
    public void maintenanceRelationshipUpdateCommitsCanonicalAndProjectsCompatibilityStore() {
        SocialRelationshipStore store = new SocialRelationshipStore(context);
        long before = new WorldQueryServiceV210(kernel.database()).revision();
        JSONObject first = store.applyUpdate(
                "npc1", "npc2", 0.10, 0.12, -0.08, "first", 2, 5000L, 6000L);
        long after = new WorldQueryServiceV210(kernel.database()).revision();
        assertEquals(before + 1L, after);
        assertEquals(0.10, first.optDouble("familiarity"), 0.0001);
        assertEquals(0.12, first.optDouble("trust"), 0.0001);
        assertEquals(-0.08, first.optDouble("affinity"), 0.0001);

        JSONObject canonicalNpc = new WorldQueryServiceV210(kernel.database())
                .snapshot("npc1").optJSONObject("npc");
        assertTrue(canonicalNpc.optJSONObject("relationships").toString().contains("npc2"));
        String projected = context.getSharedPreferences(
                "npcbrain_social_relationships_v1", Context.MODE_PRIVATE)
                .getString("rel_npc1__npc2", "");
        assertTrue(projected.contains("\"other_id\":\"npc2\""));

        JSONObject replay = store.applyUpdate(
                "npc1", "npc2", 0.10, 0.12, -0.08, "retry", 2, 5000L, 7000L);
        assertEquals(after, new WorldQueryServiceV210(kernel.database()).revision());
        assertEquals(first.toString(), replay.toString());
    }

    private static void resetKernel() throws Exception {
        Field field = WorldKernelV210.class.getDeclaredField("instance");
        field.setAccessible(true);
        Object existing = field.get(null);
        if (existing instanceof WorldKernelV210) {
            ((WorldKernelV210) existing).database().close();
        }
        field.set(null, null);
    }
}
