# Handler duplication gate (hard)

**Ticket:** MUD-039 (R2 hard; R0 was MUD-036) · **Design:** `docs/AGENT_QUALITY_GATES_DESIGN.md` C3  
**Depends:** MUD-036 (checker) + MUD-037 (parity applies)

Jam-style **block clone** on console/GUI handler twins. Standalone checker is always **exit 0** (`exit_policy: report_only`). **`./tools/verify_mud.sh`** owns policy. **R2 = hard** on default/fast/core/full: emit `DUP_BLOCK_E` and fail if E>0. Live `DUP_BLOCK_W` is retired. Soft opt-out: `MUD_DUP_SOFT=1` / `--dup-soft`.

| Piece | Path |
|-------|------|
| Checker | `tools/quality/check_duplication_kt.py` |
| Config | `config/quality/duplication_kt.json` |
| Default JSON out | `tmp/duplication_kt.json` (optional `--json-out`) |
| Verify side JSON | `tmp/duplication_kt_verify.json` (gitignored; written by verify) |

## Scope

- Includes: `app/**/handlers/**/*.kt` and `client/**/handlers/**/*.kt` under `src/main`
- Excludes: `build/`, `src/test/**`, GameServer (lives under `app/`, not `handlers/`)
- Intra-app and intra-client clones are **out** — a finding requires the same 10-line window on **both** an app path and a client path
- Identifier rename / AST clone is **out** (literal copy-paste only)

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

Allowlist entries are `{ "app": "…", "client": "…", "ticket": "MUD-xxx" }`. **Empty.** Ticket required; new pairs cannot be silently dropped. Do not populate to hide clones.

## Verify policy (R2 hard)

| Lane | R2 |
|------|----|
| `default` / `fast` / `core` / `full` | **Hard:** fail if `DUP_BLOCK_E` > 0 |
| Soft opt-out | `MUD_DUP_SOFT=1` or `--dup-soft` → pass + merge findings |
| `quarantine` / `pitest` / `--preflight` / `--smoke` | **Skip** |
| `--dry-run` | Gate listed; checker **not** invoked |

- Gate name: **`duplication_kt`** (optional; **not** in the required schema tuple).
- `record_gate` **fail** if hard and `E>0`. Note: `hard E=n pairs=m`.
- `record_gate` **pass** if hard and `E=0`. Note: `hard E=0 pairs=0`.
- Soft: **pass** + findings merge. Note: `soft E=n pairs=m report-only`.
- Missing `python3` / checker → **fail-closed** when hard; **skip** when soft.
- Checker crash / empty JSON → **fail** (script bug).
- **No** `MUD_DUP_HARD` / `--dup-hard` / R1 rung.

```bash
# Standalone (always exit 0)
python3 tools/quality/check_duplication_kt.py --root . --json-out tmp/duplication_kt.json

# Via verify (hard; --core fails on clones)
./tools/verify_mud.sh --core

# Soft opt-out
MUD_DUP_SOFT=1 ./tools/verify_mud.sh --core
./tools/verify_mud.sh --core --dup-soft
```

## Ratchet

| Rung | Policy | Status |
|------|--------|--------|
| **R0** | warn-only `DUP_BLOCK_W`; `--core` never fails on clones | MUD-036 (done) |
| **R1** | `MUD_DUP_HARD=1` fails `--full` only | **skipped** (MUD-039 jumped to R2) |
| **R2 (now)** | hard default on default/fast/core/full; `DUP_BLOCK_E`; soft opt-out | **MUD-039** |

## Findings

```json
{ "code": "DUP_BLOCK_E", "path": "app/…/Foo.kt", "metric": 42, "limit": 10,
  "remediation": "clone of client/…/ClientFoo.kt (42 lines); extract shared apply or thin one side" }
```

`path` is the app file; the client peer is in `remediation`. `DUP_BLOCK_W` is retired (not emitted).

## What this does *not* do

| Non-goal | Owner |
|----------|--------|
| Merge console/GUI handlers into one file | out of scope |
| GameServer / non-`handlers/` trees | later ticket if needed |
| CPD / detekt CopyPaste / new deps | out of scope |
| Allowlist of known twins | forbidden — extract instead |

## Related

- `docs/DOD_SUMMARY.md` — `DUP_BLOCK_E` + optional `gates.duplication_kt`
- `docs/AGENT_QUALITY_GATES_DESIGN.md` — C3
- `config/quality/dod_summary.schema.json` — findings shape (v2); required gate tuple unchanged
