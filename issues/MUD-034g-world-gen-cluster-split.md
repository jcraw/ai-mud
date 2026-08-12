---
id: MUD-034g
area: engine
title: Split world gen cluster (Wave Q3)
status: open
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: ""
worker_pid: "1170293"
parent: MUD-034
---

# MUD-034g — World gen cluster split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** World gen cluster · **peak measured tokens:** 7653 · **note:** subpackages

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/WorldGenerator.kt` | 7653 | 664 | 7653 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/DungeonInitializer.kt` | 6327 | 529 | 6327 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/TownGenerator.kt` | 3906 | 361 | 3906 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/ExitLinker.kt` | 3316 | 297 | 3316 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/ExitResolver.kt` | 2597 | 256 | 2597 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/MobSpawner.kt` | 3016 | 288 | 3016 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/HiddenExitPlacer.kt` | 2894 | 240 | 2894 |

## Acceptance
- [ ] Behavior-preserving extract of `WorldGenerator.kt`, `DungeonInitializer.kt`, `TownGenerator.kt`, `ExitLinker.kt`, `ExitResolver.kt`, `MobSpawner.kt`, `HiddenExitPlacer.kt` (pure moves / thin public entrypoints; no feature work)
- [ ] Console+GUI **parity** where app/client pairs exist in this family
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [ ] Retarget remaining override `ticket` from `MUD-034` → `MUD-034g` when still needed
- [ ] New extracted `.kt` files meet global E (no override grandfather)
- [ ] No unauthorized `src/test/**` edits unless explicitly scoped + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`

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
- session: _(fill when spawned)_
- brief: plan under `plans/` if substantial → Astra/Jason APPROVED → fresh impl

## Resolution
