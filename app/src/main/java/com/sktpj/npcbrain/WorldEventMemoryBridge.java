package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Publishes one observable world event and, when requested, the same event to NPC memory. */
final class WorldEventMemoryBridge {
    private final Context appContext;
    private final WorldClock clock;
    private final WorldStateStore worldStore;
    private final NpcRegistryStore registry;

    WorldEventMemoryBridge(Context context) {
        appContext = context.getApplicationContext();
        clock = new WorldClock(appContext);
        worldStore = new WorldStateStore(appContext);
        registry = new NpcRegistryStore(appContext);
    }

    WorldEvent record(
            String eventType,
            String actorId,
            String targetId,
            long timeMs,
            String location,
            JSONObject payload,
            String causeEventId,
            boolean rememberActor,
            boolean rememberTarget,
            double importance
    ) {
        String actor = normalizeOptionalNpcId(actorId);
        String target = normalizeOptionalNpcId(targetId);
        long worldTime = timeMs > 0L ? clock.advanceTo(timeMs) : clock.now();
        WorldEvent event = WorldEvent.create(
                safe(eventType),
                actor,
                target,
                worldTime,
                safe(location),
                payload,
                safe(causeEventId));
        worldStore.appendEvent(event);

        List<String> active = registry.activeNpcIds();
        if (rememberActor && active.contains(actor)) {
            remember(actor, event, importance);
        }
        if (rememberTarget && !target.equals(actor) && active.contains(target)) {
            remember(target, event, importance);
        }
        return event;
    }

    private void remember(String npcId, WorldEvent event, double importance) {
        try {
            JSONObject json = event.toJson();
            String summary = event.eventType() + " @ " + event.location();
            JSONObject payload = event.payload();
            String text = payload.optString("text", "").trim();
            if (!text.isEmpty()) summary = summary + ": " + limit(text, 600);
            String action = payload.optString("action", "").trim();
            if (!action.isEmpty() && text.isEmpty()) summary = summary + ": " + limit(action, 600);
            new MemoryStore(NpcContexts.storage(appContext, npcId)).remember(
                    json.toString(),
                    summary,
                    summary,
                    HumanMemoryPolicy.clamp01(importance),
                    new JSONArray());
        } catch (Exception ignored) {
        }
    }

    private static String normalizeOptionalNpcId(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            return NpcId.of(value).value();
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
