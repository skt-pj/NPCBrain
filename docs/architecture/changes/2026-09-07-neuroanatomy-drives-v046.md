# v0.4.46 Neuroanatomy / Primary Drives

Status: implementation branch.

## Scope

The existing 9 parallel cognition specialists plus Global Workspace remain unchanged as the cognitive topology. This change adds a cross-cutting many-to-many neuroanatomy context and extends Inner Life physiology from hunger alone to hunger, sleep pressure, and reproductive drive with stable per-NPC individual variation.

## Neuroanatomy representation

`NeuroanatomyModel` maps each existing cognition owner to multiple major brain regions/networks. The same region can participate in multiple cognition owners. The mapping is a functional approximation rather than a firing-rate simulation and does not create new LLM modules.

The mapping is included in `character_state.neuroanatomy`, which is already delivered unchanged to all 9 specialists and the Global Workspace. `interoceptive_signals` carries bounded energy, hunger, sleep pressure, reproductive drive, and safety concern from `character_state.inner_life`.

The Global Workspace remains the final owner of psychological choice. A brain-region label or a physiological scalar never independently decides an action.

## Primary-drive physiology

`NpcInnerLifeState` now persists:

- `hunger` and `hunger_sensitivity`
- `sleep_pressure` and `sleep_sensitivity`
- `reproductive_drive` and `reproductive_set_point`

Initial values and sensitivities are stable deterministic functions of NPC ID. Existing saved Inner Life JSON remains readable; missing new fields are backfilled without altering the old stored fields.

`NpcInnerLifePolicy` uses exact canonical schedule activities only:

- `sleep`: energy rises and sleep pressure falls.
- non-`sleep`: energy falls and sleep pressure rises.
- `meal`: hunger falls.
- non-`meal`: hunger rises.
- reproductive drive slowly returns toward the NPC-specific set point and is not satisfied by matching arbitrary action text.

Local physiology does not replace mood, focus, intention, consent, relationship state, participation, or dungeon decisions. Reproductive drive is only an internal motivational signal; it does not by itself establish romantic or sexual behavior or consent.

## Compatibility and topology

- No cognition module is added, removed, reordered, split, or made recurrent.
- BrainEngine parallel fan-out/fan-in and API request count are unchanged.
- SharedPreferences namespace `npcbrain_inner_life_v1` and state/stream keys are unchanged.
- UI layout, permissions, application ID, memory subsystem, conversation routing, dungeon execution, and signing configuration are unchanged.

## Release target

- versionName: `0.4.46`
- versionCode: `66`

Verification requires full JUnit regression, Release/Debug build, common signing verification, PR CI, main CI, and final APK byte/version/hash/certificate verification.
