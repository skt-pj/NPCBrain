# Personality Integration Audit

Status: **Reviewed; no topology change approved**

Version under review: NPCBrain v0.1.3 (`VERSION_CODE=4`)

## Executive conclusion

NPCBrain's current 9 specialist modules + Global Workspace should remain structurally unchanged for the personality work.

The evidence supports treating personality as stable individual differences in parameters of existing cognitive, motivational, affective, and behavioral processes. It does **not** support mapping each personality-relevant psychological label to a new LLM module.

The implementation target should therefore be:

1. add a persistent character/personality state as data, not as a new processing node;
2. pass the relevant character state to existing modules;
3. modify each module's weighting and output contract in a traceable way;
4. keep objective world facts separate from personality-weighted salience, concern, and value;
5. change the existing Global Workspace from assistant-style answer synthesis to in-world NPC action/utterance synthesis only after explicit design approval;
6. validate with fixed-scene personality regression tests before considering any new module boundary.

## Why the original brain architecture had no explicit personality

The original research record `brain-inspired-ai.functional-cognitive-architecture` answered a narrower question: how to decompose general human cognitive functions into a functional AI architecture. Its practical decomposition covered perception, salience, working/global workspace, executive control, episodic/semantic memory, world prediction, valuation/motivation, action selection, execution, error monitoring, and learning/consolidation.

That decomposition is a species-general functional model. Personality is not another species-general cognitive function analogous to episodic memory or visual perception. It is primarily a pattern of stable individual differences in how common mechanisms react, prioritize, persist, explore, interpret, and choose.

Therefore the original decomposition is not invalid merely because it lacks a `personality` node. However, it was incomplete for the broader product goal of simulating an **individual person/NPC**, because the scope distinction between generic cognition and stable individual differences was not made explicit.

## Evidence basis

### 1. Cybernetic Big Five Theory

DeYoung (2015), *Journal of Research in Personality*, DOI `10.1016/j.jrp.2014.07.004`:

- personality traits are parameters of evolved cybernetic mechanisms;
- characteristic adaptations are goals, interpretations, and strategies stored in updateable memory;
- the theory explicitly cautions against overly simple one-to-one mappings of Big Five traits to single stages of the control cycle.

Engineering implication: personality should modulate existing control functions and persistent memory contents across the cycle.

### 2. Motivational reaction-norm interpretation

Denissen & Penke (2008), *Journal of Research in Personality*, DOI `10.1016/j.jrp.2008.04.002`:

- Extraversion: reward activation, particularly social reward;
- Agreeableness: cooperation vs selfishness in conflicts;
- Conscientiousness: tenacity of goal pursuit under distraction;
- Neuroticism: punishment/threat activation, including social exclusion cues;
- Openness: reward from cognitive engagement/exploration.

Engineering implication: these are weighting/reaction differences that map naturally to existing salience, executive control, valuation, world modeling, and action selection.

### 3. Personality network neuroscience

Hilger & Markett (2021), *Network Neuroscience*, DOI `10.1162/netn_a_00198`:

- it remains unresolved whether personality traits are specific, dissociable biophysical entities;
- a network perspective is more plausible than isolated region mappings.

Chen & Canli (2022), *Personality Neuroscience*, DOI `10.1017/pen.2021.5`:

- systematic review/meta-analysis found no robustly replicable Big Five structural brain correlates.

Engineering implication: do not justify a new architecture node by claiming a dedicated neural personality substrate.

### 4. Affect and social cognition are also distributed

Lindquist & Barrett (2013), *Current Opinion in Neurobiology*, DOI `10.1016/j.conb.2012.12.012`:

- emotional, social, and cognitive phenomena can be understood through domain-general, distributed large-scale networks rather than discrete isolated modules.

Schurz, Maliske, & Kanske (2020), *Cortex*, DOI `10.1016/j.cortex.2020.05.006`:

- theory-of-mind, empathy, and action observation involve cross-network integration/segregation.

Engineering implication: appraisal and social cognition are important processes, but the neuroscience does not require them to be separate LLM nodes in NPCBrain.

### 5. Modular-agent evidence sets a higher bar for adding modules

Webb, Mondal, & Momennejad (2025), *Nature Communications*, DOI `10.1038/s41467-025-63804-5`:

- specialized modules improved planning;
- ablations established that individual components contributed to performance.

Engineering implication: a new module should be added only when it owns a distinct function/state/interface and its removal produces a predicted selective deficit or measurable loss. Psychological naming alone is not sufficient.

## Personality representation without topology changes

The personality state should be data shared with existing modules.

### Stable traits

Initial implementation may use continuous Big Five dimensions, optionally refined to aspects later if evaluation demonstrates benefit. Do not hard-code a one-to-one trait/module mapping.

Suggested state:

