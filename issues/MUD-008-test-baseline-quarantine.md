---
id: MUD-008
area: testing
title: Re-baseline test suite + quarantine reasoning failures
status: done
priority: high
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [testing, quarantine, modernization, wave-b]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-004]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-10-ai-mud-MUD-008-test-baseline-quarantine.md
worker_out_dir: tmp/workers/MUD-008
grok_session: ""
codex_session: ""
worker_pid: ""
---

**Plan APPROVED by Astra 2026-08-10 20:45 MST** — fresh impl authorized.

# MUD-008 — Test baseline + quarantine

## Problem
On-disk 2025-12 results: ~1490 tests, **~22 `:reasoning` failures**. README pass counts are stale/inconsistent. Blind full green is a lie and burns agents.

## Intent
Re-run suite, record truth, quarantine known reds, update docs. Do **not** fix all reasoning fails here.

## Acceptance
- [x] Fresh test run recorded (date + command + counts)
- [x] `:reasoning` known failures tagged `@Tag("quarantine")` (or project equivalent) with short reason comments
- [x] Default/core verify lanes exclude quarantine
- [x] README / AGENTS pass claims updated to honest numbers + quarantine count
- [x] List of quarantined tests in `docs/` or ticket resolution (path noted)
- [x] No weakening of asserts to force green

## Notes
- DIGEST-025: quarantined count must appear in future DoD JSON
- Repair wave is later (MUD-017; ticket notes had MUD-018 typo for repair)

## Resolution

**Done 2026-08-10 (grok impl).**

- Baseline: `./gradlew :reasoning:test --continue` → **644** tests, **621** pass, **23** fail (`tmp/workers/MUD-008/baseline-reasoning-20260810.log`)
- Core pre-tag: core 462 + perception 56 + memory 321 green (`baseline-core-20260810.log`)
- 23 methods `@Tag("quarantine")` + reason comments; Gradle default `excludeTags("quarantine")`; `-Pmud.quarantineOnly=true` include path
- `tools/verify_mud.sh` core/full include green `:reasoning`; quarantine uses property
- List: **`docs/TEST_QUARANTINE.md`**
- Verify: `./tools/verify_mud.sh --core` **PASS**; `--full` **PASS**; `--quarantine` **FAIL** (22/23 red; 1 flaky pass on re-run — tag retained per baseline)
- Residual: count drift / flakes → re-baseline; root fixes **MUD-017**
