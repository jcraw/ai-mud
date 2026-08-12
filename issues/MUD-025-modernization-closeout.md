---


id: MUD-025
area: docs
title: Modernization program closeout (quarantine 0 + board)
status: done
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [docs, chore, wave-g]
assignee: ""
worker: grok
phase: done
agent_eligible: false
eligibility: done
needs_jason: ""
depends_on: [MUD-022, MUD-023, MUD-024]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-025-modernization-closeout.md
worker_out_dir: tmp/workers/MUD-025
worker_pid: ""
grok_session: ""
codex_session: ""
plan_session: ""
impl_session: 019ff38f-402b-74c1-bc3e-ade15868d546
---

# MUD-025

**Status: APPROVED by Astra 2026-08-11 18:21 MST**
 — Modernization program closeout

## Problem
After Wave G product/debt tickets, need a single closeout: quarantine **0**, board/handoff truth, AGENTS/TESTING status lines, PUSHED note, optional slim “modernization done” pointer so spare-capacity drains don’t re-open finished program.

## Acceptance
- [x] `docs/TEST_QUARANTINE.md` reflects **0** quarantined tests (post-022)
- [x] `docs/TESTING.md` Current Test Status matches reality (no stale 23 fails)
- [x] `issues/BOARD.md` Wave G complete; Open backlog only human_gated (MUD-007 playtest if still open)
- [x] `issues/OVERNIGHT_HANDOFF.md` Live=none; program complete note
- [x] Short `docs/research/` or `docs/MODERNIZATION_STATUS.md` one-pager: gates on, residual human-only
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] No product redesign

## Non-goals
- PIT hard threshold (scores still day-one soft — out of scope)
- Detekt baseline burn-down
- MUD-007 playtest (Jason)
- git push (Astra)

## Notes
Docs-first ticket; tiny board surgery only.

## Closeout
See `tmp/workers/MUD-025/CLOSEOUT.md`. Push + `PUSHED.md` row = Astra.
