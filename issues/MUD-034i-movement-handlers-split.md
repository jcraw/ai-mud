---
id: MUD-034i
area: engine
title: Split movement handlers parity (Wave Q3)
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
plan: plans/2026-08-12-ai-mud-MUD-034i-movement-handlers-split.md
worker_out_dir: tmp/workers/MUD-034i
parent: MUD-034
---

# MUD-034i — Movement handlers split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Movement handlers · **peak measured tokens:** 5770 · **note:** frontier/lazy-fill

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `app/src/main/kotlin/com/jcraw/app/handlers/MovementHandlers.kt` | 5770 | 465 | 5770 |
| `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientMovementHandlers.kt` | 4738 | 389 | 4738 |

## Acceptance
- [x] Behavior-preserving extract of `MovementHandlers.kt`, `ClientMovementHandlers.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034i` when still needed — **N/A both removed**
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
- session: grok fresh IMPL 2026-08-12
- brief: plan under `plans/` APPROVED by Astra → fresh impl

## Resolution
Pure-move movement handlers parity complete. Hosts **5770→266** / **4738→398**. Both host overrides **removed** (under global E). App PostMove (+Populate/Frontier) · Move+Flee · TreasureExit · Look · Search(+success frag) · Travel · Scout lockstep; client Interact stub kept. `--core` PASS. Closeout `tmp/workers/MUD-034i/CLOSEOUT.md`. No `src/test/**`. No git commit.

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034i-movement-handlers-split.md` (mirror `tmp/workers/MUD-034i/PLAN.md`)
- Phase: **done** — APPROVED by Astra 2026-08-12 07:13 MST → fresh IMPL complete
