package com.sktpj.npcbrain;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

/** Regression: compatibility/UI reads must not become a second simulation writer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class CanonicalObserverRuntimeV211Test {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        resetKernel();
        context.deleteDatabase(WorldDatabaseV210.DB_NAME);
    }

    @After
    public void tearDown() throws Exception {
        resetKernel();
        context.deleteDatabase(WorldDatabaseV210.DB_NAME);
    }

    @Test
    public void compatibilityConversationRuntimeConstructionAndReadsDoNotAdvanceWorld() {
        WorldKernelV210 kernel = WorldKernelV210.get(context);
        new LegacyWorldImporterV210(context, kernel.database()).importIfNeeded();
        WorldQueryServiceV210 query = new WorldQueryServiceV210(kernel.database());
        long revisionBefore = query.revision();
        long timeBefore = query.worldTimeMs();

        WorldRuntimeV040 compatibility = new WorldRuntimeV040(context);
        compatibility.syncAllNow();
        compatibility.events();
        compatibility.lifeState("npc1");
        compatibility.room("direct_npc1");
        compatibility.now();

        assertEquals(revisionBefore, query.revision());
        assertEquals(timeBefore, query.worldTimeMs());
    }

    @Test
    public void canonicalQuerySnapshotIsReadOnly() {
        WorldKernelV210 kernel = WorldKernelV210.get(context);
        new LegacyWorldImporterV210(context, kernel.database()).importIfNeeded();
        WorldQueryServiceV210 query = new WorldQueryServiceV210(kernel.database());
        long revisionBefore = query.revision();
        long timeBefore = query.worldTimeMs();

        query.snapshot("npc1");
        query.snapshot("npc2");
        query.diagnostics();

        assertEquals(revisionBefore, query.revision());
        assertEquals(timeBefore, query.worldTimeMs());
    }

    private static void resetKernel() throws Exception {
        Field field = WorldKernelV210.class.getDeclaredField("instance");
        field.setAccessible(true);
        WorldKernelV210 existing = (WorldKernelV210) field.get(null);
        if (existing != null) existing.database().close();
        field.set(null, null);
    }
}
