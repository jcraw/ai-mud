---
id: MUD-029
area: tooling
title: Touched-path mode for token/structure checks (Wave Q1)
status: open
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-028]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-029
worker_pid: ""
---

# MUD-029 — Touched-path quality mode

## Problem
Full-repo static thrash is useless for agents. Need path-scoped analysis (git diff / explicit file list).

## Acceptance
- [ ] Checker accepts `--files` and/or `--git-diff` (vs origin/master or HEAD~1 — document default)
- [ ] Scope = prod `.kt` only unless flagged
- [ ] JSON findings limited to touched set
- [ ] Still report-only (no hard fail) unless env/flag from 030/031
- [ ] Documented in tools help + short docs
- [ ] `--core` remains green

## Non-goals
- Hard-on-touched default (031)
