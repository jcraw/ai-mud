---
id: MUD-003
area: tooling
title: Add lean AGENTS.md (neutral agent ops + DoD + secrets)
status: done
priority: high
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [tooling, agents, modernization, wave-a]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-001]
verify: ""
plan: plans/2026-08-09-ai-mud-MUD-003-lean-agents-md.md
plan_session: ""
grok_session: ""
codex_session: ""
worker_out_dir: tmp/workers/MUD-003
worker_pid: ""
---

# MUD-003 — Lean AGENTS.md

## Problem
AI MUD only has Claude-era docs (`CLAUDE.md` ~25k, `CODEX.md`). Builders need a neutral, short ops contract so spare-capacity drains work without babysitting.

## Intent
Add dustcrawl-lean **AGENTS.md** (~100–200 lines). Point at CLAUDE.md as optional deep status only — never mandatory full-read.

## Acceptance
- [x] `AGENTS.md` at repo root with: overview, startup reads, stack, workflow (plan→approve→impl), DoD, verification placeholder, secrets/protected paths, agent parity, serial-one-builder
- [x] Explicit: do not full-read CLAUDE.md by default
- [x] Secrets: never commit `local.properties`, keys, `*.db`, secret-bearing logs
- [x] Spike outline in MUD-001 report §Appendix A is the starting shape (adapt, don’t paste blindly)
- [x] No product code changes

## Notes
- Jason posture: background / low-stakes / spare agent capacity only; autonomous drains preferred
- Follow-up: MUD-004 wires real verify command into this file

## Resolution

**Done** (2026-08-10, grok fresh impl session).

- Created root `AGENTS.md` (~130 lines) per approved plan §3
- Plan: `plans/2026-08-09-ai-mud-MUD-003-lean-agents-md.md` (APPROVED by Astra 2026-08-10 17:15 MST)
- Worker notes: `tmp/workers/MUD-003/`
- Verify field left empty until MUD-004 (`tools/verify_mud.sh`)
- No product code (`*.kt` / `*.kts`) changes

Status: APPROVED by Astra 2026-08-10 17:15 MST

- Scheduled Wave A with MUD-004/005; Turn 2 IMPL completed 2026-08-10.
