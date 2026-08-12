---
id: MUD-030
area: tooling
title: Wire token/structure into verify (pilot flag) (Wave Q1)
status: open
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, verify]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-029]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-030
worker_pid: ""
---

# MUD-030 — Verify wire + pilot hard flag

## Problem
Token checker exists off to the side until verify owns it.

## Acceptance
- [ ] `verify_mud.sh` default/fast/core/full runs token/structure **report-only** (findings in dod-summary v2)
- [ ] `MUD_TOKEN_HARD=1` (or `--token-hard`) fails closed on error-tier findings for scoped paths
- [ ] Pilot documented; default remains soft so drain doesn’t cliff
- [ ] AGENTS Verification one-liner
- [ ] `--core` exit 0 with default soft mode
- [ ] Quarantine lane skips token check (debt-only)

## Non-goals
- Making hard default (031)
- God file splits
