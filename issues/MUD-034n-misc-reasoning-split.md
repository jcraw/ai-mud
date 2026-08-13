---
id: MUD-034n
area: engine
title: Split misc reasoning leftovers (Wave Q3)
status: done
priority: med
created: 2026-08-12
updated: 2026-08-13
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: "grok"
worker: "grok"
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034n-misc-reasoning-split.md
worker_out_dir: tmp/workers/MUD-034n
worker_pid: ""
parent: MUD-034
---

# MUD-034n — Misc reasoning split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Misc reasoning · **peak measured tokens:** 3667 · **note:** split further if fat; kept as one family (plan trim-if-fat: 7 hosts, cohesive reasoning leftovers)

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/DispositionManager.kt` | 3667 | 346 | 3667 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCKnowledgeManager.kt` | 3028 | 306 | 3028 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/pickpocket/PickpocketHandler.kt` | 3572 | 312 | 3572 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/NPCGenerator.kt` | 3372 | 328 | 3372 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/QuestGenerator.kt` | 2975 | 288 | 2975 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/town/TownMerchantTemplates.kt` | 2523 | 297 | 2523 |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomDescriptionGenerator.kt` | 2572 | 211 | 2572 |

## Acceptance
- [x] Behavior-preserving extract of `DispositionManager.kt`, `NPCKnowledgeManager.kt`, `PickpocketHandler.kt`, `NPCGenerator.kt`, `QuestGenerator.kt`, `TownMerchantTemplates.kt`, `TreasureRoomDescriptionGenerator.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family _(N/A — reasoning leftovers; no app/client pairs)_
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034n` when still needed _(N/A — all 7 host overrides removed)_
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
- session: plan 2026-08-12 (grok) APPROVED Astra 09:22 MST; IMPL then FINISH 2026-08-13
- brief: plan under `plans/` if substantial → Astra/Jason APPROVED → fresh impl

## Resolution
- Done 2026-08-13 by grok (FINISH after supervisor-killed IMPL; Astra APPROVED plan 2026-08-12).
- Pure-move misc reasoning: Disposition* · NPCKnowledge* · Pickpocket* · NPCGenerator* · Quest*Gen · TownMerchant* · TreasureRoom*+Complete · thin hosts.
- Hosts: **3667→1575** / **3028→1241** / **3572→1225** / **3372→421** / **2975→491** / **2523→711** / **2572→1097**.
- All 7 host overrides **removed** (under global E; never raised; no Added override).
- Finish split: `completeRoomDescription` 252 → `TreasureRoomComplete` (`complete` 202). Parity N/A.
- `./tools/verify_mud.sh --core` PASS · dod-summary `tmp/dod-summary.json` · closeout `tmp/workers/MUD-034n/CLOSEOUT.md`.
- No `src/test/**` · no features · no 035–038 · no git commit.

