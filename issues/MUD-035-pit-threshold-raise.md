---
id: MUD-035
area: tooling
title: PIT threshold raise schedule toward 80% (Wave Q4)
status: done
priority: med
created: 2026-08-11
updated: 2026-08-16
source: jason
labels: [quality-gates, wave-q, pitest]
assignee: "grok"
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034]
verify: "./tools/verify_mud.sh --pitest"
plan: plans/2026-08-16-ai-mud-MUD-035-pit-threshold-raise.md
worker_out_dir: tmp/workers/MUD-035
worker_pid: ""
---

# MUD-035 — PIT raise toward 80%

## Problem
Soft 60% is day-one honesty; agent-proof tests want ~80% on pure modules (DIGEST-007/025) after structure improves.

## Acceptance
- [x] Document schedule in docs/PIT.md: 60 soft → 70 soft → 80 hard (opt-in then default on --pitest)
- [x] Only raise after at least one god-split landed or measurement shows headroom
- [x] Keep PIT out of fast/core
- [x] Nightly/CI optional job sketch (may be docs-only if Actions minutes concern)
- [x] No weakening tests to hit score

## Non-goals
- Mutating app/client UI modules day one

## Plan
- Path: `plans/2026-08-16-ai-mud-MUD-035-pit-threshold-raise.md`
- Phase: **done** — approved plan → fresh IMPL
- Approach: document + plumb rungs; **live stay R0** (60 / 60 / `PITEST_HARD_DEFAULT=0`). Splits landed; remasured min **9.8%** << 72.

## Resolution
Scheduled the 60→70→80 ratchet. Did **not** raise live numbers (headroom not met).

| Deliverable | Path / note |
|-------------|-------------|
| Schedule SoT | `docs/PIT.md` — R0–R2b table, promote checklist, remasure, anti-game, nightly YAML sketch (docs-only) |
| Verify plumbing | `PITEST_SOFT_THRESHOLD=60` · `PITEST_HARD_THRESHOLD=60` · `PITEST_HARD_DEFAULT=0` in `tools/verify_mud.sh` |
| AGENTS | one surgical phrase: soft 60% now; schedule 60→70→80 → `docs/PIT.md` |
| Remeasure | core **25.9** · perception **9.8** (min) · memory **39.9** · min **9.8** |
| Verify | `./tools/verify_mud.sh --pitest` → **PASS** (soft below-60 + note) · `tmp/dod-summary.json` |
| Lanes | default + `--core` → `pitest.status=skipped` |
| Hard opt-in | `MUD_PITEST_HARD=1 ./tools/verify_mud.sh --pitest` → **FAIL** (min 9.8 < 60) |
| Closeout | `tmp/workers/MUD-035/CLOSEOUT.md` |

No `src/test/**`, no test-lock, no mutator/target shrink, no `.github/workflows`, no product `*.kt`, no deploy/push/merge.

**Residual:** still far from 70/80. Follow-on is assertion-strength on **perception first**, then core, then memory — not this ticket.
