# v0.4.27 Brain Authority Correction

Status: implemented and verified. The user requested final APK delivery on 2026-08-25 after implementation continuation.

## 1. Problem statement

Current v0.4.26 lets local Android policies override or reconstruct psychological decisions after the 9 specialists + Global Workspace have already made a character decision. Reproduced from source review:

- dungeon participation starts from fixed reluctance/fear, requires numeric thresholds and a lexical personal-reason cue, and is inferred from emitted text rather than owned by the Brain cycle;
- low HP / nearby enemies can deterministically withdraw or force EVADE;
- direct Big Five formulas select combat, retreat and movement behavior;
- persistent Dungeon Brain intent is replaced or diluted by local fallback execution;
- local inner-life thresholds/keywords choose focus, mood and intention;
- conversational silence policy has no explicit obligation to consciously re-evaluate repeated unresolved direct questions.

Expected behavior: personality, fear, memory, relationship, current state and grounded situation are inputs to the existing cognitive architecture; Global Workspace owns the final psychological choice. Android code enforces only hard feasibility/world rules and compiles the Brain choice to legal actions.

## 2. Current-design traceability

Source-of-truth baseline: `main` at `4d5b43b09bf4601a8d9c40180c692d8ad978c740`, v0.4.26 / versionCode 43.

Relevant classes: `DungeonParticipationState`, `DungeonParticipationPolicy`, `DungeonParticipationChatBridge`, `HumanBaseline`, `ReplyTimerToolSession`, `ReplyTimerRuntimeContext`, `DungeonIntent`, `DungeonPlan`, `DungeonPersonalityPolicy`, `DungeonEngine`, `DungeonActivity`, `NpcInnerLifePolicy`, `BrainEngine`, `DemoRuntimeV032`.

`DungeonParticipationInference` is intentionally deleted in v0.4.27. Participation may not be reconstructed from emitted wording or phrase dictionaries.

Relevant architecture record: `docs/architecture/personality-integration-audit.md`; topology freeze: `docs/architecture/change-control.md`.

## 3. Evidence

The existing project personality audit already establishes the applicable design principle: personality is a cross-cutting parameter/state input to salience, valuation, executive control and action selection; it must not be reduced to one-to-one hard-coded trait rules. The current failure is therefore an implementation/data-contract defect inside the approved topology, not evidence for a new module.

## 4. Alternatives considered

1. Lower the participation thresholds: rejected because it preserves a hidden post-Brain gate.
2. Expand Japanese/English phrase dictionaries: rejected because lexical post-processing still owns the decision and fails on silence/paraphrase.
3. Keep HP/trait overrides as safety rules: rejected for psychological choices. HP=0 death, collision, visibility and adjacency remain hard rules; fear/retreat/continued combat are character choices.
4. Add a new cognition module: rejected; existing valuation/action_selection/Global Workspace already own this function.
5. Selected approach: keep 9 specialists + Global Workspace, remove psychological post-gates, use structured Brain/runtime decisions, and make local dungeon execution a feasibility/compiler layer.

## 5. Final authority contracts

### 5.1 Dungeon participation

- Global Workspace owns `accept/refuse/hesitate/withdraw`.
- `willingness`, `fear`, and `resolve` remain descriptive compatibility/monitor values only. They never gate stance.
- `personal_reason` is optional and never a prerequisite.
- The old lexical participation inference path is deleted.
- Conversational Global Workspace cycles must invoke `npc_runtime_decision` exactly once. `none` is the explicit no-write operation.
- Participation state writes are independent of visible speech. Silence does not prevent a participation decision from being persisted.
- If participation is decided now but a grounded temporary condition delays the conversational reply, the combined runtime operation records participation immediately and independently schedules the reply timer.

### 5.2 Dungeon action execution

- A legal same-cycle `environment_action` from Brain is executed directly.
- The Android local layer may reject/replace that exact action only when hard world state makes it infeasible, such as collision, invalid/non-visible target, or other physical legality failure.
- Persistent Brain `dungeon_plan` is used as authored. Structured Brain plan values are bounded for data validity but are not blended with Android-side personality formulas.
- Compatibility conversion from an older Brain intent without `dungeon_plan` maps only the categorical strategy. It keeps numeric preference values neutral instead of inventing courage/aggression/risk values.
- Big Five traits are not used by the local dungeon executor to make a second psychological decision.
- HP does not force EVADE or participation withdrawal. HP=0 still causes death.
- Selected and background dungeon execution both preserve Brain authority. A one-turn Brain action is consumed only on its exact floor/turn; later turns use the persistent Brain plan plus a neutral legal executor until a cognition trigger requests Brain reassessment.

### 5.3 Conversation and inner life

- Repeated unresolved direct questions are re-evaluated as new social input instead of mechanically inheriting prior silence.
- Silence remains a valid character choice when grounded in personality, relationship, current state or situation; it is not the default suppression rule.
- Local inner-life ticks may evolve bounded descriptive signals, but they do not replace Brain-authored mood/focus/intention with keyword or fixed-threshold choices.

## 6. Module-boundary test

No module is added, removed, split, merged, reordered or made recurrent. No new persistent memory subsystem is introduced. Global Workspace keeps its existing responsibility to integrate cognition into NPC speech/action; structured participation is an additional representation of the same integrated choice, not a new cognitive owner.

## 7. Evaluation plan

- explicit accept becomes accepted in one Brain decision regardless of numeric state or empty reason;
- explicit refusal/withdraw remains authoritative;
- no deterministic low-HP withdrawal or EVADE;
- same persistent Brain intent/plan + same board yields the same local behavior when only Big Five values change;
- legal same-cycle Brain action executes directly; hard world constraints can still reject illegal actions;
- Brain EVADE/HOLD does not expire solely by a local turn lease;
- structured Brain plan values are not blended with local psychological formulas;
- legacy intent fallback keeps numeric psychological weights neutral;
- participation state can persist even when visible reply is silent or deferred;
- participation + grounded reply deferral can be recorded in the same Global Workspace cycle;
- inner-life local ticks do not replace AI-generated mood/focus/intention from keyword/threshold rules;
- repeated direct-question policy is present in conversational runtime context;
- stale one-turn Brain dungeon actions are not reused on later background turns;
- all existing hard-rule, hidden-information, map, combat, death, persistence and build tests remain green.

## 8. Migration and rollback

Existing participation JSON remains readable. Existing stance is preserved. Numeric willingness/fear/resolve remain bounded compatibility/monitor fields but cease to gate stance. Existing DungeonPlan/Mind JSON remains readable. Rollback is the parent commit of the v0.4.27 change branch/PR. No memory deletion is required.

APK version: v0.4.27 / versionCode 44.

Verification evidence:

- GitHub Actions run: `32801326094` (`Android CI` #172)
- `:app:testDebugUnitTest`: success, 186 tests
- `:app:assembleRelease`: success
- release signing: success
- artifact upload: success
- artifact: `NPCBrain-v0.4.27-release`
- APK SHA-256: `d47aac0287da3120fd26f09194147759e35d02ad61b969672cf80bafd75e8745`

## 9. Approval

The user first requested a logical explanation before changes, then requested correction of the identified bad specifications, later instructed implementation to continue while APK delivery could wait, and finally requested that work continue through APK delivery. This is approval for the non-topology corrective implementation and final v0.4.27 APK handoff described above.
