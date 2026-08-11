---
id: MUD-000
area: engine          # tooling | engine | client | docs | chore | spike
title: Short title
status: open          # open | scheduled | in_progress | plan_review | done | blocked | wontfix
priority: med         # low | med | high
created: YYYY-MM-DD
updated: YYYY-MM-DD
source: jason
labels: []
assignee: ""
worker: ""            # grok | codex | claude | cursor | ""
phase: backlog        # backlog | planning | plan_review | impl | done | …
agent_eligible: true
eligibility: agent_eligible   # agent_eligible | human_gated | done
needs_jason: ""       # "" | playtest | opinion | action
depends_on: []
verify: "./tools/verify_mud.sh"
plan: ""
report: ""
plan_session: ""
grok_session: ""
codex_session: ""
worker_out_dir: ""    # tmp/workers/MUD-NNN when active
worker_pid: ""
---

# MUD-000 — Title

## Problem
What is wrong / missing.

## Acceptance
- [ ] Concrete done check

## Notes
Optional frontmatter (add only when needed): `brief`, `impl_session`, `spike`, `handoff_to`, `prior_failure`.

## Builder
- session: _(fill when spawned)_
- brief: `issues/MUD-NNN-task.md` or inline

## Resolution
