---
id: MUD-034k
area: engine
title: Split combat surface + pure combat (Wave Q3)
status: scheduled
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: "grok"
worker: "grok"
phase: planning
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034k-combat-surface-split.md
worker_out_dir: tmp/workers/MUD-034k
parent: MUD-034
---

# MUD-034k — Combat surface split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Combat surface · **peak measured tokens:** 3614 · **note:** pure first; parity handlers

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `app/src/main/kotlin/com/jcraw/app/handlers/CombatHandlers.kt` | 3249 | 272 | 3249 |
| `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientCombatHandlers.kt` | 3432 | 284 | 3432 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/combat/AttackResolver.kt` | 3614 | 362 | 3614 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/combat/FleeResolver.kt` | 2794 | 287 | 2794 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/combat/MonsterAIHandler.kt` | 2863 | 297 | 2863 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatNarrator.kt` | 3219 | 309 | 3219 |

## Acceptance
- [ ] Behavior-preserving extract of `CombatHandlers.kt`, `ClientCombatHandlers.kt`, `AttackResolver.kt`, `FleeResolver.kt`, `MonsterAIHandler.kt`, `CombatNarrator.kt` (pure moves / thin public entrypoints; no feature work)
- [ ] Console+GUI **parity** where app/client pairs exist in this family
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [ ] Retarget remaining override `ticket` from `MUD-034` → `MUD-034k` when still needed
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
