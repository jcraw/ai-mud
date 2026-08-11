---
id: MUD-023
area: engine
title: Drop path → V2 InventoryComponent parity
status: open
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [bug, inventory, wave-g]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-019]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-023
worker_pid: ""
grok_session: ""
codex_session: ""
plan_session: ""
impl_session: ""
---

# MUD-023 — Drop path → V2 InventoryComponent parity

## Problem
MUD-019 fixed **floor take** → V2 `InventoryComponent`. KNOWN residual: **drop** (and any give/corpse edge that still strips via V1 `inventory` list) can desync V2 vs floor/entity state — Success narrative with wrong component writes.

## Acceptance
- [ ] Drop Success removes matching `ItemInstance` from V2 `inventoryComponent` (by instanceId/templateId/name rules consistent with take)
- [ ] Floor/space gains entity (or itemsDropped) consistent with take reverse; console + GUI (+ GameServer if present) share pure apply helper
- [ ] No Success path that only mutates deprecated V1 `PlayerState.inventory` for drop
- [ ] Contract tests (mock LLM): drop → component loses item; space gains it; missing item → Failure
- [ ] KNOWN_ISSUES drop residual marked fixed/narrowed
- [ ] `./tools/verify_mud.sh --core` exit 0; test-lock if tests added

## Non-goals
- Full V1 field deletion
- Trade/pickpocket redesign
- MUD-007 treasure playtest
- git push (Astra)

## Notes
Mirror `FloorItemTakeApply` / `TreasureRoomStateApply` pure-helper pattern.
