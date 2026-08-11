---
id: MUD-016
area: tooling
title: CI workflow — compile + scoped tests on PR
status: done
priority: med
created: 2026-08-09
updated: 2026-08-11
source: jason
labels: [ci, tooling, wave-e]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-004, MUD-008]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-016-ci-scoped-tests.md
worker_out_dir: tmp/workers/MUD-016
worker_pid: ""
grok_session: ""
codex_session: ""
needs_jason: null
approved_by: Astra
approved_at: "2026-08-11 02:00 MST"
---
## Astra approve (2026-08-11 02:00 MST)
Common-sense approve: GHA verify.yml with `--core` only; no secrets; quarantine excluded; java.home portability fix. Fresh impl session next (plan file handoff). In-tree only — no push required this turn.


# MUD-016 — CI

## Problem
No known CI. Remote fans/PRs and agent commits lack a shared green signal.

## Acceptance
- [x] GitHub Actions (or existing host CI) runs compile + core/fast verify on PR/push
- [x] No secret leakage; no live OpenAI in CI unit path
- [x] Quarantine excluded from required check
- [x] Badge or README note optional

## Notes
- Needs remote push access / Jason OK if first workflow file

## Resolution
- Created `.github/workflows/verify.yml` (job `core` → `./tools/verify_mud.sh --core` on PR/push `master`/`main` + `workflow_dispatch`)
- Stripped machine-only `org.gradle.java.home` from committed `gradle.properties` (locals: `~/.gradle/gradle.properties`)
- Optional README 1-line CI blurb under Development → Verify
- Local verify: `./tools/verify_mud.sh --core` exit 0; secrets grep empty
- Closeout: `tmp/workers/MUD-016/CLOSEOUT.md`
- **Residual:** first Actions run needs Jason push; branch protection later
