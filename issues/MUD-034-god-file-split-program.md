---
id: MUD-034
area: chore
title: God-file split program umbrella (Wave Q3)
status: open
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [quality-gates, wave-q, refactor]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-034
worker_pid: ""
---

# MUD-034 — God-file split program (umbrella)

## Problem
Token hard-on-touched needs burn-down of oversized hosts (GraphGenerator, EngineGameClient, SkillQuestHandlers, GameServer, IntentRecognizer, …).

## Acceptance
- [ ] From MUD-028 report: ranked list of prod files over error token/LOC ceilings
- [ ] File **one child ticket per split family** (MUD-034a style or next free ids) — do not split all in one PR
- [ ] Each child: behavior-preserving extract; tests green; remove/reduce override for that file
- [ ] This umbrella may close when child tickets filed + first split done OR when list + tickets only (prefer list+tickets if huge)
- [ ] `--core` green after any code touch
- [ ] No drive-by features

## Non-goals
- Full baseline detekt burn-down
- PIT hard 80% (035)
