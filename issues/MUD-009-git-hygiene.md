---
id: MUD-009
area: chore
title: Git hygiene — dirty testbot, logs, first clean tooling PR base
status: open
priority: med
created: 2026-08-09
updated: 2026-08-09
source: jason
labels: [chore, git, wave-b]
assignee: jason
worker: ""
phase: backlog
agent_eligible: false
eligibility: human_gated
needs_jason: action
depends_on: []
verify: ""
plan: ""
grok_session: ""
codex_session: ""
---

# MUD-009 — Git hygiene

## Problem
Branch ahead of origin by 1; dirty `testbot` tracked files; untracked skill_progression logs / issues / plans. Unclear commit intent — agents must not guess secrets or drive-by commit junk.

## Acceptance (Jason-led; agent may prepare diff summary only)
- [ ] Decide: commit, discard, or split dirty testbot diffs
- [ ] skill_progression / test-logs: gitignore vs delete vs keep local-only
- [ ] Policy: may agents open one hygiene PR with `issues/` + research + AGENTS when those land?
- [ ] No secrets in any commit (`local.properties`, keys, dbs)

## Notes
- Agent_eligible false until Jason answers; can re-open slices as agent work after decisions

## Resolution
