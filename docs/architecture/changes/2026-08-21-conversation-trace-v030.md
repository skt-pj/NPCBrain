# Conversation + Per-Message Brain Trace Change Packet — v0.3.0

Date: 2026-08-21  
Approval: user requested gradual implementation with the explicit requirement that tapping each NPC conversation message reveals the brain-stage contents responsible for it.  
Prior architecture incident: `NPCB-INC-20260821-01`

## 1. Problem statement

v0.2.0 demonstrates one NPC at a time. The product direction is now a reusable NPC library where multiple NPCs can exist independently and later live through time, occasionally sending messages to users or other NPCs. The demo UI should no longer make the brain monitor the primary experience. Instead, it should present ordinary conversation and let the operator inspect the cognitive trace behind an exact NPC message.

The immediate observable gap is:

- one shared character store;
- one shared long-term memory store;
- one prompt-oriented demo screen;
- brain diagnostics are transient UI state and are not bound to the message they produced;
- no direct-chat/group-chat persistence exists.

## 2. Scope of this increment

v0.3.0 intentionally implements only the reusable foundation needed before life simulation.

Included:

1. independent NPC1/NPC2 personality and long-term-memory storage;
2. three demo rooms: user+NPC1, user+NPC2, user+NPC1+NPC2;
3. persistent conversation records;
4. one cognitive pass per addressed NPC when the user sends a message;
5. persistent ten-stage public brain trace attached to each NPC-generated message;
6. message-tap trace viewer;
7. conversation-first demo UI.

Explicitly excluded:

- autonomous world clock;
- daily schedules;
- location/activity simulation;
- spontaneous messages caused by life events;
- background wakeups/offline catch-up;
- recursive NPC-to-NPC conversation loops;
- cognitive-module topology changes.

## 3. Architecture protection

The protected cognitive topology remains exactly:

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

No specialist is added, removed, split, merged, reordered, or made recurrent in v0.3.0.

All cognitive calls remain OpenAI Responses API `gpt-5.6-luna` with `reasoning.effort=max`.

## 4. Storage decision

### NPC1

NPC1 reuses the existing v0.2.0 `CharacterStateStore` and `MemoryStore` backing preferences. This avoids destructive migration and preserves the previously configured single NPC as the first multi-NPC instance.

### NPC2

NPC2 uses a `ContextWrapper` that namespaces the exact existing preference stores. No cognitive-memory implementation is forked or duplicated.

This is a low-impact migration mechanism. The existing character and memory code remains the source implementation for both NPCs.

### Conversation data

A new `ConversationStore` persists demo/runtime conversation events separately from cognitive long-term memory. Conversation history is environment/runtime state, not a replacement semantic or episodic memory subsystem.

Each message contains the event/message identity, sender, room, timestamp, optional in-world action, cause event, and an optional brain trace.

## 5. Brain trace contract

When a `BrainEngine` stage completes, the existing `ProgressListener` already exposes a public diagnostic summary. v0.3.0 records those outputs rather than requesting hidden reasoning.

Stored trace entry:

- `stage_id`
- `stage_label`
- `summary`
- `confidence`
- `salient_facts`
- `personality_effect`

A normal NPC response produces ten trace records: nine specialists + Global Workspace.

No private scratchpad or hidden chain-of-thought is requested, displayed, or stored.

## 6. Chat execution behavior

A user message is persisted first. The demo runtime then performs at most one cognitive cycle for each NPC participant in that room.

Direct room:
- only the addressed NPC is evaluated.

Three-person room:
- NPC1 is evaluated once;
- if NPC1 sends a message, it becomes part of recent room context;
- NPC2 is then evaluated once and can see that updated transcript;
- processing stops after NPC2.

This fixed one-pass-per-participant rule is deliberate in v0.3.0. It prevents accidental endless model-to-model chat before a proper event scheduler and conversation-continuation policy exist.

The incoming-message prompt explicitly states that receiving a message does not require a response. The character may stay silent and should not invent unrelated off-screen events merely to keep conversation active.

## 7. UI/UX decision

The demo APK is not the final product UI. Its purpose is runtime inspection.

Main screen:
- three conversation rooms;
- no always-visible ten-card brain monitor.

Chat screen:
- familiar left/right message bubbles;
- user messages on the right;
- NPC messages on the left;
- a small `脳内を見る` affordance appears on NPC messages with a saved trace.

Message tap:
- user message: event details only;
- NPC message: the ten cognitive-stage diagnostic cards that produced that exact message.

The existing Pixel-oriented edge-to-edge/safe-inset behavior and 48dp-class top controls are retained.

## 8. Evaluation / acceptance criteria

Build-level:

- `testDebugUnitTest` passes;
- release APK assembles;
- shared signing succeeds;
- `apksigner` verification succeeds.

Functional/demo acceptance:

- room list shows two direct rooms and one three-person room;
- NPC1/NPC2 settings and long-term memories are independent;
- a user message appears immediately in the selected room;
- each addressed NPC performs no more than one cognitive pass;
- an NPC may produce no message;
- if an NPC message is produced, tapping it shows exactly the trace captured during that message's generation;
- each stored trace includes the same ten stage IDs as the frozen architecture;
- no hidden chain-of-thought appears;
- API model remains `gpt-5.6-luna` / `max`.

## 9. Rollback

Rollback is source-only: return to the v0.2.0 verified source commit. The new conversation preferences are additive and ignored by old code. NPC2's namespaced preferences are also additive. NPC1's v0.2.0 storage remains structurally unchanged.

No irreversible migration is performed.

## 10. Next gated increment

After v0.3.0 is validated on-device, the next increment should introduce explicit runtime event/time contracts (`WorldEvent`, `LifeState`, clock advancement, activity transitions) without changing the cognitive topology. Autonomous messaging should only be implemented after those event causes can be persisted and traced.
