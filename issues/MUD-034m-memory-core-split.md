---
id: MUD-034m
area: engine
title: Split memory repos + core gods (Wave Q3)
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
plan: plans/2026-08-12-ai-mud-MUD-034m-memory-core-split.md
worker_out_dir: tmp/workers/MUD-034m
worker_pid: ""
parent: MUD-034
---

# MUD-034m — Memory + core split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Memory + core · **peak measured tokens:** 3762 · **note:** repos easier

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `memory/src/main/kotlin/com/jcraw/mud/memory/skill/SQLiteSkillRepository.kt` | 2564 | 252 | 2564 |
| `memory/src/main/kotlin/com/jcraw/mud/memory/item/SQLiteItemRepository.kt` | 3215 | 293 | 3215 |
| `memory/src/main/kotlin/com/jcraw/mud/memory/combat/SQLiteCombatRepository.kt` | 2641 | 245 | 2641 |
| `memory/src/main/kotlin/com/jcraw/mud/memory/world/WorldDatabase.kt` | 2506 | 237 | 2506 |
| `memory/src/main/kotlin/com/jcraw/mud/memory/combat/NarrationVariantGenerator.kt` | 2967 | 290 | 2967 |
| `core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt` | 3762 | 337 | 3762 |
| `core/src/main/kotlin/com/jcraw/mud/core/CombatComponent.kt` | 2599 | 277 | 2599 |

## Acceptance
- [x] Behavior-preserving extract of `SQLiteSkillRepository.kt`, `SQLiteItemRepository.kt`, `SQLiteCombatRepository.kt`, `WorldDatabase.kt`, `NarrationVariantGenerator.kt`, `WorldState.kt`, `CombatComponent.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family _(N/A — memory/core; no app/client pairs)_
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034m` when still needed _(N/A — all 7 host overrides removed)_
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
- session: plan 2026-08-12 (grok) — PLAN ONLY; not impl approval
- brief: plan under `plans/` if substantial → Astra/Jason APPROVED → fresh impl


## Drain note
- 2026-08-12 08:42 MST clear-backlog: Turn 1 PLAN spawned after MUD-034l done.
## Resolution
- Done 2026-08-12 by grok (fresh IMPL after Astra APPROVED plan).
- Pure-move memory + core: SkillRepo* · ItemRepo* · CombatRepo* · WorldSchema* · Narration* · WorldStateNav/Entities/Items · CombatStatusOps/TickOps · thin hosts.
- Hosts: **2564→602** / **3215→622** / **2641→700** / **2506→689** / **2967→434** / **3762→2331** / **2599→1877**.
- All 7 host overrides **removed** (under global E; never raised; no Added override).
- Combat stubs remain empty TODOs. WorldState/CombatComponent stay members. Parity N/A.
- `./tools/verify_mud.sh --core` PASS · dod-summary `tmp/dod-summary.json` · closeout `tmp/workers/MUD-034m/CLOSEOUT.md`.
- No `src/test/**` · no features · no 034n · no git commit.

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034m-memory-core-split.md` (mirror `tmp/workers/MUD-034m/PLAN.md`)
- **APPROVED by Astra 2026-08-12 08:53 MST** → fresh IMPL session (do not resume plan session)
- Scope OK: pure-move memory repos + core (7 hosts) · members not extensions · override remove/lower only · `--core` · no features / no 034n
