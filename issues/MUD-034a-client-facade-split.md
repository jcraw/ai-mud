---
id: MUD-034a
area: client
title: Split EngineGameClient facade (Wave Q3)
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
plan: plans/2026-08-12-ai-mud-MUD-034a-client-facade-split.md
worker_out_dir: tmp/workers/MUD-034a
parent: MUD-034
---

# MUD-034a — Client facade split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Client facade · **peak measured tokens:** 14209 · **note:** top god; UI later

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt` | 14209 | 1072 | 14209 |
| `client/src/main/kotlin/com/jcraw/mud/client/ui/MainGameScreen.kt` | 3033 | 328 | 3033 |

## Acceptance
- [x] Behavior-preserving extract of `EngineGameClient.kt`, `MainGameScreen.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family (MainGameScreen = GUI-only; no app pair; handler contracts unchanged)
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034a` when still needed
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
- session: grok fresh impl 2026-08-12
- brief: plan under `plans/` APPROVED by Astra → fresh impl

## Resolution
Behavior-preserving client facade split complete.

**Hosts (before → after file tokens):**
- `EngineGameClient.kt`: **14209 → 6282** (LOC ~1204 → 474) — residual override lowered, `ticket: MUD-034a`
- `MainGameScreen.kt`: **3033 → 590** (LOC ~344 → 73) — residual override lowered (fn residual for composable root), `ticket: MUD-034a`

**Extracts (all ≤ global file E2500, no overrides):**
- UI: `ui/StatusBar.kt`, `ui/GameLogWindow.kt`, `ui/GameInputField.kt`
- `ClientItemTemplateCache.kt`, `ClientSpaceContent.kt`, `ClientSpaceDescribe.kt`, `ClientFrontierExpansion.kt`
- `ClientQuestDeathSupport.kt`, `ClientNpcCombat.kt`, `ClientNpcAttack.kt`
- Optional `ClientIntentRouter` **not shipped** — `processIntent` stays on facade so residual override covers FN_E (global fn E250 blocks large when-dispatch on Added files)

**Overrides:** lowered only (never raised); no Added-file overrides; ticket retarget `MUD-034` → `MUD-034a`. Tool `TICKET_RE` widened to `^MUD-\d+[a-z]?$` so child tickets validate. Compose detekt: `FunctionNaming.ignoreAnnotated: ['Composable']`.

**Verify:** `./tools/verify_mud.sh --core` PASS · `tmp/dod-summary.json` · remeasure `tmp/workers/MUD-034a/token_remeasure.json` · closeout `tmp/workers/MUD-034a/CLOSEOUT.md`

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034a-client-facade-split.md` (mirror `tmp/workers/MUD-034a/PLAN.md`)
- Phase: **done** — APPROVED by Astra 2026-08-12 02:53 MST → fresh impl session green
