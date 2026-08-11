---
id: MUD-004
area: tooling
title: Add tools/verify_mud.sh + default/full/quarantine lanes
status: done
priority: high
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [tooling, verify, modernization, wave-a]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-003]
verify: "./tools/verify_mud.sh"
plan: plans/2026-08-10-ai-mud-MUD-004-verify-mud-sh.md
plan_session: ""
grok_session: ""
codex_session: ""
worker_out_dir: tmp/workers/MUD-004
worker_pid: ""
---

# MUD-004 — verify_mud.sh

## Problem
No single verify entrypoint. Blind `./gradlew test` will thrash on known `:reasoning` debt. Tickets need a concrete `verify:` command.

## Intent
Thin wrapper script + document lanes. Honest defaults — fast iteration, not full green theater.

## Acceptance
- [x] `tools/verify_mud.sh` exists and is executable
- [x] **default/fast:** touched or `:core:compileKotlin` (+ scoped test when args passed)
- [x] **core:** core + perception + memory tests (exclude quarantine when tags exist)
- [x] **full:** broader check; documents reasoning debt / quarantine
- [x] **quarantine:** optional lane to include known reds
- [x] Exit non-zero on real failure; print short summary
- [x] `AGENTS.md` Verification section updated to point here
- [x] Ticket template / BOARD note: put concrete command in `verify:`

## Notes
- DIGEST-025 lanes are the north star; PIT/Detekt/Konsist come in later tickets — stub hooks OK
- Do not re-baseline the whole suite here (MUD-008)

## Resolution

- 2026-08-10: **IMPL done (Grok fresh session).** Delivered `tools/verify_mud.sh` (lanes default/fast, core, full, quarantine; `--help`, `--dry-run`, summary, stub hooks). AGENTS Verification → script. Template `verify: "./tools/verify_mud.sh"`. README verify pointer. Closeout: `tmp/workers/MUD-004/CLOSEOUT.md`.
- verify: `bash -n` OK; `--help` / `--dry-run` OK; default smoke `./tools/verify_mud.sh` → PASS (`:core:compileKotlin`, exit 0).
- 2026-08-10 ~18:55 AZ: **APPROVED by Astra** (common-sense). Plan stamped. Fresh IMPL launched via `run_detached_builder.sh` (grok, implementing, IMPL_BRIEF). Do not resume plan session.
- 2026-08-10: Turn 1 PLAN complete (Grok). Plan: `plans/2026-08-10-ai-mud-MUD-004-verify-mud-sh.md` (mirror `tmp/workers/MUD-004/PLAN.md`). Status was `plan_review`.
- Drain 2026-08-10: Turn 1 PLAN kicked (clear-backlog). Final GitHub push when agent queue empty.
- Scheduled 2026-08-10 for spare-capacity Wave A (behind MUD-003). Serial one builder — do not start early.
