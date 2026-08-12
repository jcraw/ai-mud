---
id: MUD-034
area: chore
title: God-file split program umbrella (Wave Q3)
status: done
priority: med
created: 2026-08-11
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor]
assignee: "grok"
worker: grok
phase: closeout
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034-god-file-split-program.md
worker_out_dir: tmp/workers/MUD-034
worker_pid: ""
---

# MUD-034 — God-file split program (umbrella)

## Problem
Token hard-on-touched needs burn-down of oversized hosts (GraphGenerator, EngineGameClient, SkillQuestHandlers, GameServer, IntentRecognizer, …).

## Acceptance
- [x] From MUD-028 report: ranked list of prod files over error token/LOC ceilings
- [x] File **one child ticket per split family** (MUD-034a style or next free ids) — do not split all in one PR
- [x] Each child: behavior-preserving extract; tests green; remove/reduce override for that file
- [x] This umbrella may close when child tickets filed + first split done OR when list + tickets only (prefer list+tickets if huge)
- [x] `--core` green after any code touch
- [x] No drive-by features

## Non-goals
- Full baseline detekt burn-down
- PIT hard 80% (035)

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034-god-file-split-program.md` (mirror `tmp/workers/MUD-034/PLAN.md`)
- Phase: **done** — tickets-only close after **APPROVED by Astra 2026-08-12 02:33 MST**
- Approach: full override remeasure → `RANKED_GODS.md` → 14 family children `034a`–`n` · no product god extract

## Resolution
Wave Q3 umbrella shipped **tickets-only** (no product `*.kt` extract):

- **Ranked list:** `tmp/workers/MUD-034/RANKED_GODS.md` — all **55** override hosts ranked by measured `file_tokens`; full scan JSON `tmp/workers/MUD-034/token_budget_full.json`
- **Children filed (14):** `MUD-034a`…`034n` — one split family each; acceptance = behavior-preserving extract + green + lower/remove override (never raise); `depends_on: [MUD-034, MUD-031]`; `verify: ./tools/verify_mud.sh --core`
  - a Client facade · b Graph gen · c Intent · d App runtime · e Skill/quest handlers (parity) · f Testbot · g World gen cluster · h Item handlers (parity) · i Movement handlers (parity) · j Skill data/mgr · k Combat surface · l Social/trade/treasure · m Memory+core · n Misc reasoning
- **No first split** (plan default skip) · **no override raise/edit** · letter IDs only (035–038 left for Q4)
- **Docs:** BOARD Q3 lists children; optional pointer in `docs/TOKEN_BUDGET_KT.md`
- **Verify:** `./tools/verify_mud.sh --core` — cite CLOSEOUT / `tmp/dod-summary.json`
- Closeout: `tmp/workers/MUD-034/CLOSEOUT.md`
