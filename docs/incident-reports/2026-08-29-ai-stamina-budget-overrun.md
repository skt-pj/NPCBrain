# AI STAMINA budget overrun incident — 2026-08-29

## Severity
Critical cost-control defect.

## Observed impact
The all-NPC usage screen showed four active NPCs with a nominal monthly budget of ¥10 each (¥40 total) while the local usage ledger had reached approximately ¥1551.66 total. The intended per-NPC budget was therefore not being enforced as a hard API-send limit.

## Root cause
`NpcAiStaminaStore.recordUsage(...)` only recorded usage after an OpenAI Responses API call had succeeded. `OpenAiClient` did not perform a budget check before sending request bytes. The Brain runtime also runs nine specialists concurrently, so even adding a non-atomic `remaining > 0` check would still permit multiple simultaneous requests to cross the limit together.

## Corrective action in v0.4.39-hotfix3
- Add a conservative per-request cost reservation before every NPC-attributed Responses API POST.
- Share outstanding reservations across all `NpcAiStaminaStore` instances with one process-wide lock.
- Include outstanding parallel reservations when deciding whether another request may start.
- Release reservations only after synchronous usage recording or request failure.
- Bound ordinary NPC Brain specialist/global maximum output tokens so a full parallel cycle can be budgeted conservatively.
- Route Dungeon Brain usage through the same central OpenAI attribution/accounting path.
- Preserve historical spend rather than resetting it; NPCs already over ¥10 are blocked immediately.

## Regression requirements
- An NPC with spent >= ¥10 cannot start another attributed API request.
- `spent + outstanding reservations + new reservation` must never be allowed above ¥10.
- Reservation must occur before request bytes are written to the network.
- Failed requests release their reservation.
- Dungeon, conversation/spontaneous, function continuation, and ambient NPC calls use the same central preflight path when attributed to an NPC.
- Release and Debug APKs are both produced for the hotfix.
