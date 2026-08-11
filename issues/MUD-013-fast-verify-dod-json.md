---
id: MUD-013
area: tooling
title: Fast verify integration + compact dod-summary.json
status: done
priority: med
created: 2026-08-09
updated: 2026-08-11
source: jason
labels: [quality-gates, verify, wave-c]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-010, MUD-011, MUD-012]
verify: "./tools/verify_mud.sh"
plan: plans/2026-08-10-ai-mud-MUD-013-fast-verify-dod-json.md
worker_out_dir: tmp/workers/MUD-013
worker_pid: ""
grok_session: ""
codex_session: ""
---

# MUD-013 — Fast verify + DoD JSON

## Problem
Full Gradle logs blow token budgets. Need compact agent feedback and a single fast lane composing compile/tests/static/arch/lock.

## Acceptance
- [x] `verify_mud.sh` fast lane runs: compile + scoped tests + detekt + konsist + test-lock as available
- [x] Emits compact `dod-summary.json` (pass/fail per gate, quarantine count, durations)
- [x] AGENTS.md: never mark done on coverage % alone; attach/summary path in closeout
- [x] N=3 retry then escalate documented (no infinite thrash)

## Source
DIGEST-025 gates #1,#5,#8 + executive rules

## Resolution
Extended `tools/verify_mud.sh` only: per-gate pass/fail/skipped + wall-clock durations; always writes `tmp/dod-summary.json` (or `$MUD_DOD_SUMMARY`); human `== verify_mud ==` kept with `dod_summary:` line. `fast` ≡ `default` (bare tests gate = skipped). Cheap `quarantine_count` via `@Tag("quarantine")` scan. AGENTS DoD + Verification bullets (coverage ≠ done; N=3 escalate; script does not auto-retry). Closeout: `tmp/workers/MUD-013/CLOSEOUT.md`.
