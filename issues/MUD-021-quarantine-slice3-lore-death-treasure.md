---
id: MUD-021
area: testing
title: Quarantine slice 3 — lore, death loot, treasure placer
status: open
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [testing, quarantine, wave-f]
assignee: ""
worker: ""
phase: backlog
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-020]
verify: "./tools/verify_mud.sh --core"
plan: ""
worker_out_dir: tmp/workers/MUD-021
worker_pid: ""
grok_session: ""
codex_session: ""
plan_session: ""
impl_session: ""
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
- [ ] Fix or honestly re-contract the **4** tagged methods above (real fixes preferred)
- [ ] Un-tag only when green for real; no weaken/`@Disabled`/delete
- [ ] `docs/TEST_QUARANTINE.md` + `quarantine_count` updated
- [ ] `./tools/verify_mud.sh --core` exit 0; quarantine lane residual OK
- [ ] test-lock regen if tests touched
- [ ] Closeout lists any still-deferred SkillManager debt explicitly

## Non-goals
- SkillManager L1 vs L2 unlock redesign (unless trivial test-only and documented)
- Full worldgen rewrite
- MUD-007 playtest

## Notes
- DeathHandler: prefer V3 graph space/corpse truth over forcing old room asserts.
- TreasureRoomPlacer: hard-exclude Boss + Frontier in selection (prod fix if bug).
