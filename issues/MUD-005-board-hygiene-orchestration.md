---
id: MUD-005
area: tooling
title: Board/template hygiene + short ORCHESTRATION note
status: done
priority: med
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [tooling, board, modernization, wave-a]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: done
depends_on: [MUD-003]
verify: "./tools/verify_mud.sh"
plan: plans/2026-08-10-ai-mud-MUD-005-board-hygiene-orchestration.md
plan_session: ""
grok_session: ""
codex_session: ""
worker_out_dir: tmp/workers/MUD-005
worker_pid: ""
---

# MUD-005 — Board hygiene + short ORCHESTRATION

## Problem
Ticket template missing live frontmatter fields used on MUD-001/002. No in-repo orchestration note for spare-capacity drains.

## Acceptance
- [x] `_templates/ticket.md` includes: `agent_eligible`, `eligibility`, `needs_jason`, `phase`, `plan`, `report`, `depends_on`, `worker_out_dir`, session fields
- [x] `issues/ORCHESTRATION.md` ≤ ~2 screens: serial-per-tree, plan→Astra approve→fresh impl, human_gated ≠ done, spare-capacity drain posture, worker dirs under `tmp/workers/<ID>/`
- [x] BOARD.md “How agents use this” points at ORCHESTRATION + AGENTS
- [x] No game_jam-length novel

## Resolution

- 2026-08-10 ~19:15 AZ: clear-backlog — MUD-004 done; free tree; **Turn 1 PLAN** launched (Grok fresh, `PLAN_BRIEF`).
- Scheduled 2026-08-10 for spare-capacity Wave A (behind MUD-003). Serial one builder — do not start early.
- 2026-08-10: **Plan written** → `plans/2026-08-10-ai-mud-MUD-005-board-hygiene-orchestration.md` + `tmp/workers/MUD-005/PLAN.md`. Status `plan_review`.
- 2026-08-10 ~19:35 AZ: **APPROVED by Astra** (common-sense). Fresh **IMPL** launched (`IMPL_BRIEF`, Grok bypassPermissions via launcher). Do not resume plan session.
- 2026-08-10: **IMPL done** (fresh session). Expanded template frontmatter to live core fields; created short `issues/ORCHESTRATION.md` (78 lines); surgical BOARD “How agents use this” → ORCHESTRATION + AGENTS. `verify` remains `./tools/verify_mud.sh`. Closeout: `tmp/workers/MUD-005/CLOSEOUT.md`.
