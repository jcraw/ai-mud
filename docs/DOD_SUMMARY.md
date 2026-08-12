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

**Gates** (fixed keys): `compile`, `tests`, `detekt`, `konsist`, `test_lock`, `pitest`.  
Each: `status` ∈ `pass|fail|skipped`, `duration_s`, optional `note`; pitest may include `mutation_score`.

**Findings** (always present; may be empty):

```json
{ "code": "TOKEN_FILE_E", "path": "app/…", "metric": 3200, "limit": 2500, "remediation": "split …" }
```

Empty `"findings": []` is valid until MUD-028+ populates token/structure rows.

## Stable codes (reserved)

| Code | Owner | Meaning |
|------|-------|---------|
| `TOKEN_FILE_W` / `TOKEN_FILE_E` | MUD-028/031 | file token budget warn/err |
| `TOKEN_FN_W` / `TOKEN_FN_E` | MUD-028/031 | function token budget |
| `STRUCTURE_*` | MUD-028+ | structure ceilings when wired |
| `DETEKT_NEW` | later | unbaselined detekt |
| `TEST_LOCK` | later | lock mismatch (gate already fails) |
| `DOD_SCHEMA` | validator | summary itself invalid |

## Validation

Post-write light shape check (`validate_dod_summary` in `verify_mud.sh`): fail closed → exit 1 + note; bad file kept for debug. Prefer `python3` + stdlib `json`; bash fallback if python missing.

## Closeout

AGENTS DoD: cite `tmp/dod-summary.json` (or `$MUD_DOD_SUMMARY`) when verify ran.
