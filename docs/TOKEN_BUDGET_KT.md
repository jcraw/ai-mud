# Kotlin token / structure budget (report-only)

**Ticket:** MUD-028 · **Touched-path:** MUD-029 (done) · **Hard fail:** MUD-031 · **Verify wire:** MUD-030  
**Design:** `docs/AGENT_QUALITY_GATES_DESIGN.md` A6/A7, §7.1–§7.2

## What it is

Report-only measurement of prod Kotlin sources before hard ceilings. Does **not** fail CI or `./tools/verify_mud.sh` yet.

| Piece | Path |
|-------|------|
| Checker | `tools/quality/check_token_budget_kt.py` |
| Config | `config/quality/token_budget_kt.json` |
| Default JSON out | `tmp/token_budget_kt.json` (optional `--json-out`) |

## Run

```bash
# Full-repo prod scan (MUD-028 default)
python3 tools/quality/check_token_budget_kt.py \
  --root . \
  --config config/quality/token_budget_kt.json \
  --json-out tmp/token_budget_kt.json

# Touched paths only — explicit files (MUD-029)
python3 tools/quality/check_token_budget_kt.py \
  --root . --files core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt \
  --quiet-stdout --json-out tmp/token_budget_kt_touched.json

# Touched paths only — git working tree vs base (default base: origin/master)
python3 tools/quality/check_token_budget_kt.py \
  --root . --git-diff --git-base origin/master \
  --quiet-stdout --json-out tmp/token_budget_kt_git.json
```

Always **exit 0** (even with error-threshold breaches). `exit_policy: report_only`.

Useful flags:

| Flag | Meaning |
|------|---------|
| `--root` | Repo root (default `.`) |
| `--config` | Thresholds JSON |
| `--json-out PATH` | Write full report |
| `--quiet-stdout` | One-line summary on stdout (still writes `--json-out`) |
| `--files PATH [PATH …]` | Explicit paths (repo-rel or under `--root`); prod main `.kt` only |
| `--git-diff` | Touched tracked files vs git base (`git diff --name-only --diff-filter=ACMR <base>`) |
| `--git-base REF` | Base for `--git-diff` (default: **`origin/master`**) |

### Path scope (MUD-029)

| Mode | How | Summary `scope` |
|------|-----|-----------------|
| Full-repo | Neither `--files` nor `--git-diff` | `full` |
| Touched | `--files` and/or `--git-diff` | `touched` |

- **Both** `--files` and `--git-diff`: **union**, then prod filter.
- Only **existing** prod `*/src/main/**/*.kt` under `--root` are analyzed.
- Paths outside `--root` are rejected (stderr note); non-prod / missing paths skipped.
- **Empty touch set** (clean tree, only docs/tests, etc.): `files_scanned: 0`, empty `findings` / `override_candidates`, still exit **0**. Summary may include `git_base`, `touched_input_count`, `touched_prod_kt_count`.
- **Git base missing:** try requested ref → `master` → `HEAD~1`; **one** stderr warning; still exit 0.
- **Untracked new `.kt`:** **not** in `git diff` → pass `--files path/to/New.kt` (no auto-include).
- Deletes filtered out (`ACMR`); renames use the new path if on disk.

## Scope

- Includes: `*/src/main/**/*.kt` (all modules under root, or the touched subset)
- Excludes: `src/test/**`, `build/`, `buildSrc/`, `.git`, `.gradle`, `tmp/`
- No `--include-tests` flag: prod main only (KISS)

## Token formula

```
tokens = max(0, (len(source) + 3) // 4)   # ≡ ceil(chars / 4)
```

- Raw UTF-8 source length (no comment strip for **token** count — matches jam / OpenClaw convention).
- File tokens: whole file. Function tokens: heuristic span (see below).

## Thresholds (from DESIGN)

| Surface | Warn | Error | Code prefix |
|---------|------|-------|-------------|
| File tokens | 2000 | 2500 | `TOKEN_FILE_W` / `TOKEN_FILE_E` |
| Function tokens | 200 | 250 | `TOKEN_FN_W` / `TOKEN_FN_E` |
| File LOC | 700 | 1100 | `STRUCTURE_FILE_LOC_*` |
| Fn LOC | 55 | 90 | `STRUCTURE_FN_LOC_*` |
| Cyclomatic (heuristic) | 10 | 16 | `STRUCTURE_CYCLO_*` |
| Cognitive (heuristic) | 15 | 25 | `STRUCTURE_COGNITIVE_*` |

Tokens are **primary**; structure metrics are **secondary**.

## JSON envelope

```json
{
  "tool": "check_token_budget_kt",
  "exit_policy": "report_only",
  "summary": {
    "files_scanned": 0,
    "modules": [],
    "findings_total": 0,
    "override_candidates": 0,
    "scope": "touched",
    "git_base": "origin/master",
    "touched_input_count": 0,
    "touched_prod_kt_count": 0
  },
  "findings": [
    { "code": "TOKEN_FILE_E", "path": "…", "metric": 3200, "limit": 2500, "remediation": "…" }
  ],
  "override_candidates": [
    { "path": "…", "file_tokens": 0, "file_loc": 0, "reasons": ["file_tokens=…>=2500"] }
  ]
}
```

Finding shape matches dod-summary v2 (`code`, `path`, `metric`, `limit`, `remediation`). Function paths use `file.kt:line` (optional `name`).

**`override_candidates`:** files over file-token **error** and/or file-LOC **error**. Config `overrides: {}` stays empty until a burn-down ticket (MUD-031/034); **do not auto-fill**.

Per-file function findings are capped (top severity) to limit noise; all file-level findings are kept.

## Heuristics (not full Kotlin parse)

| Concern | Approach | Caveat |
|---------|----------|--------|
| Function spans | Regex `fun` / modifiers + brace match on comment/string-stripped text | Misses some expression-body `=`, nested generics, unusual syntax |
| LOC | Non-blank lines | Includes comments |
| Cyclomatic | Base 1 + `if`/`when`/`for`/`while`/`catch`/`&&`/`\|\|` counts | Not Detekt PSI |
| Cognitive | Same keywords with nesting bonus via braces | Approximate |

Good enough for agent feedback and burn-down lists; not a compiler frontend.

## Overrides policy

See DESIGN §7.1:

- Temporary higher caps only for listed gods during split wave
- Each override **requires** a burn-down ticket id
- New files: no override privilege — must meet target
- Never raise caps without ticket; prefer lowering only

This ticket only **lists candidates**; it does not write overrides.

## What this ticket does *not* do

| Non-goal | Owner |
|----------|--------|
| Hard fail on breaches / hard-on-touched | MUD-031 |
| Wire into `verify_mud.sh` / `MUD_TOKEN_HARD` | MUD-030 |
| God-file product splits | later Q3 tickets |
| Merge into `tmp/dod-summary.json` | MUD-030 (verify owns writer) |
| Auto-include untracked files | agents pass `--files` |

## Related

- `docs/DOD_SUMMARY.md` — reserved `TOKEN_*` / `STRUCTURE_*` codes
- `docs/AGENT_QUALITY_GATES_DESIGN.md` — A6/A7 catalog + §7 thresholds
- `config/quality/dod_summary.schema.json` — findings shape (v2)
