---
id: MUD-024
area: engine
title: Purge V1 inventory/equip production write paths
status: done
priority: med
created: 2026-08-11
updated: 2026-08-12
source: jason
labels: [chore, inventory, v2, wave-g]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-023]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-024-v1-inventory-write-purge.md
worker_out_dir: tmp/workers/MUD-024
worker_pid: ""
grok_session: ""
codex_session: ""
plan_session: ""
impl_session: ""
---

# MUD-024 — Purge V1 inventory/equip production write paths

## Problem
`docs/V2_REMOVAL_PLAN.md` / TODO: deprecated V1 `PlayerState.inventory` / `equippedWeapon` / `equippedArmor` still written or relied on in production handlers after take/drop fixed. Modernization closeout = **no production Success path writes V1 inventory/equip fields**; reads may keep deprecated fallbacks briefly if needed.

## Acceptance
- [x] Inventory of production write sites for `.inventory =`, `addToInventory`, `removeFromInventory`, V1 equip setters in `app/`, `client/`, `reasoning/` (exclude tests if still bridging)
- [x] Each Success write path uses `inventoryComponent` / equip-slot APIs only
- [x] Grep gate documented in closeout: zero new V1 writes on Success in handlers (list exceptions if test-only)
- [x] Smoke contract or existing tests still green; add thin tests only where a path had none
- [x] `docs/V2_REMOVAL_PLAN.md` + `docs/TODO.md` remaining-work notes updated (fields may remain `@Deprecated` for one more slice)
- [x] `./tools/verify_mud.sh --core` exit 0

## Non-goals
- Delete deprecated fields from PlayerState (optional follow-up if greps clean)
- Skills map V1 purge (only if trivial same PR; else note residual)
- Mass detekt baseline regen
- git push (Astra)

## Notes
Depends on drop parity (023) so take+drop both V2 before write purge.

## Closeout
- Pure `GiveItemApply` + `UseConsumableApply`; equip V2 on all surfaces; GUI buy `addItemInstance`
- Grep gate 0 mutator hits in app/client/reasoning main; exceptions: core PlayerState defs + testbot
- `--core` PASS · test-lock 114 · `tmp/dod-summary.json` · full note `tmp/workers/MUD-024/CLOSEOUT.md`
