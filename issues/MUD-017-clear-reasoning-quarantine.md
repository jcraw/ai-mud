---
id: MUD-017
area: testing
title: Clear/repair :reasoning quarantine (real fixes)
status: done
priority: low
created: 2026-08-09
updated: 2026-08-11
source: jason
labels: [testing, quarantine, wave-e]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-008, MUD-013]
verify: "./tools/verify_mud.sh --quarantine"
plan: plans/2026-08-11-ai-mud-MUD-017-clear-reasoning-quarantine.md
worker_out_dir: tmp/workers/MUD-017
worker_pid: ""
grok_session: ""
codex_session: ""
approved_by: Astra
approved_at: "2026-08-11 02:20 MST"
plan_session: 019ff01c-ba3c-79b1-8e0b-57891011202f
---

# MUD-017 — Clear reasoning quarantine

## Problem
Quarantine is honest debt, not forever. 90d DIGEST-025 goal: repair real failures without assert-gaming.

## Acceptance
- [x] Triage list from MUD-008; fix in small slices (one PR/ticket wave if large — split OK)
- [x] Tests un-quarantined only when genuinely fixed
- [x] No delete-or-weaken to clear debt
- [x] Quarantine count drops; dod-summary reflects it

## Resolution

**Slice 1 done (2026-08-11):** 23 → **20** quarantine tags.

| Cleared | Fix |
|---------|-----|
| CapacityCalculator linear STR-0 | Test aligned to `MINIMUM_CAPACITY` (10.0); kept 5/20 linear asserts |
| ThemeRegistry all themes | Size 9 + `training grounds` membership |
| ThemeRegistry magma semantic | Prod: add `"volcanic"` keyword |

**Verify:** `--core` exit 0; `--quarantine` residual hard-fail (20 fails) OK; `tmp/dod-summary.json` `quarantine_count`: 20.

**Deferred (follow-up MUD-017b / new id):** SkillManager ×8, SkillClassifier ×4, SkillDefinitions ×1, DungeonInitializer ×3, Lore/WG ×2, DeathHandler ×1, TreasureRoomPlacer ×1.

Closeout: `tmp/workers/MUD-017/CLOSEOUT.md`

## Astra approve (2026-08-11 02:20 MST)
Common-sense approve: slice-1 Capacity floor + ThemeRegistry count/magma only (23→20); real fixes not assert-gaming; Skill*/Dungeon/Lore/Death/Treasure deferred. Fresh impl session next (plan file handoff).
