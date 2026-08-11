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

