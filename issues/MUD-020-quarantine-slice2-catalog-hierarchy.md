---
id: MUD-020
area: testing
title: Quarantine slice 2 — catalog + hierarchy contracts
status: done
priority: med
created: 2026-08-11
updated: 2026-08-11
source: jason
labels: [testing, quarantine, wave-f]
assignee: ""
worker: grok
phase: done
approved_at: 2026-08-11 13:02 MST
approved_by: Astra
agent_eligible: true
eligibility: agent_eligible
needs_jason: ""
depends_on: [MUD-017]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-11-ai-mud-MUD-020-quarantine-slice2-catalog-hierarchy.md
worker_out_dir: tmp/workers/MUD-020
worker_pid: ""
grok_session: ""
codex_session: ""
plan_session: ""
impl_session: ""
---

# MUD-020 — Quarantine slice 2 (catalog + hierarchy)

## Problem
MUD-017 cleared 3 of 23 quarantine tags. Residual **20**. This slice owns **agent-fixable contract drift** only:

| Cluster | # | Note |
|---------|--:|------|
| SkillDefinitions combat count | 1 | catalog 6→11 |
| SkillClassifier fallback/filter | 4 | extra matches / empty-list contract |
| DungeonInitializer region count | 3 | hierarchy 3→4 REGIONs |

**Out of this ticket:** SkillManager ×8 (likely Jason L1 vs L2 opinion), Lore/WG ×2, DeathHandler ×1, TreasureRoomPlacer ×1 → later tickets.

## Acceptance
- [x] Triage each of the **8** tagged methods; real fix = prod contract OR intentional stronger test (document which)
- [x] Un-tag only when genuinely fixed — no assert-weaken, no `@Disabled`, no silent delete
- [x] Quarantine count drops by the number cleared (target **20 → ≤12** if all 8 land)
- [x] `docs/TEST_QUARANTINE.md` updated (cleared table + residual)
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] `./tools/verify_mud.sh --quarantine` residual hard-fail OK; `tmp/dod-summary.json` `quarantine_count` matches
- [x] test-lock regen with `MUD_ALLOW_TEST_CHANGES=1` if tests touched

## Non-goals
- SkillManager XP/level unlock product decisions
- Lore embedding, DeathHandler loot, TreasureRoomPlacer frontier
- Mass detekt baseline regen

## Notes
- Prefer **prod truth** when catalog/hierarchy intentionally grew; align tests to documented contracts.
- Split further only if blocked mid-impl.
