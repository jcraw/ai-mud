---
id: MUD-024
area: engine
title: Purge V1 inventory/equip production write paths
status: open
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [chore, inventory, v2, wave-g]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-023]
verify: "./tools/verify_mud.sh --core"
plan: ""
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
- [ ] Inventory of production write sites for `.inventory =`, `addToInventory`, `removeFromInventory`, V1 equip setters in `app/`, `client/`, `reasoning/` (exclude tests if still bridging)
- [ ] Each Success write path uses `inventoryComponent` / equip-slot APIs only
- [ ] Grep gate documented in closeout: zero new V1 writes on Success in handlers (list exceptions if test-only)
- [ ] Smoke contract or existing tests still green; add thin tests only where a path had none
- [ ] `docs/V2_REMOVAL_PLAN.md` + `docs/TODO.md` remaining-work notes updated (fields may remain `@Deprecated` for one more slice)
- [ ] `./tools/verify_mud.sh --core` exit 0

## Non-goals
- Delete deprecated fields from PlayerState (optional follow-up if greps clean)
- Skills map V1 purge (only if trivial same PR; else note residual)
- Mass detekt baseline regen
- git push (Astra)

## Notes
Depends on drop parity (023) so take+drop both V2 before write purge.
