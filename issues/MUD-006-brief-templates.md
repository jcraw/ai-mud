---
id: MUD-006
area: tooling
title: Plan/implement brief templates (token-budget aware)
status: done
priority: med
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [tooling, briefs, modernization, wave-a]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-005]
verify: "docs-only"
plan: "plans/2026-08-10-ai-mud-MUD-006-brief-templates.md"
worker_out_dir: tmp/workers/MUD-006
plan_session: ""
grok_session: ""
codex_session: ""
worker_pid: ""
---

# MUD-006 — Brief templates

## Problem
MUD-001 used portfolio/tmp briefs ad hoc. Need in-repo plan + impl templates so spare drains are consistent and ≤~6k mandatory read packs.

## Acceptance
- [x] `issues/_templates/plan-brief.md` and `implement-brief.md` (or equivalent)
- [x] Mandatory read list capped; CLAUDE.md not in default pack
- [x] Worker out dir convention documented: `tmp/workers/<ID>/`
- [x] ORCHESTRATION or AGENTS points at templates

## Resolution
- Created `issues/_templates/plan-brief.md` (PLAN ONLY scaffold, lean Read/NEVER, dual plan paths, after-plan bookkeeping)
- Created `issues/_templates/implement-brief.md` (fresh IMPL after APPROVED; verify + closeout)
- ORCHESTRATION: doc table + Plan→approve→fresh impl bullets; removed MUD-006 out-of-scope stub
- AGENTS: one Workflow bullet pointing at both templates
- Closeout: `tmp/workers/MUD-006/CLOSEOUT.md`