```json
{
  "traits": {
    "extraversion": 0.5,
    "neuroticism": 0.5,
    "agreeableness": 0.5,
    "conscientiousness": 0.5,
    "openness": 0.5
  },
  "current_state": {
    "valence": 0.0,
    "arousal": 0.0,
    "stress": 0.0
  }
}
```

The exact schema is **not yet approved for implementation**. It is shown only to clarify that personality is shared state, not a new module.

### Characteristic adaptations

Goals, values, fears, loyalties, relationships, habits, role identity, and self-beliefs should live inside the existing long-term memory architecture, especially semantic memory, with explicit type metadata. This follows Cybernetic Big Five Theory's distinction between stable traits and updateable goals/interpretations/strategies.

## Existing-module responsibility matrix

### perception

Role remains factual state extraction.

Personality rule:
- no rewriting of observed facts;
- no personality-driven hallucination;
- optional neutral tags such as `social`, `threat_candidate`, `novel`, or `goal_relevant` may be emitted if they are grounded in the observation.

Reason: personality should change reaction to a fact more than the fact itself.

### salience

Primary personality entry point for attention.

Possible modulation:
- Extraversion: social/reward opportunity weighting;
- Neuroticism: threat, uncertainty, rejection weighting;
- Openness: novelty/information weighting;
- Conscientiousness: duty, unfinished goal, rule/standard weighting;
- Agreeableness: others' needs, conflict, cooperation weighting.

No new attention module is required.

### episodic_memory

Stored event content remains grounded.

Personality may alter retrieval priority through a bounded bias term, while relevance/recency/importance remain independent. This prevents a personality-confirmation feedback loop.

### semantic_memory

Keep the existing module. Add typed records rather than a new self-model module.

Possible record classes:
- `world_fact`
- `self_belief`
- `goal`
- `value`
- `relationship`
- `habit_strategy`
- `role_identity`

This preserves one semantic-memory boundary while allowing person-specific information.

### world_model

Keep prediction inside the existing world model.

Outputs should distinguish:
- estimated external likelihood;
- character-specific concern/desirability.

Personality may alter which plausible futures receive more sampling/attention, but should not silently overwrite physical or causal constraints.

### executive_control

Personality may modify:
- goal persistence;
- switching threshold;
- tolerance for distraction;
- plan orderliness;
- exploration vs exploitation tendency.

Conscientiousness should not be treated as a generic intelligence boost.

### valuation

This is the strongest existing home for personality-driven affect/motivation.

Possible value dimensions:
- reward;
- threat/safety;
- social affiliation;
- status;
- cooperation/fairness;
- curiosity/information gain;
- duty/goal completion;
- effort/cost;
- relationship impact.

Stable personality changes weights; current state changes momentary gain. The resulting affect/motivation state can be broadcast through existing working memory/Global Workspace.

### error_monitor

Keep objective detection separate from subjective concern.

Example distinction:
- `error_probability`: evidence-based estimate;
- `concern`: character-specific reaction to possible error.

This prevents neurotic or conscientious profiles from being modeled as simply 'more correct.'

### action_selection

Personality must become behavior here.

The selector should choose among candidates using both objective feasibility and personality-weighted valuation. If personality changes only final wording, the architecture has failed its purpose.

### Global Workspace

No new renderer node is required at this stage.

The existing Global Workspace can own two outputs:
- internal/public debug summary for the brain monitor;
- in-world NPC product output (`utterance`, `action`) without assistant commentary.

The current contract (`final answer`, `next action`, `rationale`) is an identified source of assistant-like behavior and should be changed only after approval under the architecture change-control gate.

## What is explicitly NOT approved

The following are not approved by this audit:

- adding `affective_appraisal` as a new specialist node;
- adding `social_cognition` as a new specialist node;
- adding `character_renderer` as a new specialist node;
- splitting existing memory modules solely because personality research uses different psychological labels;
- changing module order;
- parallelizing/recurrent rewiring;
- removing current specialist nodes.

Any of those requires a separate evidence-backed ADR and user approval.

## Acceptance tests required before implementation is considered successful

Use the same scene with multiple fixed character profiles.

Required invariants:
1. perception facts stay substantially stable across personalities;
2. salience rankings differ in trait-relevant ways;
3. memory retrieval may differ but must remain relevant and traceable;
4. value rankings differ consistently;
5. selected actions differ plausibly;
6. product output contains only NPC speech/action, not generic assistant advice;
7. repeated multi-turn behavior remains consistent with stored goals/relationships;
8. disabling personality state returns behavior close to a neutral baseline;
9. no new module is needed to pass these tests unless evidence shows a specific failure that existing boundaries cannot represent.

## Architecture decision

**Keep the 9 specialist + Global Workspace topology unchanged.**

Personality work proceeds as a cross-cutting state-and-parameter integration inside existing responsibilities. Architecture changes are blocked until a documented module-level failure and explicit approval justify them.
