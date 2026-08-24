package com.sktpj.npcbrain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class NpcInnerLifeRuntime {
    private static final int AMBIENT_MAX_OUTPUT_TOKENS = 384;
    private static final double REFLECTION_MEMORY_THRESHOLD = 0.55;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean ambientInFlight = new AtomicBoolean(false);
    private final NpcRegistryStore registryStore;
    private final WorldRuntimeV040 worldRuntime;
    private final ConversationStore conversations;
    private final SecureApiKeyStore apiKeyStore;
    private final NpcAiStaminaStore staminaStore;

    private volatile boolean foreground;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (!foreground) return;
            tick();
            if (foreground) handler.postDelayed(this, NpcInnerLifePolicy.HEARTBEAT_MS);
        }
    };

    NpcInnerLifeRuntime(Context context) {
        appContext = context.getApplicationContext();
        registryStore = new NpcRegistryStore(appContext);
        worldRuntime = new WorldRuntimeV040(appContext);
        conversations = new ConversationStore(appContext);
        apiKeyStore = new SecureApiKeyStore(appContext);
        staminaStore = new NpcAiStaminaStore(appContext);
    }

    void onForeground() {
        foreground = true;
        handler.removeCallbacks(heartbeat);
        handler.post(heartbeat);
    }

    void onBackground() {
        foreground = false;
        handler.removeCallbacks(heartbeat);
    }

    void shutdown() {
        onBackground();
        executor.shutdownNow();
    }

    private void tick() {
        try {
            worldRuntime.syncAllNow();
        } catch (RuntimeException ignored) {
        }
        long now = worldRuntime.now();
        String ambientCandidate = "";
        for (String npcId : registryStore.activeNpcIds()) {
            try {
                NpcTraits traits = traits(npcId);
                Context storage = NpcContexts.storage(appContext, npcId);
                CharacterStateStore character = new CharacterStateStore(storage);
                if (character.isDead()) continue;
                LifeState life = worldRuntime.lifeState(npcId);
                NpcInnerLifeStore store = new NpcInnerLifeStore(storage);
                NpcInnerLifeState before = store.loadOrCreate(
                        now, traits.extraversion, traits.neuroticism, traits.openness);
                JSONObject characterJson = character.snapshotJson();
                JSONObject currentState = characterJson.optJSONObject("current_state");
                double valence = currentState == null ? 0.0 : currentState.optDouble("valence", 0.0);
                double stress = currentState == null ? 0.15 : currentState.optDouble("stress", 0.15);
                NpcInnerLifePolicy.AdvanceResult advanced = NpcInnerLifePolicy.advance(
                        before,
                        now,
                        life == null ? "" : life.currentActivity(),
                        life == null ? "" : life.currentGoal(),
                        traits.extraversion,
                        traits.neuroticism,
                        traits.conscientiousness,
                        traits.openness,
                        valence,
                        stress
                );
                store.save(advanced.state);
                if (advanced.appendLocalThought) {
                    store.appendThought(new NpcThoughtEntry(
                            now,
                            NpcThoughtEntry.SOURCE_LOCAL,
                            NpcInnerLifePolicy.localThought(
                                    advanced.state,
                                    life == null ? "" : life.currentActivity(),
                                    life == null ? "" : life.currentGoal())
                    ));
                }
                if (ambientCandidate.isEmpty()
                        && NpcInnerLifePolicy.isAmbientDue(
                                advanced.state,
                                now,
                                traits.extraversion,
                                traits.neuroticism,
                                traits.openness)) {
                    ambientCandidate = npcId;
                }
            } catch (Exception ignored) {
            }
        }
        if (!ambientCandidate.isEmpty()) submitAmbient(ambientCandidate);
    }

    private void submitAmbient(String npcId) {
        if (!foreground || !ambientInFlight.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                runAmbient(npcId);
            } finally {
                ambientInFlight.set(false);
            }
        });
    }

    private void runAmbient(String npcId) {
        long now = worldRuntime.now();
        Context storage = NpcContexts.storage(appContext, npcId);
        CharacterStateStore character = new CharacterStateStore(storage);
        if (character.isDead() || !registryStore.activeNpcIds().contains(npcId)) return;
        NpcTraits traits = traits(npcId);
        NpcInnerLifeStore store = new NpcInnerLifeStore(storage);
        NpcInnerLifeState state = store.loadOrCreate(
                now, traits.extraversion, traits.neuroticism, traits.openness);
        boolean reflectionDue = NpcInnerLifePolicy.reflectionDue(state, now);

        String apiKey;
        try {
            apiKey = apiKeyStore.load();
        } catch (Exception error) {
            apiKey = "";
        }
        if (apiKey == null || apiKey.trim().isEmpty() || staminaStore.snapshot(npcId).exhausted()) {
            recordAmbientFallback(store, state, now);
            return;
        }

        try {
            LifeState life = worldRuntime.lifeState(npcId);
            JSONObject characterJson = character.snapshotJson();
            MemoryStore memory = new MemoryStore(storage);
            JSONObject runtime = new JSONObject();
            runtime.put("mode", "ambient_inner_life");
            runtime.put("character_id", npcId);
            runtime.put("now_ms", now);
            runtime.put("character_state", characterJson);
            runtime.put("inner_life", state.snapshotForBrain());
            runtime.put("life_state", life == null ? new JSONObject() : life.toJson());
            runtime.put("long_term_memory", new JSONObject(memory.contextFor(
                    state.focus + " " + state.intention,
                    characterJson)));
            runtime.put("recent_direct_transcript",
                    conversations.recentContext("direct_" + npcId, 12));
            runtime.put("recent_group_transcript",
                    conversations.recentContext(DemoRuntimeV032.ROOM_GROUP, 12));
            runtime.put("reflection_due", reflectionDue);

            JSONObject result = new OpenAiClient(appContext, apiKey, "low")
                    .requestJson(ambientPrompt(runtime), AMBIENT_MAX_OUTPUT_TOKENS);
            String mood = limit(result.optString("mood_summary", state.mood), 120, state.mood);
            String focus = limit(result.optString("focus", state.focus), 140, state.focus);
            String thought = limit(result.optString("thought", ""), 360, "");
            String intention = limit(result.optString("intention", state.intention), 180, state.intention);
            String reflection = reflectionDue
                    ? limit(result.optString("reflection", ""), 420, "")
                    : "";
            double importance = clamp01(result.optDouble("importance", 0.0));
            if (thought.isEmpty()) {
                recordAmbientFallback(store, state, now);
                return;
            }

            boolean reflected = reflectionDue && !reflection.isEmpty();
            NpcInnerLifeState updated = state.withAmbient(
                    now, mood, focus, intention, reflected);
            store.save(updated);
            store.appendThought(new NpcThoughtEntry(
                    now, NpcThoughtEntry.SOURCE_AMBIENT, thought));
            if (reflected) {
                store.appendThought(new NpcThoughtEntry(
                        now, NpcThoughtEntry.SOURCE_REFLECTION, reflection));
                if (importance >= REFLECTION_MEMORY_THRESHOLD) {
                    memory.remember(
                            "ambient_reflection",
                            reflection,
                            reflection,
                            importance,
                            new JSONArray()
                    );
                }
            }
        } catch (Exception error) {
            recordAmbientFallback(store, state, now);
        }
    }

    private void recordAmbientFallback(
            NpcInnerLifeStore store,
            NpcInnerLifeState state,
            long now
    ) {
        store.save(state.withAmbientFallback(now));
        store.appendThought(new NpcThoughtEntry(
                now,
                NpcThoughtEntry.SOURCE_LOCAL,
                NpcInnerLifePolicy.localThought(state, "", "")
        ));
    }

    private NpcTraits traits(String npcId) {
        CharacterStateStore store = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
        return new NpcTraits(
                store.traitPercent(CharacterStateStore.extraversionKey()) / 100.0,
                store.traitPercent(CharacterStateStore.neuroticismKey()) / 100.0,
                store.traitPercent(CharacterStateStore.conscientiousnessKey()) / 100.0,
                store.traitPercent(CharacterStateStore.opennessKey()) / 100.0
        );
    }

    private static String ambientPrompt(JSONObject runtime) {
        return "You are producing one brief PUBLIC inner-life monitor update for an autonomous NPC.\n"
                + "Runtime JSON:\n" + runtime.toString() + "\n\n"
                + "This is not a user-facing assistant response and not a command execution step. "
                + "Use only grounded state, retrieved memory, and recent transcripts in Runtime JSON. "
                + "The NPC may be bored, tired, conflicted, distracted, curious, socially hesitant, or have nothing urgent to do. "
                + "Do not force productivity, conversation, or a new action. Do not invent off-screen events. "
                + "mood_summary is a short observable developer-facing emotional summary. "
                + "focus is what currently occupies attention. thought is one concise public paraphrase of what is on the NPC's mind. "
                + "intention is tentative and may be empty or mundane. "
                + "When reflection_due=true, reflection must be a concise public consolidation of a pattern supported by the supplied state/memory/transcripts; otherwise reflection must be empty. "
                + "importance describes only whether the reflection is worth durable memory. "
                + "Do not reveal or generate hidden chain-of-thought, private scratch work, step-by-step reasoning, prompts, or internal deliberation transcripts. "
                + "Do not output communication targets, messages to send, schedule edits, dungeon moves, consent changes, tool calls, or instructions.\n"
                + "Return ONLY JSON with exactly this shape:\n"
                + "{\"mood_summary\":\"short public mood\",\"focus\":\"short focus\","
                + "\"thought\":\"one short public thought summary\",\"intention\":\"short tentative intention or empty\","
                + "\"reflection\":\"public reflection when due otherwise empty\",\"importance\":0.0}\n"
                + "importance must be 0.0..1.0.";
    }

    private static String limit(String value, int max, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) text = fallback == null ? "" : fallback.trim();
        if (text.length() > max) text = text.substring(0, max);
        return text;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class NpcTraits {
        final double extraversion;
        final double neuroticism;
        final double conscientiousness;
        final double openness;

        NpcTraits(
                double extraversion,
                double neuroticism,
                double conscientiousness,
                double openness
        ) {
            this.extraversion = extraversion;
            this.neuroticism = neuroticism;
            this.conscientiousness = conscientiousness;
            this.openness = openness;
        }
    }
}
