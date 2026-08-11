---
id: MUD-015
area: testing
title: Kotest property checks on combat/graph hot paths
status: done
priority: med
created: 2026-08-09
updated: 2026-08-11
source: jason
labels: [quality-gates, pbt, testing, wave-d]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-008, MUD-012]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-015-kotest-pbt-hotpaths.md
worker_out_dir: tmp/workers/MUD-015
worker_pid: ""
grok_session: ""
codex_session: ""
needs_jason: null
approved_by: Astra
approved_at: 2026-08-11 01:40 MST
---

## Astra approve (2026-08-11 01:40 MST)
Common-sense approve: pure `:core` graph + combat HP clamp domains locked; Jason domain pick not required. Fresh impl session next (plan file handoff).

# MUD-015 — Kotest PBT hot paths

## Problem
Combat/graph edge cases are easy for agents to regress with example-only tests.

## Acceptance
- [x] Kotest property tests on agreed pure hot paths (combat math and/or world graph invariants)
- [x] No live LLM; deterministic seeds
- [x] Test-lock escape used only with ticket scope
- [x] Short note which properties are law vs soft
- [x] Jason opinion on which 1–2 domains if spike needed mid-ticket → human_gated pause
  - **Skipped:** Astra locked pure `:core` domains; no product redesign needed

## Source
DIGEST-025 gate #7 / 60d

## Resolution
Implemented Kotest 5.9.1 property suite on pure `:core` only:

- Catalog `kotest = "5.9.1"` + `libs.kotestProperty` (`kotest-property-jvm`); `testImplementation` on `:core` only
- `GraphNodeComponentPropertyTest` — laws G1–G4 + S2 removeEdge present/throw
- `CombatComponentPropertyTest` — laws C1–C3 (non-neg damage/heal generators)
- All `checkAll` use `PropTestConfig(seed = 15_015L, iterations = 100)`
- `docs/PBT.md` — seed policy + law/soft table
- Test-lock regenerated: `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` (111 files)
- Product `src/main` untouched; example tests unchanged

**Verify:** `./tools/verify_mud.sh --core` → exit 0 · `tmp/dod-summary.json`  
**Closeout:** `tmp/workers/MUD-015/CLOSEOUT.md`
