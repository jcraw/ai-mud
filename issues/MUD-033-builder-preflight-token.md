---
id: MUD-033
area: tooling
title: Builder plan/brief token preflight (Wave Q2)
status: done
priority: med
created: 2026-08-11
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, process]
assignee: "grok"
worker: grok
phase: closeout
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-030]
verify: "./tools/verify_mud.sh"
plan: plans/2026-08-12-ai-mud-MUD-033-builder-preflight-token.md
worker_out_dir: tmp/workers/MUD-033
worker_pid: ""

# MUD-033 — Plan/brief token preflight

## Problem
Fat plans burn builder context (jam GJ-026 lesson). Need preflight before approve/impl.

## Acceptance
- [x] Tool checks `plans/*.md` and `tmp/workers/*/PLAN*.md` + briefs against DESIGN budgets (plan 2k/3.5k, brief 1.2k/2k tok)
- [x] Exit codes: 0 clear, 1 warn-only mode, 2 hard fail (document)
- [x] ORCHESTRATION or AGENTS one-liner: run before plan approve when practical
- [x] No product code changes
- [x] Optional wire as `verify_mud.sh --preflight <path>`

## Non-goals
- Changing orchestration launcher behavior across all repos

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-033-builder-preflight-token.md` (mirror `tmp/workers/MUD-033/PLAN.md`)
- Phase: **done** — implemented after **APPROVED by Astra 2026-08-12 02:14 MST**
- Approach: `tools/quality/check_builder_preflight.py` D1/D2; exit 0/1/2; optional --preflight; ORCH one-liner; no product kt

## Resolution
Shipped Wave Q2 D1/D2 builder plan/brief token preflight:

- **Checker:** `tools/quality/check_builder_preflight.py` — tok `ceil(chars/4)`; plan 2k/3.5k; brief 1.2k/2k; exit 0/1/2; `--allow-warn`; PATH + default inventory; `plans/` always plan (titles may contain "brief"); `*BRIEF*` → brief before `PLAN*`
- **Docs:** `docs/BUILDER_PREFLIGHT.md`; ORCHESTRATION plan→approve step 2; DESIGN D1/D2 live pointer
- **Verify:** optional `./tools/verify_mud.sh --preflight <path>` only (not default lanes); checker 2→fail; 1 warn→pass+note; 0→pass; `gates.builder_preflight` when recorded
- **Smokes:** lean→0; warn→1; allow-warn→0; fat plan/brief→2; default inventory exit 1 (historical warn, fail=0); `--core` PASS
- **No product `*.kt`** · no D7 pack · no mass plan rewrites · no commit/push
- Closeout: `tmp/workers/MUD-033/CLOSEOUT.md`
