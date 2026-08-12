---
id: MUD-032
area: tooling
title: Gate — no live LLM/network in unit tests (Wave Q2)
status: open
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, tests]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-032
worker_pid: ""
---

# MUD-032 — No live LLM in unit tests

## Problem
Live OpenAI in unit tests = cost, flake, non-deterministic DoD (DIGEST-025).

## Acceptance
- [ ] Static or test gate fails if unit tests under `*/src/test/**` call real OpenAI / network LLM clients
- [ ] Mocks / frozen fixtures only; document allowlist if integration tests need network (testbot excluded or separate lane)
- [ ] Wired into core verify
- [ ] `--core` exit 0
- [ ] Short docs note

## Non-goals
- Rewriting testbot
- Product play smoke
