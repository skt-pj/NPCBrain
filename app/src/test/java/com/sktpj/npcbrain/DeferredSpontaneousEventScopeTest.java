package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

public class DeferredSpontaneousEventScopeTest {
    @After
    public void cleanup() {
        DeferredSpontaneousEventScope.exit();
    }

    @Test
    public void scopeKeepsOnlyCurrentThreadEventId() throws Exception {
        DeferredSpontaneousEventScope.enter("event_a");
        assertEquals("event_a", DeferredSpontaneousEventScope.currentEventId());
        final String[] other = {"unset"};
        Thread thread = new Thread(() -> other[0] = DeferredSpontaneousEventScope.currentEventId());
        thread.start();
        thread.join();
        assertEquals("", other[0]);
        DeferredSpontaneousEventScope.exit();
        assertEquals("", DeferredSpontaneousEventScope.currentEventId());
    }
}
