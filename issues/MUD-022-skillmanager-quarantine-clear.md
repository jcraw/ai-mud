---
id: MUD-022
area: engine
title: Clear SkillManager quarantine ×8 (align to dual-path design)
status: done
priority: high
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [test, quarantine, skills, wave-g]
assignee: ""
worker: grok
phase: done
approved_by: Astra
approved_at: "2026-08-11 16:30 MST"
plan_session: 019ff325-b279-7e31-85aa-b36814f24a5a
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-021]
verify: "./tools/verify_mud.sh --core && ./tools/verify_mud.sh --quarantine"
plan: plans/2026-08-11-ai-mud-MUD-022-skillmanager-quarantine-clear.md
worker_out_dir: tmp/workers/MUD-022
worker_pid: ""
grok_session: ""
codex_session: ""
impl_session: ""
---

# MUD-022 — Clear SkillManager quarantine ×8

## Problem
Last quarantine residual is **SkillManagerTest ×8** (count **8**). Failures look like dual-path progression rewrite drift vs older asserts (lucky unlock level, failure XP scale, multi level-up counts). Deferred for “Jason L1/L2 opinion” — closing modernization: **design SoT =** `docs/requirements/V2/FEATURE_PLAN_generic_skill_progression.md` (unlock 0→1 at **level 1**, failure XP **~20%**, quadratic thresholds, defensive isolation).

## Acceptance
- [x] All 8 `SkillManagerTest` methods un-quarantined and green under default excludeTags
- [x] Prod and/or tests aligned to FEATURE_PLAN dual-path contracts (prefer **fix prod** when drift is a clear bug vs plan; re-contract test only when plan intentionally differs)
- [x] `docs/TEST_QUARANTINE.md` count **0** (or only non-SkillManager if any new — goal **0**)
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] `./tools/verify_mud.sh --quarantine` exit 0 (empty quarantine set OK / no hard fails)
- [x] test-lock regen if tests touched (`MUD_ALLOW_TEST_CHANGES=1`)
- [x] BOARD / AGENTS residual SkillManager notes cleared

## Non-goals
- Full skill system redesign / new L3+ economy
- Combat balance pass
- Detekt baseline mass regen
- git push (Astra Wave G)

## Notes
Design anchors: lucky unlock **starts at level 1**; grantXp success full XP; failure ~20% (not 10×); level-up at `100 * level^2` thresholds; defensive skills independent per entity.

## Closeout
See `tmp/workers/MUD-022/CLOSEOUT.md`. Quarantine **8 → 0**. C1 prod `skillXpMultiplier=1.0f`; C2 locked grantXp re-contract (auto-unlock); C3 L1 hard assert + isolation via lucky-off.
