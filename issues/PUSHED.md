# AI MUD final push (clear-backlog)

- **When:** 2026-08-11 02:57 MST
- **Branch:** master
- **SHA:** `5e75c5298e7d1e0adc120d5474d205f721237326`
- **Remote:** origin (`jcraw/ai-mud`)
- **Force:** no

## Included (allowlist)
- Ops: `AGENTS.md`, `issues/`, `plans/`, `docs/` (+ research), `tools/verify_mud.sh`, `tools/test_lock.sh`, test-lock manifest
- Gates/CI: detekt, konsist, PIT, PBT, quarantine, `.github/workflows/verify.yml`, gradle portable java.home
- Product tickets: treasure inventory impl (MUD-007 code; playtest still gated), CLAUDE/CODEX deprecation (MUD-018)
- Also pushed prior local commit `625c9aa` (bot recognize low health)

## Excluded
- Dirty `testbot/` (MUD-009)
- `tmp/`, logs, `local.properties` / secrets

## Human-gated remaining
- **MUD-007** — Jason playtest
- **MUD-009** — Jason git hygiene

## Cron
- Disarmed: `AI MUD clear-backlog re-drain` (`54f4d0f8-485f-45fb-90b8-425efcdedb80`)

## Wave F pushes (2026-08-11+)
- Target tickets: MUD-019 · MUD-020 · MUD-021
- Rule: one allowlisted push per ticket after `status: done`
- Cron: `AI MUD Wave F re-drain` id `0047039f-0dd4-46ea-a487-86aef73f737a` every 20m
- Rows below appended as each lands:

### MUD-019 — floor-item V2 inventory parity
- **When:** 2026-08-11 12:42 MST
- **SHA:** `d6446aa76abb9d2a32fd2f3684ec7e693a2062aa` (bookkeep `3b30a152ba29dd4e834774c7547a1e8e7121c36b`)
- **Branch:** master → origin
- **Force:** no
- **Included:** FloorItemTakeApply + handlers (app/client/GameServer/MultiUserGame), contract test, KNOWN_ISSUES, test-lock 112, issues/plans Wave F tickets+board+handoff
- **Excluded:** testbot/, tmp/, secrets

### MUD-020 — quarantine slice 2 catalog/hierarchy
- **When:** 2026-08-11 13:42 MST
- **SHA:** `e68ff12c9ed96c94dbdc353daeb895959414fb81` (bookkeep `49e861c792622b4a76284ac4d34c92061a2c6eb6`)
- **Branch:** master → origin
- **Force:** no
- **Included:** SkillClassifier prod filter, SkillDefinitions/DungeonInitializer KDoc+tests, TEST_QUARANTINE 20→12, AGENTS pointer, test-lock, issues/plans/board/handoff
- **Excluded:** testbot/, tmp/, secrets

### MUD-021 — quarantine slice 3 lore/death/placer
- **When:** 2026-08-11 14:22 MST
- **SHA:** `4eef71e792af47540673d17d0ff2e56b6abc6797` (bookkeep `21714c64bf810e6e37bf5d6a15ef70fbd7c58d75`)
- **Branch:** master → origin
- **Force:** no
- **Included:** TreasureRoomPlacer isTreasureEligible prod, lore/worldgen harness, DeathHandler V3 re-contract, TEST_QUARANTINE 12→8, AGENTS pointer, test-lock, issues/plans/board/handoff
- **Excluded:** testbot/, tmp/, secrets

## Wave F complete
- All of MUD-019 · MUD-020 · MUD-021 done + pushed
- Cron `AI MUD Wave F re-drain` (`0047039f-0dd4-46ea-a487-86aef73f737a`) disarmed 2026-08-11 14:22 MST
- Human-gated left: MUD-007 playtest · MUD-009 Jason git · SkillManager ×8 L1/L2 opinion


### MUD-009 — git hygiene
- **When:** 2026-08-11 16:25 MST
- **SHA:** `74c343e`
- **Branch:** master → origin
- **Force:** no
- **Included:** testbot SkillProgression message parsers only
- **Note:** closed under Jason finish-mod; tree clean after

## Wave G pushes (2026-08-11+)
- Target: MUD-022 · 023 · 024 · 025
- Cron: AI MUD Wave G re-drain (`cd5f9827`) every 20m
- Rows below as each lands:

