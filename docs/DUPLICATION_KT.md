# Handler duplication gate (warn-only)

**Ticket:** MUD-036 · **Design:** `docs/AGENT_QUALITY_GATES_DESIGN.md` C3  
**Depends:** MUD-031 (token hard-on-touched, done)

Jam-style **block clone** on console/GUI handler twins. Standalone checker is always **exit 0** (`exit_policy: report_only`). **`./tools/verify_mud.sh`** owns policy. **v1 = always warn** (`DUP_BLOCK_W`). `DUP_BLOCK_E` is reserved and **not emitted**.

| Piece | Path |
|-------|------|
| Checker | `tools/quality/check_duplication_kt.py` |
| Config | `config/quality/duplication_kt.json` |
| Default JSON out | `tmp/duplication_kt.json` (optional `--json-out`) |
| Verify side JSON | `tmp/duplication_kt_verify.json` (gitignored; written by verify) |

## Scope (v1)

- Includes: `app/**/handlers/**/*.kt` and `client/**/handlers/**/*.kt` under `src/main`
- Excludes: `build/`, `src/test/**`, GameServer (lives under `app/`, not `handlers/`)
- Intra-app and intra-client clones are **out of v1** — a finding requires the same 10-line window on **both** an app path and a client path
- Identifier rename / AST clone is **out of v1** (literal copy-paste only)

## Algorithm

1. Normalize per file: strip `/* */` and `//`; drop `package` / `import`; drop `@file:Suppress(...)` (identical 11-line headers are FP); drop blanks; collapse inner whitespace. **Do not** rename identifiers.
2. Sliding window of **10** consecutive normalized lines; SHA-1 the window.
3. Report a pair only when the hash appears on both sides.
4. Merge adjacent windows per `(app_file, client_file)` into one region. One finding per pair if merged lines ≥ **10**.
5. Sort by `metric` desc; cap **50**.

Config (`config/quality/duplication_kt.json`):

```json
{ "window": 10, "min_block_lines": 10, "allowlist": [] }
```

Allowlist entries are `{ "app": "…", "client": "…", "ticket": "MUD-xxx" }`. Empty in v1. Ticket required; new pairs cannot be silently dropped. Allowlist is for a **later hard** rung — do not populate to hide clones in this ticket.

## Verify policy (R0 warn-only)

| Lane | v1 |
|------|----|
| `default` / `fast` / `core` / `full` | **Run warn-only** |
| `quarantine` / `pitest` / `--preflight` | **Skip** |
| `--dry-run` | Gate listed; checker **not** invoked |

- Gate name: **`duplication_kt`** (optional; **not** in the required schema tuple).
- `record_gate` **pass** if the checker ran (even when `W>0`). Note: `warn-only W=n pairs=m`.
- Findings merge into `tmp/dod-summary.json` via `append_finding` (cap 50).
- Missing `python3` / checker → **skip** (do not brick `--core`).
- Checker crash / empty JSON → **fail** (script bug).
- **No** `--dup-hard` flag in this ticket.

```bash
# Standalone (always exit 0)
python3 tools/quality/check_duplication_kt.py --root . --json-out tmp/duplication_kt.json

# Via verify (warn-only; --core stays green on clones)
./tools/verify_mud.sh --core
```

## Path to hard (docs only — do not flip here)

| Rung | Policy | This ticket? |
|------|--------|--------------|
| **R0 (now)** | warn-only on default/fast/core/full; `--core` never fails on clones | **yes** |
| **R1** | `MUD_DUP_HARD=1` fails **`--full` only** on `DUP_BLOCK_E` (emit E = same pairs, or W above a later cap) | no — follow-on |
| **R2** | hard default on `--full`; core stays warn until Jason/Astra | no |

Never hard-fail default/fast/core in this ticket (DESIGN C3 is Tier C). Do not force handler merges to go green (that is **MUD-037** / later extracts).

## Findings

```json
{ "code": "DUP_BLOCK_W", "path": "app/…/Foo.kt", "metric": 42, "limit": 10,
  "remediation": "clone of client/…/ClientFoo.kt (42 lines); extract shared apply or thin one side — do not merge in MUD-036" }
```

`path` is the app file; the client peer is in `remediation`. `DUP_BLOCK_E` is documented here and in `docs/DOD_SUMMARY.md` but **not emitted** in v1.

## What this does *not* do

| Non-goal | Owner |
|----------|--------|
| Merge console/GUI handlers | MUD-037 / later extracts |
| GameServer / non-`handlers/` trees | later ticket if needed |
| Hard-fail on `--core` / default | R1/R2 follow-on |
| CPD / detekt CopyPaste / new deps | out of scope |

## Related

- `docs/DOD_SUMMARY.md` — `DUP_BLOCK_W` / `DUP_BLOCK_E` + optional `gates.duplication_kt`
- `docs/AGENT_QUALITY_GATES_DESIGN.md` — C3
- `config/quality/dod_summary.schema.json` — findings shape (v2); required gate tuple unchanged
