---
id: MUD-035
area: tooling
title: PIT threshold raise schedule toward 80% (Wave Q4)
status: open
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, pitest]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034]
verify: "./tools/verify_mud.sh --pitest"
plan: ""
worker_out_dir: tmp/workers/MUD-035
worker_pid: ""
---

# MUD-035 — PIT raise toward 80%

## Problem
Soft 60% is day-one honesty; agent-proof tests want ~80% on pure modules (DIGEST-007/025) after structure improves.

## Acceptance
- [ ] Document schedule in docs/PIT.md: 60 soft → 70 soft → 80 hard (opt-in then default on --pitest)
- [ ] Only raise after at least one god-split landed or measurement shows headroom
- [ ] Keep PIT out of fast/core
- [ ] Nightly/CI optional job sketch (may be docs-only if Actions minutes concern)
- [ ] No weakening tests to hit score

## Non-goals
- Mutating app/client UI modules day one