### MUD-022 — SkillManager quarantine clear (8→0)
- **When:** 2026-08-11 16:45 MST
- **SHA:** `2b265caab28a4fa93cae4ac4721d529aaf0ef612` (bookkeep `b497f0bc4692cdb4cbd77b25ed7afe98011f8255`)
- **Branch:** master → origin
- **Force:** no
- **Included:** GameConfig skillXpMultiplier 1.0f, SkillManagerTest un-quarantine+re-contract, TEST_QUARANTINE 0, test-lock, AGENTS/KNOWN_ISSUES posture, MUD-007/009 harness-done, Wave G tickets 022–025 + plan/board/handoff
- **Excluded:** testbot/, tmp/, secrets

### MUD-023 — Drop path → V2 InventoryComponent parity
- **When:** 2026-08-11 17:26 MST
- **SHA:** `85a1af10a8ce8fa40e4e2ce906f81229fb012b15` (bookkeep `3a60b388379689450b6792b0550b06434f9f1273`)
- **Branch:** master → origin
- **Force:** no
- **Included:** FloorItemDropApply + handlers (app/client/GameServer), FloorItemDropContractTest ×5, KNOWN_ISSUES drop residual fixed, test-lock 113, plan/board/handoff
- **Excluded:** testbot/, tmp/, secrets

### MUD-024 — V1 inventory/equip production write purge
- **When:** 2026-08-11 18:06 MST
- **SHA:** `54bcce4449fd7c5d218452eda18ac938d6e15c83` (bookkeep `ba3fa98d3cb5156602e8bb7d78bb9c5456a2464f`)
- **Branch:** master → origin
- **Force:** no
- **Included:** GiveItemApply + UseConsumableApply, equip V2 all surfaces, GUI buy addItemInstance, GiveItemContractTest, KNOWN_ISSUES/V2_REMOVAL/TODO residual notes, test-lock 114, plan/board/handoff
- **Excluded:** testbot/, tmp/, secrets

### MUD-025 — modernization program closeout
- **When:** 2026-08-11 18:25 MST
- **SHA:** `6b9e0fb347410ecf8cb68b6a9ffe8d2b8b7f28b7` (bookkeep `e8868ff01a515cd1efcb2ea85e95e04a549de025`)
- **Branch:** master → origin
- **Force:** no
- **Included:** docs/MODERNIZATION_STATUS.md, TESTING/TEST_QUARANTINE/AGENTS quarantine 0 truth, KNOWN_ISSUES pointer, MUD-025 ticket+plan, board/handoff Wave G complete
- **Excluded:** testbot/, tmp/, secrets, *.kt

## Wave G complete
- All of MUD-022 · 023 · 024 · 025 done + pushed
- Cron `AI MUD Wave G re-drain` (`cd5f9827-947e-4351-bdc4-78603d7d6fd2`) disarmed 2026-08-11 18:25 MST
- Harness modernization Waves A–G closed; product playtest not a gate


## Wave Q pushes (2026-08-11+)
- Target tickets: MUD-026…038 (serial Q1 then Q2+)
- Rule: one allowlisted push per done ticket (or batch when design+first impl land together)
- Cron: `AI MUD Wave Q re-drain` id `fb01f053-1e6e-4afa-b908-56a8a761a1a0` every 20m
- Rows below appended as each lands:

### MUD-026 + MUD-027 — design lock + dod-summary v2
- **When:** 2026-08-11 22:53 MST
- **SHA:** `f123f5ebf33ebb637a882b3b58356411f60d3215`
- **Branch:** master → origin
- **Force:** no
- **Included:** DESIGN + Wave Q tickets 026–038, dod_summary.schema.json v2, verify_mud findings[] + validate, docs/DOD_SUMMARY.md, AGENTS Verification, plan MUD-027, board/handoff
- **Excluded:** testbot/, tmp/, secrets, *.kt product

### MUD-028 — token/structure report-only checker
- **When:** 2026-08-11 23:33 MST
- **SHA:** `1e93751c388d870b61f0474a612b0474e970924f`
- **Branch:** master → origin
- **Force:** no
- **Included:** tools/quality/check_token_budget_kt.py, config/quality/token_budget_kt.json, docs/TOKEN_BUDGET_KT.md, DESIGN A6/A7 + DOD_SUMMARY pointer, plan MUD-028, board/handoff
- **Excluded:** testbot/, tmp/, secrets, product *.kt
