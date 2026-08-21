# Architecture Change Control

This document is mandatory for NPCBrain architecture changes.

## Purpose

NPCBrain is a research-oriented cognitive architecture. Module churn is costly because it changes prompts, memory contracts, UI monitoring, evaluation baselines, latency, API cost, and accumulated research assumptions. Therefore architectural topology must not change from ad-hoc interpretation of a paper or from a single observed behavior.

## Protected architecture elements

The following are architecture-level and require the full gate below:

- adding or removing a specialist module;
- merging or splitting specialist modules;
- changing module order or recurrence;
- changing Global Workspace coordination topology;
- introducing a new persistent memory subsystem;
- changing ownership of durable state between modules;
- changing the primary final-output contract in a way that changes module responsibility.

Prompt wording, UI copy, and implementation details may still require review, but are not automatically topology changes.

## Required change packet

No architecture change may be implemented until one written change packet contains all items below.

### 1. Problem statement

- observable failure;
- reproducible input/scenario;
- expected behavior;
- actual behavior;
- why the problem is architectural rather than a prompt/data/test defect.

### 2. Current-design traceability

- existing module(s) responsible for the behavior;
- current input/output/state contracts;
- relevant source files and commit SHA;
- relevant research/ADR/knowledge IDs.

### 3. Evidence

At least one of:

- peer-reviewed primary research supporting a separable function;
- authoritative cognitive-architecture evidence;
- controlled project experiment demonstrating that the existing boundary cannot represent the required behavior.

A psychological or neuroscientific label by itself is not evidence for a software module boundary.

### 4. Alternatives considered

At minimum compare:

- no topology change / state or parameter change;
- prompt or output-contract change;
- data-model or memory-metadata change;
- architecture topology change.

Explain why the lower-impact alternatives are insufficient.

### 5. Module-boundary test

A proposed new module must have:

- distinct responsibility;
- distinct inputs and outputs;
- state ownership, if any;
- expected selective failure if disabled;
- reason it cannot remain a subfunction of an existing module.

### 6. Ablation / evaluation plan

Before merge, define:

- baseline;
- changed system;
- ablated system;
- fixed evaluation scenes/tasks;
- success metrics;
- regression metrics;
- latency/API-cost impact.

A module addition is not accepted merely because the system still works. It must show a measurable benefit or a required selective capability that cannot be achieved with the existing topology.

### 7. Migration and rollback

- state/schema migration;
- backward compatibility;
- rollback commit or procedure;
- effect on stored memories;
- effect on APK/versioning;
- effect on UI brain monitor.

### 8. Approval

Architecture topology changes require explicit user approval after the change packet is presented.

Research alone does not authorize implementation.

## Current freeze

As of incident `NPCB-INC-20260821-01`, the approved topology is:

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

Until explicitly approved otherwise, personality integration must occur within these existing boundaries.

## Evidence standard for future brain-inspired modules

NPCBrain's modularity rationale follows the same general standard demonstrated by Webb, Mondal, & Momennejad (2025), *Nature Communications*, DOI `10.1038/s41467-025-63804-5`: specialized component processes should have clear functional roles and should justify themselves through controlled evaluation/ablation.

Network neuroscience is also a standing constraint: do not infer `one psychological label = one brain region = one LLM module`. The architecture is functional and many-to-many, not literal anatomical emulation.
