---
id: MUD-026
area: docs
title: Agent quality gates design lock (Wave Q0)
status: done
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, docs]
assignee: ""
worker: astra
phase: done
agent_eligible: false
eligibility: done
needs_jason: ""
depends_on: []
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-026
worker_pid: ""
grok_session: ""
codex_session: ""
---

# MUD-026 — Agent quality gates design lock (Q0)

## Problem
Need board + AGENTS pointers so builders treat `docs/AGENT_QUALITY_GATES_DESIGN.md` as accepted policy (Jason 2026-08-11), not a draft to re-litigate.

## Acceptance
- [x] Design doc status = accepted; decisions section closed (token ceilings, hard-on-touched Q2, PIT 80% after splits, E-tier after Q2)
- [x] `issues/BOARD.md` Wave Q drain order lists Q0→Q4 tickets
- [x] `AGENTS.md` Verification: one short pointer to design doc + Wave Q (no novel-length paste)
- [x] `issues/OVERNIGHT_HANDOFF.md` notes Wave Q backlog (Live still none unless drain kicked)
- [x] No product/code refactors in this ticket
- [x] `./tools/verify_mud.sh --core` exit 0

## Resolution
Astra DIY docs lock 2026-08-11. Design accepted block + decisions closed. BOARD Wave Q MUD-026…038. AGENTS pointer. OVERNIGHT queue note. Verify core PASS.

## Non-goals
- Implementing token checkers (MUD-028+)
- Detekt baseline burn-down
- Raising PIT threshold

## Notes
Design body already at `docs/AGENT_QUALITY_GATES_DESIGN.md`. This ticket is pointer/board truth only if not already stamped.
