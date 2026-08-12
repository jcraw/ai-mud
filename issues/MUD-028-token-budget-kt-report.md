---
id: MUD-028
area: tooling
title: Kotlin token/structure report-only checker (Wave Q1)
status: done
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, token]
assignee: ""
worker: grok
phase: closed
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-027]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-028-token-budget-kt-report.md
worker_out_dir: tmp/workers/MUD-028
worker_pid: ""
---

# MUD-028 — Token/structure report-only (Kotlin)

## Problem
No enforced token/file ceilings. Need jam-like measurement before hard fail.

## Acceptance
- [x] `tools/quality/check_token_budget_kt.py` (or sibling) — chars/4 token estimate on `src/main/**/*.kt`
- [x] `config/quality/token_budget_kt.json` — file warn 2000/err 2500 tok; fn 200/250; structure secondary thresholds per DESIGN
- [x] Report-only default: exit 0 even with breaches; JSON findings to stdout/`tmp/`
- [x] Lists current god files for override candidates (no hard fail yet)
- [x] `docs/` short usage note + DESIGN cross-link
- [x] Does not break `--core` green
- [x] No mass file splits in this ticket

## Non-goals
- Hard fail (031)
- Touched-only mode (029)
- Verify wire / `MUD_TOKEN_HARD` (030)
- God splits / product `*.kt`

## Closeout
- Smoke: 251 files · 10 modules · 55 `override_candidates` · exit 0 · `tmp/token_budget_kt.json`
- Verify: `./tools/verify_mud.sh --core` PASS · `tmp/dod-summary.json`
- Detail: `tmp/workers/MUD-028/CLOSEOUT.md`
