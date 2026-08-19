# dod-summary.json (machine DoD)

Compact verify artifact for agents. Prefer this over scraping Gradle logs.

## Path

| Item | Value |
|------|--------|
| Default path | `tmp/dod-summary.json` |
| Override | `$MUD_DOD_SUMMARY` |
| Writer | `tools/verify_mud.sh` (all lanes; dry-run still emits) |
| Schema | `config/quality/dod_summary.schema.json` (**schema_version 2**, MUD-027) |
| Design | `docs/AGENT_QUALITY_GATES_DESIGN.md` §5 |

## Shape (v2)

Top-level: `schema_version` (2), `tool`, `lane`, `result` (`PASS`|`FAIL`|`DRY_RUN`), `exit_code`, `generated_at`, `duration_s`, optional `dry_run`, `gates`, `quarantine_count`, `steps`, **`findings`**.

**Gates** (fixed required keys): `compile`, `tests`, `detekt`, `konsist`, `test_lock`, `pitest`.  
Optional (schema `additionalProperties`): **`token_budget`** (MUD-030/031) on default/fast/core/full (skipped quarantine/pitest); **`no_live_llm_unit`** (MUD-032) on default/fast/core/full/pitest (skipped quarantine); **`duplication_kt`** (MUD-039) on default/fast/core/full (hard E=0; skipped quarantine/pitest); **`command_smoke`** (MUD-038 / E1) on `--smoke` only (skipped other lanes; not on `--core`).  
Each: `status` ∈ `pass|fail|skipped`, `duration_s`, optional `note`; pitest may include `mutation_score`.

**Findings** (always present; may be empty):

```json
{ "code": "TOKEN_FILE_E", "path": "app/…", "metric": 3200, "limit": 2500, "remediation": "split …" }
```

Verify merges token/structure rows from the soft pilot checker on default/fast/core/full (scoped `--git-diff` by default; cap ~50). Live-LLM unit hits (when the gate fails) merge as `LIVE_LLM_*` rows (cap ~50). Handler block clones merge as `DUP_BLOCK_E` on default/fast/core/full (cap ~50; hard — fails `--core` when E>0; `MUD_DUP_SOFT=1` / `--dup-soft` report-only). Empty `"findings": []` is valid when clean or lane skips those gates. Standalone: `python3 tools/quality/check_token_budget_kt.py` → `docs/TOKEN_BUDGET_KT.md`; `./tools/quality/check_no_live_llm_unit.sh` → `docs/NO_LIVE_LLM_UNIT.md`; `python3 tools/quality/check_duplication_kt.py` → `docs/DUPLICATION_KT.md`.

## Stable codes (reserved)

| Code | Owner | Meaning |
|------|-------|---------|
| `TOKEN_FILE_W` / `TOKEN_FILE_E` | MUD-028/031 | file token budget warn/err |
| `TOKEN_FN_W` / `TOKEN_FN_E` | MUD-028/031 | function token budget |
| `STRUCTURE_*` | MUD-028+ | structure ceilings when wired |
| `LIVE_LLM_OPENAI_CLIENT` | MUD-032 | unit test constructs `OpenAIClient(` |
| `LIVE_LLM_API_KEY` | MUD-032 | unit test loads `OPENAI_API_KEY` / `openai.api.key` |
| `DUP_BLOCK_W` | MUD-036 | retired — not emitted (R0 warn-only) |
| `DUP_BLOCK_E` | MUD-039 | app/client handler block clone (hard; live) |
| `DETEKT_NEW` | later | unbaselined detekt |
| `TEST_LOCK` | later | lock mismatch (gate already fails) |
| `DOD_SCHEMA` | validator | summary itself invalid |

## Validation

Post-write light shape check (`validate_dod_summary` in `verify_mud.sh`): fail closed → exit 1 + note; bad file kept for debug. Prefer `python3` + stdlib `json`; bash fallback if python missing.

## Closeout

AGENTS DoD: cite `tmp/dod-summary.json` (or `$MUD_DOD_SUMMARY`) when verify ran.
