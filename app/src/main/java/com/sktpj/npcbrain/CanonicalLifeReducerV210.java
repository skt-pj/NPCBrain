package com.sktpj.npcbrain;

import android.content.Context;

import org.json.JSONObject;

/** Pure-ish reducers that derive life/need state from one canonical time boundary. */
final class CanonicalLifeReducerV210 {
    private final Context appContext;

    CanonicalLifeReducerV210(Context context) {
        appContext = context.getApplicationContext();
    }

    Result reduce(String npcId, CanonicalNpcStateV210 canonical, long worldTimeMs) {
        NpcId id = NpcId.of(npcId);
        LifeState before = LifeState.fromJson(canonical.lifeState(), id, worldTimeMs);
        CharacterStateStore character = new CharacterStateStore(NpcContexts.storage(appContext, npcId));
        DailySchedule schedule = DailySchedule.fromJson(id, before.dailySchedule());
        if (schedule == null) {
            schedule = DailySchedule.profileFor(id, character.age(), character.occupation());
        }
        ScheduleSlot slot = schedule.slotAt(worldTimeMs);
        boolean changed = !slot.entryId().equals(before.currentScheduleEntryId())
                || !slot.activity().equals(before.currentActivity())
                || !slot.location().equals(before.location());
        LifeState life = changed
                ? before.transitionTo(
                        worldTimeMs,
                        slot.location(),
                        slot.activity(),
                        schedule.slotStartTimeMs(worldTimeMs, slot),
                        slot.goal(),
                        slot.context(),
                        slot.entryId(),
                        "",
                        schedule.toJson())
                : before.refreshCurrentSlot(worldTimeMs, slot, schedule.toJson());

        JSONObject characterJson = character.snapshotJson();
        double extraversion = character.traitPercent(CharacterStateStore.extraversionKey()) / 100.0;
        double neuroticism = character.traitPercent(CharacterStateStore.neuroticismKey()) / 100.0;
        double conscientiousness = character.traitPercent(CharacterStateStore.conscientiousnessKey()) / 100.0;
        double openness = character.traitPercent(CharacterStateStore.opennessKey()) / 100.0;
        JSONObject currentState = canonical.dynamicState();
        if (currentState.length() == 0) currentState = characterJson.optJSONObject("current_state");
        double valence = currentState == null ? 0.0 : currentState.optDouble("valence", 0.0);
        double stress = currentState == null ? 0.15 : currentState.optDouble("stress", 0.15);
        NpcInnerLifeState innerBefore = NpcInnerLifeState.fromJson(
                canonical.innerLife(), worldTimeMs, extraversion, neuroticism, openness, npcId);
        NpcInnerLifePolicy.AdvanceResult inner = NpcInnerLifePolicy.advance(
                innerBefore,
                worldTimeMs,
                life.currentActivity(),
                life.currentGoal(),
                extraversion,
                neuroticism,
                conscientiousness,
                openness,
                valence,
                stress);
        return new Result(life, inner.state, changed, inner.appendLocalThought);
    }

    static final class Result {
        final LifeState life;
        final NpcInnerLifeState innerLife;
        final boolean lifeChanged;
        final boolean appendLocalThought;

        Result(
                LifeState life,
                NpcInnerLifeState innerLife,
                boolean lifeChanged,
                boolean appendLocalThought
        ) {
            this.life = life;
            this.innerLife = innerLife;
            this.lifeChanged = lifeChanged;
            this.appendLocalThought = appendLocalThought;
        }
    }
}
