---
id: MUD-034d
area: engine
title: Split MudGameEngine/GameServer runtime (Wave Q3)
status: done
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: "grok"
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034d-app-runtime-split.md
worker_out_dir: tmp/workers/MUD-034d
parent: MUD-034
---

# MUD-034d — App runtime split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** App runtime · **peak measured tokens:** 10257 · **note:** careful multi-user

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `app/src/main/kotlin/com/jcraw/app/MudGameEngine.kt` | 10257 | 787 | 10257 |
| `app/src/main/kotlin/com/jcraw/app/GameServer.kt` | 10127 | 812 | 10127 |
| `app/src/main/kotlin/com/jcraw/app/MultiUserGame.kt` | 2730 | 233 | 2730 |

## Acceptance
- [x] Behavior-preserving extract of `MudGameEngine.kt`, `GameServer.kt`, `MultiUserGame.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family (no client edits; MudGame + handlers/* contracts kept)
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034d` when still needed
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
- session: fresh impl 2026-08-12 (clear-backlog Turn 2)
- brief: `tmp/workers/MUD-034d/IMPL_BRIEF.md`
- closeout: `tmp/workers/MUD-034d/CLOSEOUT.md`

## Resolution
- Plan **APPROVED by Astra 2026-08-12 04:26 MST** — implemented pure-move extract.
- **done** — `--core` PASS; MultiUserGame override removed; residual MudGameEngine/GameServer retargeted `MUD-034d` lower-only.
