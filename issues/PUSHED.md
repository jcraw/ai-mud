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
