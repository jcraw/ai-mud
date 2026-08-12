---
id: MUD-031
area: tooling
title: Token/structure hard-on-touched default (Wave Q2)
status: done
priority: high
created: 2026-08-11
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q]
assignee: "grok"
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-030]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-031-token-hard-on-touched.md
worker_out_dir: tmp/workers/MUD-031
worker_pid: ""
approved_by: Astra
approved_at: "2026-08-12 01:26 MST"
impl_session: "grok-impl-2026-08-12-MUD-031"
---

# MUD-031 — Hard-on-touched default

## Problem
Jason accepted: new/touched prod code must meet token/structure ceilings before features expand debt.

## Acceptance
- [x] Default/fast/core: hard-fail error-tier token/structure on **touched** prod `.kt` (git diff scope)
- [x] `config/quality/token_budget_kt.json` overrides for known gods only — each override cites burn-down ticket id
- [x] New files cannot use overrides
- [x] Override caps may only lower over time (doc rule in AGENTS/DESIGN)
- [x] Full-repo report still available; not hard without flag
- [x] AGENTS + DESIGN updated
- [x] `--core` green on clean tree (overrides cover current gods if needed)

## Non-goals
- Splitting god files (Wave Q3 tickets)
- PIT 80% hard

## Resolution

**Done 2026-08-12 (Grok impl).** Inverted MUD-030 soft pilot → **hard-on-touched default** on default/fast/core/full.

| Deliverable | Detail |
|-------------|--------|
| `tools/verify_mud.sh` | Hard-on-touched default; soft opt-out `MUD_TOKEN_SOFT=1` / `--token-soft`; `MUD_TOKEN_HARD` / `--token-hard` redundant keep; `SCOPE=full` soft-only |
| `tools/quality/check_token_budget_kt.py` | Apply path overrides; required `ticket` `MUD-\d+`; new/Added ban; override E uses `metric > limit` |
| `config/quality/token_budget_kt.json` | **55** god overrides @ measured caps + `ticket: MUD-034` |
| Docs | `docs/TOKEN_BUDGET_KT.md`, `AGENTS.md` Verification, DESIGN §7.1 surgical |

**Verify:** `./tools/verify_mud.sh --core` exit **0** · `gates.token_budget` pass `E=0 W=0 scope=touched hard-on-touched` · dod-summary `tmp/dod-summary-mud031-core.json` (also `tmp/dod-summary.json` path pattern).

**Smokes (§6):** staged over-budget new `.kt` → hard fail; same + soft opt-out → token pass report-only; god under override → no file E; new-file ban ignores override; `SCOPE=full` soft pass; quarantine skips token.

**Residual:** untracked new `.kt` not in git-diff (stage); god splits → MUD-034+; no commit/push this ticket.

**Closeout:** `tmp/workers/MUD-031/CLOSEOUT.md`
