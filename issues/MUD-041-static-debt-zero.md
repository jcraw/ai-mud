---
id: MUD-041
area: chore
title: Static debt zero (detekt baseline + token overrides)
status: open
priority: high
created: 2026-08-19
updated: 2026-08-19
source: jason
labels: [quality-gates, wave-p, debt, detekt, token]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-010, MUD-031, MUD-034n]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: ""
worker_pid: ""
---

# MUD-041 — Static debt zero (detekt baseline + token overrides)

## Problem
Harness ratchets still hide legacy debt:

- Detekt **baseline ~1478** CurrentIssues IDs (new code hard; legacy soft)
- Token budget **16 path overrides** remain after god-split program
- “No debt” product-ready bar means green without baseline/override crutches

## Acceptance
- [ ] `config/detekt/baseline.xml` **CurrentIssues empty** (or file removed + config points at empty baseline) — detekt hard on full tree with **0** baselined smells
- [ ] `config/quality/token_budget_kt.json` **overrides: []** — no path/fn ceilings above global W/E
- [ ] Full-repo token/structure report shows **0 `*_E`** under global limits (warns OK only if policy keeps W soft; no E)
- [ ] Residual god-files split/refactored as needed; overrides may only lower during burn-down, never raise; finish at zero overrides
- [ ] Docs: `docs/DETEKT.md`, `docs/TOKEN_BUDGET_KT.md`, `docs/MODERNIZATION_STATUS.md` reflect debt-zero
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] No silent rule disable / no mass `@Suppress` dump as fake baseline

## Non-goals
- Style-only churn unrelated to failing smells/token E
- PIT hard 80 (MUD-040)
- Handler dup hard (MUD-039)
- Playtest UX (MUD-042)

## Notes
Large ticket — **one plan**, multi-commit impl OK, still one ticket. Prefer fix real smells over weakening detekt.yaml. testbot smells count. Coordinate with MUD-039 if handler splits overlap; serial one builder per tree.
