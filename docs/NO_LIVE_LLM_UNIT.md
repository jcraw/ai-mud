# No live LLM in unit tests (MUD-032)

Static gate against unit tests constructing a real OpenAI client or loading live API keys. Keeps DoD deterministic (no cost, no network flake).

## Paths

| Path | Role |
|------|------|
| `tools/quality/check_no_live_llm_unit.sh` | Standalone `rg` checker (exit 0/1) |
| `config/quality/no_live_llm_unit_allowlist.txt` | Optional path allowlist (empty v1) |

## Forbidden (under `*/src/test/**/*.kt`, non-testbot)

| Pattern | Code | Meaning |
|---------|------|---------|
| `\bOpenAIClient\s*\(` | `LIVE_LLM_OPENAI_CLIENT` | Real client **construction** |
| `OPENAI_API_KEY` | `LIVE_LLM_API_KEY` | Env key load |
| `openai.api.key` | `LIVE_LLM_API_KEY` | Properties key load |

## Allowed (not matched)

- `OpenAIResponse` / builders returning frozen fixtures
- `class MockLLMClient : LLMClient` and `chatCompletion` overrides
- `import …OpenAIClient` **without** `OpenAIClient(` (soft; construction is the hard rule)
- Product `src/main/**` (not scanned)

## Carve-outs

- **`testbot/**` hard-excluded** — integration/behavior lane may use live LLM; not on `--core`/`--full` unit path. Do not rewrite testbot for this gate.
- **Allowlist** — empty by design for v1. Only for future non-testbot integration tests if ever needed; each line needs a ticket id + reason (PR review).

## Verify behavior

- **Default / fast / core / full / pitest:** hard `./tools/quality/check_no_live_llm_unit.sh` after test-lock.
- **Quarantine lane:** skipped (debt-only).
- Fail-closed if checker or `rg` missing.

```bash
./tools/quality/check_no_live_llm_unit.sh   # direct
./tools/verify_mud.sh --core                # includes gate
./tools/verify_mud.sh --dry-run --core      # prints checker command
```

## Residual (out of scope v1)

Generic non-OpenAI HTTP / network in tests is **not** blocked. Expand only via a new ticket.

## Design

Wave Q2 **B2** — `docs/AGENT_QUALITY_GATES_DESIGN.md`.
