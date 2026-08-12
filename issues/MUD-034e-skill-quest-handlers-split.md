---
id: MUD-034e
area: engine
title: Split skill/quest handlers parity (Wave Q3)
status: in_progress
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: ""
worker: grok
phase: implementing
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034e-skill-quest-handlers-split.md
worker_out_dir: tmp/workers/MUD-034e
worker_pid: ""
parent: MUD-034
---

# MUD-034e — Skill/quest handlers split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Skill/quest handlers · **peak measured tokens:** 9827 · **note:** parity

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `app/src/main/kotlin/com/jcraw/app/handlers/SkillQuestHandlers.kt` | 9827 | 841 | 9827 |
| `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientSkillQuestHandlers.kt` | 9517 | 742 | 9517 |

## Acceptance
- [ ] Behavior-preserving extract of `SkillQuestHandlers.kt`, `ClientSkillQuestHandlers.kt` (pure moves / thin public entrypoints; no feature work)
- [ ] Console+GUI **parity** where app/client pairs exist in this family
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [ ] Retarget remaining override `ticket` from `MUD-034` → `MUD-034e` when still needed
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
- session: planning live 2026-08-12 ~04:48 MST (clear-backlog Turn 1)
- brief: `tmp/workers/MUD-034e/PLAN_BRIEF.md`
- plan target: `plans/2026-08-12-ai-mud-MUD-034e-skill-quest-handlers-split.md`


## Resolution

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034e-skill-quest-handlers-split.md` (mirror `tmp/workers/MUD-034e/PLAN.md`)
- Phase: **implementing** — **APPROVED by Astra 2026-08-12 04:54 MST** → fresh impl session
- Preflight: PLAN tok=1974 clear (W2000/F3500)
- Approach: pure-move skill/quest handler parity clusters (Meta/Quest/Train/SkillUse/Craft/Interact(+frag)/app Check); thin facades; lockstep app↔client; remeasure lower/remove/retarget overrides to MUD-034e only; no craft wiring; no features; `--core` green
