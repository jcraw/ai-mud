---
id: MUD-021
area: testing
title: Quarantine slice 3 — lore, death loot, treasure placer
status: done
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [testing, quarantine, wave-f]
assignee: ""
worker: grok
phase: done
approved_by: Astra
approved_at: 2026-08-11 14:02 MST
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-020]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-021-quarantine-slice3-lore-death-treasure.md
worker_out_dir: tmp/workers/MUD-021
worker_pid: ""
grok_session: ""
codex_session: ""
plan_session: ""
impl_session: "grok.impl"
---

# MUD-021 — Quarantine slice 3 (lore / death / treasure placer)

## Problem
After MUD-020, residual quarantine should still include worldgen/death/placer debt:

| Cluster | # | Note |
|---------|--:|------|
| LoreInheritanceEngine + WorldGenerator | 2 | parent keyword embedding in lore |
| DeathHandler NPC death loot | 1 | V3 space/corpse path |
| TreasureRoomPlacer frontier exclusion | 1 | selected Frontier as treasure room |

SkillManager ×8 remains **deferred** (Jason product opinion) unless already cleared earlier.

## Acceptance
- [x] Fix or honestly re-contract the **4** tagged methods above (real fixes preferred)
- [x] Un-tag only when green for real; no weaken/`@Disabled`/delete
- [x] `docs/TEST_QUARANTINE.md` + `quarantine_count` updated
- [x] `./tools/verify_mud.sh --core` exit 0; quarantine lane residual OK
- [x] test-lock regen if tests touched
- [x] Closeout lists any still-deferred SkillManager debt explicitly

## Non-goals
- SkillManager L1 vs L2 unlock redesign (unless trivial test-only and documented)
- Full worldgen rewrite
- MUD-007 playtest

## Notes
- DeathHandler: prefer V3 graph space/corpse truth over forcing old room asserts.
- TreasureRoomPlacer: hard-exclude Boss + Frontier in selection (prod fix if bug).

## Done notes (2026-08-11)
- C4 prod: `isTreasureEligible` shared Hub/Boss/Frontier exclusion on candidates + fallback
- C1/C2 harness: mock lore embeds parent keywords + direction
- C3 re-contract: V3 dual-write; gold `>= 1` (variance 0.8–1.2); multi-run 5× green
- Tags **12 → 8** (SkillManager ×8 residual); closeout `tmp/workers/MUD-021/CLOSEOUT.md`
