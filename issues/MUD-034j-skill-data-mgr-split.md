---
id: MUD-034j
area: engine
title: Split skill definitions/manager (Wave Q3)
status: in_progress
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: "grok"
worker: "grok"
phase: implementing
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034j-skill-data-mgr-split.md
worker_out_dir: tmp/workers/MUD-034j
worker_pid: ""
parent: MUD-034
---

# MUD-034j — Skill data/mgr split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Skill data/mgr · **peak measured tokens:** 7304 · **note:** data vs logic

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/PerkDefinitions.kt` | 7304 | 747 | 7304 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillDefinitions.kt` | 4419 | 490 | 4419 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillManager.kt` | 5594 | 510 | 5594 |

## Acceptance
- [ ] Behavior-preserving extract of `PerkDefinitions.kt`, `SkillDefinitions.kt`, `SkillManager.kt` (pure moves / thin public entrypoints; no feature work)
- [ ] Console+GUI **parity** where app/client pairs exist in this family
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [ ] Retarget remaining override `ticket` from `MUD-034` → `MUD-034j` when still needed
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


## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034j-skill-data-mgr-split.md` (mirror `tmp/workers/MUD-034j/PLAN.md`)
- Phase: **implementing** — **APPROVED by Astra 2026-08-12 07:35 MST** → fresh impl session
- Preflight: PLAN tok=1721 clear (W2000/F3500)
- Approach: pure-move skill data/mgr (PerkDefinitions category maps + SkillDefinitions catalogs/StarterSkillSets + SkillManager XP/unlock/check fragments); thin public facades; stable API names; remeasure lower/remove/retarget overrides to MUD-034j only; no app/client handlers; no features; `--core` green
- next: **APPROVED by Astra 2026-08-12 07:35 MST** → fresh IMPL live (do not resume plan session)
