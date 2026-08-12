---
id: MUD-027
area: tooling
title: dod-summary v2 + stable finding codes (Wave Q1)
status: done
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, verify]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-026]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-027-dod-summary-v2-findings.md
worker_out_dir: tmp/workers/MUD-027
worker_pid: ""
plan_session: ""
grok_session: ""
---

# MUD-027 — dod-summary v2 + finding codes

## Problem
Agents scrape Gradle logs. Need compact machine DoD with stable finding codes + remediation strings (jam-style).

## Acceptance
- [x] Schema doc or `config/quality/dod_summary.schema.json` (schema_version 2)
- [x] `tmp/dod-summary.json` includes optional `findings[]`: `{code,path,metric,limit,remediation}`
- [x] Existing gates still emit pass/fail/skipped + durations
- [x] Verify validates JSON shape lightly (hard fail on invalid summary when verify ran)
- [x] AGENTS closeout still cites dod-summary path
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Short `docs/` note or extend DESIGN pointer — no novel

## Non-goals
- Token checker implementation (028)
- Changing detekt baseline

## Closeout
See `tmp/workers/MUD-027/CLOSEOUT.md`. Verify: `./tools/verify_mud.sh --core` PASS · `tmp/dod-summary.json` schema_version 2 · findings `[]`.
