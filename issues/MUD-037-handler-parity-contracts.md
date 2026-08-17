---
id: MUD-037
area: engine
title: Handler parity contract pack (inventory/combat/social) (Wave Q4)
status: done
priority: med
created: 2026-08-11
updated: 2026-08-16
source: jason
labels: [quality-gates, wave-q, contracts]
assignee: ""
worker: ""
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-16-ai-mud-MUD-037-handler-parity-contracts.md
worker_out_dir: tmp/workers/MUD-037
worker_pid: ""
---

# MUD-037 — Handler parity contracts

## Problem
Strong proof that console, GUI, and GameServer apply the same pure success paths (extend MUD-019/023/024 pattern).

## Acceptance
- [x] Shared apply (or contract tests) covering take/drop/equip/use + one combat + one social intent across surfaces
- [x] Failures are assertion-strong (state deltas), not “message contains ok”
- [x] Test-lock updated if new tests (authorized by this ticket)
- [x] `--core` exit 0
- [x] No GUI redesign

## Non-goals
- Full multiplayer net layer
- Headless full playthrough (038)
