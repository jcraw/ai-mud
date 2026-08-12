---
id: MUD-031
area: tooling
title: Token/structure hard-on-touched default (Wave Q2)
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
depends_on: [MUD-030]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-031
worker_pid: ""
---

# MUD-031 — Hard-on-touched default

## Problem
Jason accepted: new/touched prod code must meet token/structure ceilings before features expand debt.

## Acceptance
- [ ] Default/fast/core: hard-fail error-tier token/structure on **touched** prod `.kt` (git diff scope)
- [ ] `config/quality/token_budget_kt.json` overrides for known gods only — each override cites burn-down ticket id
- [ ] New files cannot use overrides
- [ ] Override caps may only lower over time (doc rule in AGENTS/DESIGN)
- [ ] Full-repo report still available; not hard without flag
- [ ] AGENTS + DESIGN updated
- [ ] `--core` green on clean tree (overrides cover current gods if needed)

## Non-goals
- Splitting god files (Wave Q3 tickets)
- PIT 80% hard
