# Headless command smoke (MUD-038 / E1)

Scripted `look` → `take iron sword` → `inventory` → `attack rat` through the real console path (`MudGame` + `RealGameEngineAdapter`). Null LLM / fallback parse only. Not a playtest substitute.

## How to run

```bash
./tools/smoke_commands.sh
# or
./tools/verify_mud.sh --smoke
```

`tools/smoke_commands.sh` always sets `MUD_DATA_DIR` to a temp dir (play `data/*.db` untouched), unsets `OPENAI_API_KEY` for the Gradle child, and runs `./gradlew :app:run -PcommandSmoke=1`. First compile can take ~3 minutes (`SMOKE_TIMEOUT`, default 180s).

## Pass / fail

| Result | Output | Exit |
|--------|--------|------|
| Success | one `PASS` line | 0 |
| Failure | `FAIL <step>: <reason>` on stderr | 1 |

Take is asserted on V2 inventory (`templateId == iron_sword`), not message text. Attack **Hit or Miss** both pass; missing target / `AttackResult.Failure` fail.

## Verify

- **`--smoke` only.** Records `gates.command_smoke` (schema `additionalProperties`). Required gate tuple unchanged.
- **Never** on default / fast / `--core` / `--full` / `--pitest` / `--quarantine`.
- `--dry-run --smoke` does not run the product Gradle suite.
- Ticket `verify:` stays `./tools/verify_mud.sh --core`.

## Non-goals

Android / GUI / GameServer two-session / live OpenAI / rewriting `App.main` or TestBot.

## Design

Wave Q4 **E1** — `docs/AGENT_QUALITY_GATES_DESIGN.md`.
