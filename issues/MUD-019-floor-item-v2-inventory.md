---
id: MUD-019
area: engine
title: Floor-item take → V2 InventoryComponent parity
status: done
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [bug, inventory, wave-f]
assignee: ""
worker: grok
phase: done
approved_by: Astra
approved_at: "2026-08-11 12:21 MST"
plan_session: 019ff234-15f7-7461-9b1f-af515919382c
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-007]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-019-floor-item-v2-inventory.md
worker_out_dir: tmp/workers/MUD-019
worker_pid: ""
grok_session: ""
codex_session: ""
plan_session: ""
impl_session: ""
---

# MUD-019 — Floor-item take → V2 InventoryComponent parity

## Problem
MUD-007 fixed **treasure-room** take → `InventoryComponent` (V2) for console + GUI, but KNOWN residual risk remains: **floor-item take** may still use V1 `addToInventory` while inventory/equip/HUD read **V2 first**. Same empty-inventory class of bug for ordinary loot.

## Acceptance
- [x] Floor-item take Success puts an `ItemInstance` into V2 `InventoryComponent` (not V1-only)
- [x] Console + GUI share the same apply path (or both call one pure helper) — parity
- [x] Inventory list / equip / wield see the taken floor item after Success
- [x] Unit/contract tests cover take Success → inventory contains `templateId` (mock LLM)
- [x] `./tools/verify_mud.sh --core` exit 0; test-lock regen only if tests intentionally changed
- [x] Update `KNOWN_ISSUES.md` residual note (floor path fixed or narrowed)

## Non-goals
- Treasure-room playtest closeout (still Jason / MUD-007)
- Full V1 field deletion / V2_REMOVAL_PLAN mass migration
- Multi-user treasure retest
- SkillManager / quarantine debt

## Notes
- Read MUD-007 plan + closeout for the treasure apply pattern (`TreasureRoomStateApply` style).
- Prefer pure helper + thin handlers over dual divergent paths.

## Closeout
- Pure `FloorItemTakeApply` under `reasoning/.../inventory/`
- Thin take/takeAll: `ItemHandlers`, `ClientItemHandlers`, `GameServer` (+ MultiUserGame wires itemRepository)
- Contract tests: `FloorItemTakeContractTest` (5 cases)
- `KNOWN_ISSUES.md` residual floor note fixed/narrowed (drop/legacy residual remain)
- verify: `./tools/verify_mud.sh --core` exit 0 · `tmp/dod-summary.json`
- CLOSEOUT: `tmp/workers/MUD-019/CLOSEOUT.md`
