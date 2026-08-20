---
id: MUD-040
area: tooling
title: PIT pure-module strength → hard 80% default
status: open
priority: high
created: 2026-08-19
updated: 2026-08-19
source: jason
labels: [quality-gates, wave-p, debt, pit]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-035]
verify: "./tools/verify_mud.sh --pitest"
plan: ""
worker_out_dir: ""
worker_pid: ""
---

# MUD-040 — PIT pure-module strength → hard 80% default

## Problem
PIT is live but **soft R0**: min mutation score last measured **~9.8%** (<< 72). Target end-state is **hard 80% default** on `--pitest` (docs/PIT.md R2b) so pure modules have real assertion strength.

## Acceptance
- [ ] Strengthen tests/asserts on `:core` / `:perception` / `:memory` only (STRONGER mutators unchanged unless Jason-approved)
- [ ] Remeasured **min** mutation score ≥ **82** (2pp buffer over 80) via `./tools/verify_mud.sh --pitest`
- [ ] Flip schedule to **R2b**: `PITEST_HARD_THRESHOLD=80`, `PITEST_HARD_DEFAULT=1` (hard 80 default on `--pitest`)
- [ ] Soft/hard docs + AGENTS match live constants; promote checklist in `docs/PIT.md` marked done
- [ ] Still **never** put PIT on default/fast/core; full stays skip while wall-clock is long
- [ ] Test-lock updated if tests added (`MUD_ALLOW_TEST_CHANGES=1` + commit manifest)
- [ ] `./tools/verify_mud.sh --core` still exit 0; `--pitest` exit 0 under hard 80
- [ ] No app/client product redesign

## Non-goals
- Mutating app/client/reasoning/llm
- Coverage-% theater without killing mutants
- Putting PIT on PR `--core` CI

## Notes
Ratchet may land as one impl after headroom exists; intermediate soft 70 is optional if builder prefers single jump once min≥82. Prefer contract/property tests on pure logic over brittle string snapshots.
