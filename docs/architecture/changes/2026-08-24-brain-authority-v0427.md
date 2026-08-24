# v0.4.27 Brain Authority Correction

Status: approved for implementation by the user's 2026-08-24 request to fix all identified post-Brain gates and deliver an APK.

## 1. Problem statement

Current v0.4.26 lets local Android policies override or reconstruct psychological decisions after the 9 specialists + Global Workspace have already made a character decision. Reproduced from source review:

- dungeon participation starts from fixed reluctance/fear, requires numeric thresholds and a lexical personal-reason cue, and is inferred from emitted text rather than owned by the Brain cycle;
- low HP / nearby enemies can deterministically withdraw or force EVADE;
- direct Big Five formulas select combat, retreat and movement behavior;
- persistent Dungeon Brain intent is replaced by local fallback execution;
- local inner-life thresholds/keywords choose focus, mood and intention;
- conversational silence policy has no explicit obligation to consciously re-evaluate repeated unresolved direct questions.

Expected behavior: personality, fear, memory, relationship, current state and grounded situation are inputs to the existing cognitive architecture; Global Workspace owns the final psychological choice. Android code enforces only hard feasibility/world rules and compiles the Brain choice to legal actions.

## 2. Current-design traceability

Source-of-truth baseline: `main` at `4d5b43b09bf4601a8d9c40180c692d8ad978c740`, v0.4.26 / versionCode 43.

Relevant classes: `DungeonParticipationState`, `DungeonParticipationPolicy`, `DungeonParticipationInference`, `DungeonParticipationChatBridge`, `HumanBaseline`, `ReplyTimerToolSession`, `ReplyTimerRuntimeContext`, `DungeonIntent`, `DungeonPlan`, `DungeonPersonalityPolicy`, `DungeonActivity`, `NpcInnerLifePolicy`, `BrainEngine`, `DemoRuntimeV032`.

Relevant architecture record: `docs/architecture/personality-integration-audit.md`; topology freeze: `docs/architecture/change-control.md`.

## 3. Evidence

The existing project personality audit already establishes the applicable design principle: personality is a cross-cutting parameter/state input to salience, valuation, executive control and action selection; it must not be reduced to one-to-one hard-coded trait rules. The current failure is therefore an implementation/data-contract defect inside the approved topology, not evidence for a new module.

## 4. Alternatives considered

1. Lower the participation thresholds: rejected because it preserves a hidden post-Brain gate.
2. Expand Japanese/English phrase dictionaries: rejected because lexical post-processing still owns the decision and fails on silence/paraphrase.
3. Keep HP/trait overrides as safety rules: rejected for psychological choices. HP=0 death, collision, visibility and adjacency remain hard rules; fear/retreat/continued combat are character choices.
4. Add a new cognition module: rejected; existing valuation/action_selection/Global Workspace already own this function.
5. Selected approach: keep 9 specialists + Global Workspace, remove psychological post-gates, use structured Brain/runtime decisions, and make local dungeon execution a feasibility/compiler layer.

## 5. Module-boundary test

No module is added, removed, split, merged, reordered or made recurrent. No new persistent memory subsystem is introduced. Global Workspace keeps its existing responsibility to integrate cognition into NPC speech/action; structured participation is an additional representation of the same integrated choice, not a new cognitive owner.

## 6. Evaluation plan

- explicit accept becomes accepted in one decision regardless of numeric state or empty reason;
- explicit refusal/withdraw remains authoritative;
- no deterministic low-HP withdrawal;
- same dungeon intent/plan + same board yields same local behavior when only Big Five values change;
- Brain EVADE/HOLD does not expire solely by a local turn lease;
- structured Brain plan values are not blended with local psychological formulas;
- inner-life local ticks do not replace AI-generated mood/focus/intention from keyword/threshold rules;
- repeated direct-question policy is present in conversational runtime context;
- all existing hard-rule, hidden-information, map, combat, death, persistence and build tests remain green.

## 7. Migration and rollback

Existing participation JSON remains readable. Existing stance is preserved. Numeric willingness/fear/resolve remain bounded compatibility/monitor fields but cease to gate stance. Existing DungeonPlan/Mind JSON remains readable. Rollback is the parent commit of the v0.4.27 change branch/PR. No memory deletion is required.

APK version: v0.4.27 / versionCode 44. Common Android signing and CI artifact verification remain unchanged.

## 8. Approval

The user first requested a logical explanation before changes, then requested a parallel audit for similar bad specifications, and finally explicitly requested: `全部修正したapkを出して`. This is approval for the non-topology corrective implementation described above.
