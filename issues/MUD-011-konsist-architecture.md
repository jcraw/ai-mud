---
id: MUD-011
area: tooling
title: Konsist architecture tests (module/package boundaries)
status: done
priority: high
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [quality-gates, architecture, wave-c]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-004]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-10-ai-mud-MUD-011-konsist-architecture.md
worker_out_dir: tmp/workers/MUD-011
grok_session: ""
codex_session: ""
worker_pid: ""
---

# MUD-011 — Konsist architecture

## Problem
Free cross-module imports increase blast radius for agent edits.

## Acceptance
- [x] Konsist (or equivalent) dependency rules as tests
- [x] Rules match real `com.jcraw.mud.*` layout (sample/research names mapped)
- [x] Core verify lane includes arch tests
- [x] Document how to add a deliberate exception (ticket-scoped)

## Source
DIGEST-025 gate catalog #3

## Resolution
- Pin `konsist = "0.17.3"` (`com.lemonappdev:konsist`) in catalog; `testImplementation` on `:core`
- `ModuleBoundaryTest` encodes declared Gradle edges with real package roots (`com.jcraw.app`, `com.jcraw.sophia.llm`, `com.jcraw.mud.*`); production scope only; **zero residual allowlist** (graph clean)
- `tools/verify_mud.sh`: hard filtered `:core:test --tests 'com.jcraw.mud.architecture.*'` on default/fast/core/full; dropped `maybe_stub konsist`
- Docs: `docs/KONSIST.md` + AGENTS Verification pointer
- Verify: `./tools/verify_mud.sh --core` exit 0
