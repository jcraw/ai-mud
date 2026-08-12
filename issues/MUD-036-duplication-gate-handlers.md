---
id: MUD-036
area: tooling
title: Duplication gate on app/client handlers (Wave Q4)
status: open
priority: low
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-036
worker_pid: ""
---

# MUD-036 — Handler duplication gate

## Problem
Console/GUI/GameServer copy-paste drifts (inventory lesson). Jam has duplication heuristics.

## Acceptance
- [ ] `tools/quality/check_duplication_kt.py` (or equiv) on `app/**/handlers` + `client/**/handlers`
- [ ] Start warn-only in verify; document path to hard
- [ ] Findings JSON codes
- [ ] `--core` green default

## Non-goals
- Forced merge of all handlers in one ticket (parity contracts = 037)
