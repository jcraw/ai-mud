---
id: MUD-009
area: chore
title: Git hygiene — dirty testbot, logs, first clean tooling PR base
status: done
priority: med
created: 2026-08-09
updated: 2026-08-11
source: jason
labels: [chore, git, wave-b]
assignee: jason
worker: astra
phase: done
agent_eligible: false
eligibility: done
needs_jason: ""
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
- [x] Decide: commit, discard, or split dirty testbot diffs
- [x] skill_progression / test-logs: gitignore vs delete vs keep local-only
- [x] Policy: may agents open one hygiene PR with `issues/` + research + AGENTS when those land?
- [x] No secrets in any commit (`local.properties`, keys, dbs)

## Notes
- Closed 2026-08-11 under Jason “finish modernization”: only dirty left was intentional testbot SkillProgression message-format parsers.

## Resolution
- Committed + pushed `74c343e` — testbot parses `Dodge leveled up! N → M`
- No secrets; no log junk on tree
- Standing policy: agents may push allowlisted ops/issues/plans/product per Wave F/G rules; never secrets/testbot drive-by without clear intent
