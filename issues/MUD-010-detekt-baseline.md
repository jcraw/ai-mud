---
id: MUD-010
area: tooling
title: Detekt + baseline (new-code hard, legacy soft)
status: done
priority: high
created: 2026-08-09
updated: 2026-08-10
source: jason
labels: [quality-gates, static, wave-c]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-004, MUD-008]
verify: "./tools/verify_mud.sh"
plan: plans/2026-08-10-ai-mud-MUD-010-detekt-baseline.md
worker_out_dir: tmp/workers/MUD-010
grok_session: ""
codex_session: ""
worker_pid: ""
---

# MUD-010 — Detekt + baseline

## Problem
No static smell gate. Agents can add new debt silently while legacy noise blocks “fix everything.”

## Acceptance
- [x] Detekt wired in Gradle
- [x] Baseline for existing issues (soft on legacy)
- [x] New code hard-fails on configured rules
- [x] Fast verify lane runs detekt (or detekt on touched modules)
- [x] Document how to regenerate baseline (Jason/explicit only for mass accepts)

## Source
DIGEST-025 gate catalog #4

## Resolution
Plan APPROVED by Astra 2026-08-10 ~21:15 AZ. Fresh IMPL session (not resume plan).

Plan: `plans/2026-08-10-ai-mud-MUD-010-detekt-baseline.md`  
Worker mirror: `tmp/workers/MUD-010/PLAN.md`  
IMPL brief: `tmp/workers/MUD-010/IMPL_BRIEF.md`

### Impl closeout (2026-08-10)
- **Pin:** detekt **1.23.8** (stable; works with KGP 2.2.0 — no alpha fallback needed)
- **Paths:** `gradle/libs.versions.toml`, `buildSrc/build.gradle.kts`, `buildSrc/.../kotlin-jvm.gradle.kts`, `config/detekt/detekt.yml`, `config/detekt/baseline.xml` (1478 legacy IDs), `tools/verify_mud.sh`, `docs/DETEKT.md`, `AGENTS.md` (Verification line)
- **Verify:** `./gradlew detekt` green; `./tools/verify_mud.sh` / `--fast` run hard detekt (no stub); dry-run prints `./gradlew detekt`; temp MagicNumber smell fails exit 1 then green after remove
- **Client/CMP:** detekt applied; no exclude needed
- **Closeout note:** `tmp/workers/MUD-010/CLOSEOUT.md`
