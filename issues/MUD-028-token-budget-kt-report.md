---
id: MUD-028
area: tooling
title: Kotlin token/structure report-only checker (Wave Q1)
status: open
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, token]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-027]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-028
worker_pid: ""
---

# MUD-028 — Token/structure report-only (Kotlin)

## Problem
No enforced token/file ceilings. Need jam-like measurement before hard fail.

## Acceptance
- [ ] `tools/quality/check_token_budget_kt.py` (or sibling) — chars/4 token estimate on `src/main/**/*.kt`
- [ ] `config/quality/token_budget_kt.json` — file warn 2000/err 2500 tok; fn 200/250; structure secondary thresholds per DESIGN
- [ ] Report-only default: exit 0 even with breaches; JSON findings to stdout/`tmp/`
- [ ] Lists current god files for override candidates (no hard fail yet)
- [ ] `docs/` short usage note + DESIGN cross-link
- [ ] Does not break `--core` green
- [ ] No mass file splits in this ticket

## Non-goals
- Hard fail (031)
- Touched-only mode (029)
