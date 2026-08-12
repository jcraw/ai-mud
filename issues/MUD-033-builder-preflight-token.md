---
id: MUD-033
area: tooling
title: Builder plan/brief token preflight (Wave Q2)
status: open
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, process]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-030]
verify: "./tools/verify_mud.sh"
plan: ""
worker_out_dir: tmp/workers/MUD-033
worker_pid: ""
---

# MUD-033 — Plan/brief token preflight

## Problem
Fat plans burn builder context (jam GJ-026 lesson). Need preflight before approve/impl.

## Acceptance
- [ ] Tool checks `plans/*.md` and `tmp/workers/*/PLAN*.md` + briefs against DESIGN budgets (plan 2k/3.5k, brief 1.2k/2k tok)
- [ ] Exit codes: 0 clear, 1 warn-only mode, 2 hard fail (document)
- [ ] ORCHESTRATION or AGENTS one-liner: run before plan approve when practical
- [ ] No product code changes
- [ ] Optional wire as `verify_mud.sh --preflight <path>`

## Non-goals
- Changing orchestration launcher behavior across all repos
