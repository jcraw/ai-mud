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
- **SHA:** `(pending push)`
- **Branch:** master → origin
- **Force:** no
- **Included:** GameConfig skillXpMultiplier 1.0f, SkillManagerTest un-quarantine+re-contract, TEST_QUARANTINE 0, test-lock, AGENTS/KNOWN_ISSUES posture, MUD-007/009 harness-done, Wave G tickets 022–025 + plan/board/handoff
- **Excluded:** testbot/, tmp/, secrets
