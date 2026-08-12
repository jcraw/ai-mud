---
id: MUD-032
area: tooling
title: Gate — no live LLM/network in unit tests (Wave Q2)
status: done
priority: med
created: 2026-08-11
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, tests]
assignee: "grok"
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-032-no-live-llm-unit-tests.md
worker_out_dir: tmp/workers/MUD-032
worker_pid: ""
---

# MUD-032 — No live LLM in unit tests

## Problem
Live OpenAI in unit tests = cost, flake, non-deterministic DoD (DIGEST-025).

## Acceptance
- [x] Static or test gate fails if unit tests under `*/src/test/**` call real OpenAI / network LLM clients
- [x] Mocks / frozen fixtures only; document allowlist if integration tests need network (testbot excluded or separate lane)
- [x] Wired into core verify
- [x] `--core` exit 0
- [x] Short docs note

## Non-goals
- Rewriting testbot
- Product play smoke

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-032-no-live-llm-unit-tests.md` (mirror `tmp/workers/MUD-032/PLAN.md`)
- Phase: **done** — APPROVED by Astra 2026-08-12 01:53 MST → fresh IMPL shipped
- Approach: static `rg` gate `tools/quality/check_no_live_llm_unit.sh`; forbid `OpenAIClient(`, `OPENAI_API_KEY`, `openai.api.key` under `*/src/test/**`; hard-exclude `testbot/**`; empty allowlist; wire `no_live_llm_unit` hard on default/fast/core/full/pitest; skip quarantine

## Resolution
Shipped Wave Q2 **B2** no-live-LLM unit gate:

| Deliverable | Path / note |
|-------------|-------------|
| Checker | `tools/quality/check_no_live_llm_unit.sh` (fail-closed if `rg` missing) |
| Allowlist | `config/quality/no_live_llm_unit_allowlist.txt` (empty v1; testbot hard-excluded in script) |
| Verify wire | `run_no_live_llm_unit` / `skip_no_live_llm_unit` in `tools/verify_mud.sh`; optional `gates.no_live_llm_unit`; findings `LIVE_LLM_OPENAI_CLIENT` / `LIVE_LLM_API_KEY` |
| Docs | `docs/NO_LIVE_LLM_UNIT.md`; AGENTS Verification one-liner; DESIGN B2 live pointer; `docs/DOD_SUMMARY.md` optional gate + codes |
| Verify | `./tools/verify_mud.sh --core` → **PASS** (`no_live_llm_unit: pass`); `tmp/dod-summary.json` |
| Smokes | synthetic `OpenAIClient(` → fail; allowlist → pass; revert; quarantine dry-run skip; testbot still live; mocks clean |
| Closeout | `tmp/workers/MUD-032/CLOSEOUT.md` |

No product `*.kt`, no testbot rewrite, no test-lock regen, no commit/push.
