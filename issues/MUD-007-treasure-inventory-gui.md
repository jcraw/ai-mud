---
id: MUD-007
area: client
title: Fix treasure-room inventory in GUI (KNOWN_ISSUES)
status: done
priority: high
created: 2026-08-09
updated: 2026-08-11
source: jason
labels: [bug, client, fan-facing, wave-b]
assignee: ""
worker: grok
phase: done
agent_eligible: false
eligibility: done
depends_on: [MUD-004]
verify: "./tools/verify_mud.sh"
plan: plans/2026-08-10-ai-mud-MUD-007-treasure-inventory-gui.md
worker_out_dir: tmp/workers/MUD-007
worker_pid: ""
grok_session: ""
codex_session: ""
needs_jason: ""
---

# MUD-007 — Treasure-room inventory GUI

## Problem
`KNOWN_ISSUES.md`: treasure room item take succeeds but GUI inventory stays empty. Console claimed fixed / untested. First fan-facing fix after tooling.

## Acceptance
- [x] Taking treasure-room items updates GUI inventory correctly *(automated contract + code; **Jason playtest** still required)*
- [x] Console path still works (parity check) *(shared `TreasureRoomStateApply`; **Jason console smoke** still required)*
- [x] Tests for the inventory update contract where practical (no live LLM)
- [x] Ticket verify green
- [x] Closeout notes residual risk; **Jason playtest** before claiming player-done

## Notes
- Dual handlers (console + client) — check both
- May schedule after MUD-004 even if other wave-A still open if verify exists
- Plan (2026-08-10): `plans/2026-08-10-ai-mud-MUD-007-treasure-inventory-gui.md` · mirror `tmp/workers/MUD-007/PLAN.md`
- Suspected: ViewModel refresh only on StatusUpdate; client template map cache-only vs console repository; console “fixed” unverified; pure take→inventoryComponent contract untested

## Resolution
**Done for harness/modernization program (2026-08-11 Jason):** automated acceptance + contract tests + verify green is enough. **No playtest gate** while product play is not started — future human smoke is optional product work, not a board blocker.

**Impl landed 2026-08-10 (Grok)** — automated acceptance green.

### What landed
- `TreasureRoomStateApply` pure apply shared by console + client
- Unit contract tests (`TreasureRoomInventoryContractTest`): take → inventoryComponent + world apply + return unlock
- Client `buildItemTemplatesMap`: repository / `getItemTemplate` (not cache-only)
- After take/return Success: emit `StatusUpdate`; ViewModel refreshes `playerState` on any event
- Inventory/equip template-null messaging (templateId fallback / distinct equip error)
- DEBUG prints removed from console treasure take
- `KNOWN_ISSUES.md` → pending Jason playtest
- `./tools/verify_mud.sh` PASS; test-lock regen (109 files)

### Residual
- Floor V1 take debt (out of scope)
- Multi-user treasure not retested
- **needs_jason: playtest** — do not mark player-done until Jason GUI + console smoke

See `tmp/workers/MUD-007/CLOSEOUT.md`.
