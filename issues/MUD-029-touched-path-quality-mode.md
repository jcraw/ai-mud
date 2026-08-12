---
id: MUD-029
area: tooling
title: Touched-path mode for token/structure checks (Wave Q1)
status: done
priority: high
created: 2026-08-11
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-028]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-029-touched-path-quality-mode.md
worker_out_dir: tmp/workers/MUD-029
worker_pid: ""
---

# MUD-029 — Touched-path quality mode

## Problem
Full-repo static thrash is useless for agents. Need path-scoped analysis (git diff / explicit file list).

## Acceptance
- [x] Checker accepts `--files` and/or `--git-diff` (vs origin/master or HEAD~1 — document default)
- [x] Scope = prod `.kt` only unless flagged
- [x] JSON findings limited to touched set
- [x] Still report-only (no hard fail) unless env/flag from 030/031
- [x] Documented in tools help + short docs
- [x] `--core` remains green

## Non-goals
- Hard-on-touched default (031)

## Closeout
- Path scope on `tools/quality/check_token_budget_kt.py`: `--files`, `--git-diff`, `--git-base` (default `origin/master`); union when both; full-repo when neither
- Prod `*/src/main/**/*.kt` only; resolve under `--root`; reject escapes; untracked → `--files`
- Empty touch → `files_scanned: 0`, empty findings, exit 0; summary `scope` + optional git meta
- Docs: `docs/TOKEN_BUDGET_KT.md`; `--help` updated
- No verify hard-wire (030) · no hard-on-touched (031) · no product `*.kt`
- Verify: `./tools/verify_mud.sh --core` **PASS** · `tmp/dod-summary.json`
- Detail: `tmp/workers/MUD-029/CLOSEOUT.md`
