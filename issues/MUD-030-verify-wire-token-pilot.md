---
id: MUD-030
area: tooling
title: Wire token/structure into verify (pilot flag) (Wave Q1)
status: done
priority: high
created: 2026-08-11
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, verify]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-029]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-030-verify-wire-token-pilot.md
plan_session: 019ff4e4-83e2-7f41-ba97-c2a23a1f196d
impl_session: 019ff4f3-55ae-79c1-80eb-35a7d1d3d78a
worker_out_dir: tmp/workers/MUD-030
worker_pid: ""
approved_by: Astra
approved_at: 2026-08-12 00:48 MST
---

# MUD-030 — Verify wire + pilot hard flag

## Problem
Token checker exists off to the side until verify owns it.

## Acceptance
- [x] `verify_mud.sh` default/fast/core/full runs token/structure **report-only** (findings in dod-summary v2)
- [x] `MUD_TOKEN_HARD=1` (or `--token-hard`) fails closed on error-tier findings for scoped paths
- [x] Pilot documented; default remains soft so drain doesn’t cliff
- [x] AGENTS Verification one-liner
- [x] `--core` exit 0 with default soft mode
- [x] Quarantine lane skips token check (debt-only)

## Non-goals
- Making hard default (031)
- God file splits
