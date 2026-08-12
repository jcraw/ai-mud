---
id: MUD-038
area: tooling
title: Headless command smoke script (Wave Q4 / product-adjacent)
status: open
priority: low
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, product]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-038
worker_pid: ""
---

# MUD-038 — Headless command smoke

## Problem
Harness green ≠ commands work. Need scripted look/take/inv/attack without GUI (not full friends multiplayer).

## Acceptance
- [ ] Script or testbot path runs fixed command sequence with mocked/fallback LLM
- [ ] Exit 0 on success; compact failure output
- [ ] Optional verify lane or docs-only hook (not blocking core until Jason says)
- [ ] No live OpenAI required

## Non-goals
- Android/device smoke
- Replacing human playtest taste
- Network multiplayer
