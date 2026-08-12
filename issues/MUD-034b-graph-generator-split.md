---
id: MUD-034b
area: engine
title: Split GraphGenerator layout/MST (Wave Q3)
status: done
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034b-graph-generator-split.md
worker_out_dir: tmp/workers/MUD-034b
parent: MUD-034
---

# MUD-034b — Graph gen split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Graph gen · **peak measured tokens:** 11932 · **note:** layout/MST extract

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/GraphGenerator.kt` | 11932 | 1058 | 11932 |

## Acceptance
- [x] Behavior-preserving extract of `GraphGenerator.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family (**N/A** — reasoning-only)
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034b` when still needed (**override removed** — host under global E)
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
- session: grok impl fresh 2026-08-12 · plan APPROVED by Astra 03:33 MST
- worker_out_dir: `tmp/workers/MUD-034b`
- closeout: `tmp/workers/MUD-034b/CLOSEOUT.md`

## Resolution
Pure-move split complete. Host **11932 → 561** tokens; GraphGenerator override **removed**. Extracts: GraphLayoutNodes, GraphMst, GraphEdgeDirections + direction geometry/assign/fix/unique fragments, GraphNodeTyping. Public API `GraphGenerator(rng, difficultyLevel)` + `generate` preserved; RNG order preserved. `--core` PASS. No tests edited. No commit.

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034b-graph-generator-split.md` (mirror `tmp/workers/MUD-034b/PLAN.md`)
- Phase: **done** — APPROVED by Astra 2026-08-12 03:33 MST → impl complete
