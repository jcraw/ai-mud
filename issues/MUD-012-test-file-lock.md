---
id: MUD-012
area: tooling
title: Test-file lock / allowlist (anti test-gaming)
status: done
priority: high
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [quality-gates, testing, wave-c]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-004]
verify: "./tools/verify_mud.sh"
plan: plans/2026-08-10-ai-mud-MUD-012-test-file-lock.md
worker_out_dir: tmp/workers/MUD-012
grok_session: ""
codex_session: ""
worker_pid: ""
---

# MUD-012 — Test-file lock

## Problem
Agents can “pass” by weakening or rewriting tests. Need hard lock unless ticket explicitly scopes test edits.

## Acceptance
- [x] Mechanism blocks unauthorized `src/test/**` edits (Gradle property, git hook, or verify hash gate — pick simplest durable)
- [x] Escape hatch: `-PallowTestChanges` or ticket scope tag documented
- [x] AGENTS.md DoD: no test edits without explicit authorization
- [x] Fast verify fails closed when lock violated

## Source
DIGEST-025 gate catalog #2 + fallback hash gate

## Resolution

Content-hash manifest gate (plan APPROVED by Astra):

- `tools/test_lock.sh` (`--check` / `--write`; env `MUD_ALLOW_TEST_CHANGES=1` or `ALLOW_TEST_CHANGES=1`)
- Baseline `tools/test-lock/manifest.sha256` (108 tracked `*/src/test/**` files)
- Hard step on verify default/fast/core/full; quarantine skips; `maybe_stub testFileLock` removed
- Docs: `docs/TEST_LOCK.md`; AGENTS DoD + Verification updated
- Verify: `./tools/verify_mud.sh --fast` PASS; intentional dirty CONTENT fail; write refuse without env; quarantine dry-run no lock step
