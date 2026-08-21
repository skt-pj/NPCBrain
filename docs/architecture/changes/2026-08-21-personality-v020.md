# Personality Integration Change Packet — v0.2.0

Date: 2026-08-21  
Approval: user explicitly requested implementation after reviewing the personality integration audit.  
Incident reference: `NPCB-INC-20260821-01`

## 1. Problem statement

Observed failure: NPCBrain v0.1.3 technically runs, but the final product output often reads like a generic AI assistant.

Reproduction pattern:
- input a scene such as `ここはどこ？`;
- specialist modules run;
- Global Workspace is explicitly asked to produce a coherent response, next action, and rationale;
- the UI appends `次の行動:` and `理由:`.

Expected behavior:
- the configured character reacts as an NPC;
- stable personality and learned character adaptations influence attention, memory use, valuation, control, and action selection;
- final product output contains only plausible in-world speech/action;
- developer-facing reasoning summaries remain in the brain monitor.

## 2. Current-design traceability

Baseline source: NPCBrain v0.1.3, `BrainEngine.java` blob `8c1b0cde33e38edf7b87444ca56e5f901f5ac79d`.

Protected topology remains unchanged:

1. perception
2. salience
3. episodic_memory
4. semantic_memory
5. world_model
6. executive_control
7. valuation
8. error_monitor
9. action_selection
10. Global Workspace

Relevant evidence:
- `docs/incident-reports/2026-08-21-personality-architecture.md`
- `docs/architecture/personality-integration-audit.md`
- `docs/architecture/change-control.md`
- `brain-inspired-ai.functional-cognitive-architecture`
- `brain-inspired-ai.personality-modulated-cognitive-architecture`
- `project.npcbrain.personality-integration-audit`

## 3. Evidence

The approved implementation follows the audit rather than adding new cognitive modules.

- DeYoung (2015), Cybernetic Big Five Theory, DOI `10.1016/j.jrp.2014.07.004`: traits can be represented as stable parameters of shared adaptive/control mechanisms; goals, interpretations, and strategies are updateable characteristic adaptations.
- Denissen & Penke (2008), DOI `10.1016/j.jrp.2008.04.002`: Big Five differences can be treated as stable motivational reaction differences.
- Hilger & Markett (2021), DOI `10.1162/netn_a_00198`: personality is better treated through network-level individual differences than one-trait/one-region mapping.
- Webb, Mondal, & Momennejad (2025), DOI `10.1038/s41467-025-63804-5`: module boundaries require controlled functional justification and ablation evidence.

## 4. Alternatives considered

### A. Add personality-specific modules
Rejected by the audit. Evidence did not justify new independent module boundaries.

### B. Add only a final persona/style prompt
Rejected. It changes wording but does not reliably change salience, memory use, valuation, and behavior.

### C. Keep topology and add shared character state plus typed long-term adaptations
Approved. This is the lowest-impact design that addresses the observed failure while preserving the researched architecture.

### D. Keep final `answer + action + rationale`
Rejected for product output. The contract itself drives assistant-like behavior.

## 5. Approved responsibility/interface changes

No module is added, removed, split, merged, or reordered.

Shared `CharacterState` data:
- character name;
- five continuous Big Five traits;
- current valence/arousal/stress;
- speech style.

Existing semantic memory stores typed characteristic adaptations:
- `role_identity`
- `value`
- `goal`
- `fear`
- `relationship`
- learned `self_belief`, `habit_strategy`, and world facts.

Module behavior:
- perception remains fact-grounded;
- salience receives personality-weighted priorities;
- episodic retrieval gains only a bounded character-relevance term;
- semantic memory separates typed personal adaptations from ordinary world facts;
- world model separates external likelihood from subjective concern/desirability;
- executive control uses persistence/switching/exploration/order tendencies;
- valuation is the primary personality/motivation weighting point;
- error monitor separates error probability from subjective concern;
- action selection chooses an in-world action;
- Global Workspace integrates an NPC utterance/action instead of an assistant answer.

Brain monitor:
- keeps the same ten cards;
- adds one public `personality_effect` summary per existing stage;
- continues to prohibit hidden chain-of-thought.

## 6. Evaluation plan

Baseline: v0.1.3.

Changed system: v0.2.0.

Fixed acceptance scenes should compare at least three profiles while keeping the scene identical:
1. high Neuroticism / low Extraversion;
2. high Extraversion / high Openness;
3. high Conscientiousness / high Agreeableness.

Success:
- perception facts remain materially stable across profiles;
- salience/valuation/action differ in trait-consistent ways;
- final output contains in-world speech/action and no mandatory `次の行動:` / `理由:` assistant template;
- the brain monitor reports where personality influenced a stage;
- memory from v0.1.3 remains readable;
- module count/order remains unchanged;
- all calls remain `gpt-5.6-luna` with `reasoning.effort=max`;
- Android release build, signing, and signature verification pass.

Regression:
- API-key storage unchanged;
- network fallback unchanged;
- Pixel 10a edge-to-edge layout unchanged;
- learned memory clear operation preserves configured character adaptations.

Latency/cost:
- number of model calls remains 10 per cognitive cycle;
- prompt tokens increase because character state and typed adaptations are included;
- no additional model-call latency from new modules.

## 7. Migration and rollback

Migration:
- existing episodic entries remain unchanged;
- existing semantic entries without `type` are read as `world_fact`;
- existing semantic entries without `source` are treated as learned memory;
- configured character adaptations are stored in the existing semantic-memory store with `source=profile`.

Rollback:
- source rollback is the parent commit immediately before the v0.2.0 implementation commit;
- no irreversible memory migration is performed;
- old code can still read semantic `text`, `strength`, and `last_ms` fields and ignores additional metadata.

## 8. Approval

Architecture topology change: **none**.

Global Workspace output-contract change and shared-character-state integration were explicitly authorized by the user's instruction to implement the audited design as an APK.

Implementation may proceed on `main`; no feature branch is used.

## 9. Implementation and verification result

Implemented app commit: `0e870133498ff73da43aef1b36570469a573785c`.

Verified CI run: `32455477066`.

Result: success for `testDebugUnitTest`, `assembleRelease`, APK signing, signature verification, and artifact upload.

Artifact:
- ID: `9437111610`
- name: `NPCBrain-v0.2.0-release`
- APK SHA-256: `3ff3d2dfa0873c7f6ca6f92e8beddd906859cf958746aee083d26faa7bf06f4b`
- signing: v1/v2/v3 verified
- signer certificate SHA-256: `e648320071b8ab0de038f76790064b99461b67d7dbe7ce7506d12a5f4fa884d8`

The final source tree contains the fixed 9 specialist modules + Global Workspace, the new shared `CharacterStateStore`, typed semantic adaptations, personality-aware brain-monitor summaries, and the NPC utterance/action output contract.

## 10. Execution note

During repository manipulation, one unintended transient commit `c2daee640fb93017aa368c9dde8efc7ef878c92d` with message `noop` created an empty file named `noop`. The immediately following implementation commit removed that file while applying the intended v0.2.0 tree. The final tree contains no `noop` file.

Because the transient commit was already published to `main`, history was not force-rewritten. It caused one unnecessary CI run (`32455429000`) for v0.1.3. This is retained as an explicit evidence trail rather than hidden by rewriting shared history.
