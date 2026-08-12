---
id: MUD-034f
area: tooling
title: Split testbot god files (Wave Q3)
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
plan: "plans/2026-08-12-ai-mud-MUD-034f-testbot-god-split.md"
worker_out_dir: "tmp/workers/MUD-034f"
parent: MUD-034
---

# MUD-034f — Testbot split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Testbot · **peak measured tokens:** 9131 · **note:** lower product risk

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `testbot/src/main/kotlin/com/jcraw/mud/testbot/InputGenerator.kt` | 9131 | 592 | 9131 |
| `testbot/src/main/kotlin/com/jcraw/mud/testbot/validation/CodeValidationRules.kt` | 7761 | 616 | 7761 |
| `testbot/src/main/kotlin/com/jcraw/mud/testbot/TestBotRunner.kt` | 4420 | 365 | 4420 |
| `testbot/src/main/kotlin/com/jcraw/mud/testbot/V3TestGameEngine.kt` | 3616 | 308 | 3616 |
| `testbot/src/main/kotlin/com/jcraw/mud/testbot/TestModels.kt` | 2636 | 266 | 2636 |
| `testbot/src/main/kotlin/com/jcraw/mud/testbot/validation/ValidationPrompts.kt` | 5412 | 414 | 5412 |

## Acceptance
- [ ] Behavior-preserving extract of `InputGenerator.kt`, `CodeValidationRules.kt`, `TestBotRunner.kt`, `V3TestGameEngine.kt`, `TestModels.kt`, `ValidationPrompts.kt` (pure moves / thin public entrypoints; no feature work)
- [ ] Console+GUI **parity** where app/client pairs exist in this family
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [ ] Retarget remaining override `ticket` from `MUD-034` → `MUD-034f` when still needed
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
