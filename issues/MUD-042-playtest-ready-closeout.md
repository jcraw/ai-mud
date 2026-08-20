---
id: MUD-042
area: docs
title: Product-ready bar + closeout (playtest later)
status: open
priority: high
created: 2026-08-19
updated: 2026-08-19
source: jason
labels: [wave-p, product-ready]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-039, MUD-040, MUD-041]
verify: "./tools/verify_mud.sh --core && ./tools/verify_mud.sh --smoke"
plan: ""
worker_out_dir: ""
worker_pid: ""
---

# MUD-042 — Product-ready bar + closeout (playtest later)

## Problem
Harness modernization + Wave Q closed, but posture is still **not product-ready**. Jason wants end-state quality: no static debt, hard PIT 80, zero dup warnings, expanded automated smoke. **Human playtest is not scheduled near-term** (Jason 2026-08-19): finish bringing MUD code fully up to date, then **integration work**, and only after that a real solo playtest.

## Acceptance

### Automated / agent (this ticket)
- [ ] Depends **MUD-039/040/041** done (dup hard 0, PIT hard 80, static debt zero)
- [ ] Expand headless smoke beyond look/take/inv/attack to cover **treasure take→inv→equip**, drop/give basics, and one combat kill or clear failure path (still no live OpenAI)
- [ ] `--smoke` documented; optionally wire smoke into a **play** verify alias or CI nightly — **not** required on every PR `--core` unless cheap (<30s)
- [ ] `KNOWN_ISSUES.md`: open solo-play blockers fixed or explicitly wontfix with reason; treasure GUI checklist automated where cheap
- [ ] Multi-user Attack/Emote stubs: either minimal unstub for solo-irrelevant paths **or** documented “solo console+GUI only” scope (friends multiplayer still later)
- [ ] `docs/MODERNIZATION_STATUS.md` + `AGENTS.md` posture: **product-ready (code bar)** once gates green; board Wave P complete for agent work
- [ ] `./tools/verify_mud.sh --core` and `./tools/verify_mud.sh --smoke` exit 0

### Later (not this wave’s Jason gate)
- Human GUI + console playtest happens **after** integration work (separate phase / future ticket). Do **not** block Wave P agent closeout on a near-term Jason playtest stamp.
- Do not fake “player-done” on verify alone; just do not schedule playtest as the next human action.

## Non-goals
- Friends multiplayer / netcode polish
- Mobile stores
- Content pack / new dungeon campaign
- Near-term Jason playtest appointment

## Notes
Jason 2026-08-19: “no MUD playtest scheduled for the near future… get MUD code completely up to date and then have to do some integration work before we playtest.” Agent work = code/gates/smoke bar. Integration + playtest = later program.
