package com.sktpj.npcbrain;

import java.util.concurrent.atomic.AtomicBoolean;

final class SpontaneousProcessingGate {
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    private SpontaneousProcessingGate() {}

    static boolean tryAcquire() {
        return IN_FLIGHT.compareAndSet(false, true);
    }

    static void release() {
        IN_FLIGHT.set(false);
    }

    static boolean isBusy() {
        return IN_FLIGHT.get();
    }
}
