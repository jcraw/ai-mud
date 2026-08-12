---
id: MUD-034h
area: engine
title: Split item handlers parity (Wave Q3)
status: done
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: "grok"
worker: "grok"
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: "plans/2026-08-12-ai-mud-MUD-034h-item-handlers-split.md"
worker_out_dir: "tmp/workers/MUD-034h"
parent: MUD-034
---

# MUD-034h — Item handlers split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Item handlers · **peak measured tokens:** 6253 · **note:** parity

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `app/src/main/kotlin/com/jcraw/app/handlers/ItemHandlers.kt` | 6253 | 583 | 6253 |
| `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientItemHandlers.kt` | 4667 | 402 | 4667 |

## Acceptance
- [x] Behavior-preserving extract of `ItemHandlers.kt`, `ClientItemHandlers.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034h` when still needed (both overrides **removed**)
- [x] New extracted `.kt` files meet global E (no override grandfather)
- [x] No unauthorized `src/test/**` edits unless explicitly scoped + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`

## Non-goals
- Raising override caps
- Mass detekt baseline regen · PIT 80% (MUD-035) · MUD-036–038
- Splitting hosts outside this family
- Features / behavior changes

## Notes
- Umbrella: `issues/MUD-034-god-file-split-program.md` · plan `plans/2026-08-12-ai-mud-MUD-034-god-file-split-program.md`
- Ranked inventory: `tmp/workers/MUD-034/RANKED_GODS.md`
- Extract patterns: pure apply objects (MUD-019/023 style); mock LLM; KISS; files under ~1000 lines
- Serial one live builder per tree

## Builder
- session: implement phase complete 2026-08-12 (grok)
- plan: `plans/2026-08-12-ai-mud-MUD-034h-item-handlers-split.md` (mirror `tmp/workers/MUD-034h/PLAN.md`)
- baseline: `tmp/workers/MUD-034h/token_baseline.json`
- remeasure: `tmp/workers/MUD-034h/token_remeasure.json`
- closeout: `tmp/workers/MUD-034h/CLOSEOUT.md`

## Resolution
Behavior-preserving pure-move parity split complete. Hosts thin facades (**6253→366** / **4667→335**). Both host overrides **removed** (under global E). Clusters: Inventory(+format)/Take/DropGive/Equip/Consumable lockstep + app Loot multi-file. `./tools/verify_mud.sh --core` PASS (E=0 W=15). No `src/test/**`. CorpseHandlers + client loot stub left untouched; ItemUseHandlers name clash avoided via `ItemConsumableHandlers`.

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034h-item-handlers-split.md` (mirror `tmp/workers/MUD-034h/PLAN.md`)
- Phase: **done** — APPROVED by Astra 2026-08-12 06:53 MST → fresh impl complete
- Approach: pure-move item handler parity clusters (Inventory/Take/DropGive/Equip/Consumable + app Loot); thin facades; lockstep app↔client; leave CorpseHandlers + client loot stub; remeasure lower/remove overrides; no features; `--core` green
