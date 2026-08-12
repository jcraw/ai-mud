# Kotlin token / structure budget (hard-on-touched)

**Ticket:** MUD-028 · **Touched-path:** MUD-029 (done) · **Verify wire:** MUD-030 (done) · **Hard default:** MUD-031 (done)  
**Design:** `docs/AGENT_QUALITY_GATES_DESIGN.md` A6/A7, §7.1–§7.2

## What it is

Measurement of prod Kotlin sources (tokens primary, structure secondary). Standalone checker is always **exit 0** (`exit_policy: report_only`). **`./tools/verify_mud.sh`** owns soft vs hard policy (**MUD-031 hard-on-touched default**).

| Piece | Path |
|-------|------|
| Checker | `tools/quality/check_token_budget_kt.py` |
| Config | `config/quality/token_budget_kt.json` |
| Default JSON out | `tmp/token_budget_kt.json` (optional `--json-out`) |
| Verify side JSON | `tmp/token_budget_kt_verify.json` (gitignored; written by verify) |

## Verify policy (MUD-031 hard-on-touched)

| Lane | Token gate |
|------|------------|
| `default` / `fast` / `core` / `full` | **Run** — **hard-on-touched** by default |
| `quarantine` | **Skip** (debt-only) |
| `pitest` | **Skip** (not in ticket lane set) |
| `--dry-run` | No checker invoke; `gates.token_budget` → `skipped` + dry-run note |

| Mode | How | Exit / gate |
|------|-----|-------------|
| **Hard (default)** | `--git-diff` vs `MUD_TOKEN_GIT_BASE` (default `origin/master`) | **Fail** if any finding code ends `_E` in the touch set; `*_W` never hard-fails; `gates.token_budget` note `E=n W=m scope=touched hard-on-touched` |
| **Soft opt-out** | `MUD_TOKEN_SOFT=1` **or** `--token-soft` | Report-only; merge W+E into `findings[]`; never set verify `EXIT_CODE` from token alone |
| **Hard force** | `MUD_TOKEN_HARD=1` **or** `--token-hard` | Redundant under default hard; still accepted (030 back-compat) |
| **Optional full soft** | `MUD_TOKEN_SCOPE=full` | No `--git-diff` (full-repo inventory); **always soft**. Explicit hard force + full → forced scoped + note |

- Hard only counts **error-tier** codes (`TOKEN_*_E`, `STRUCTURE_*_E`) in the **touch** set.
- Empty touch (docs-only, clean tree): 0 findings, exit 0 under hard.
- Checker crash / missing python or script: soft → skipped/pass + note; hard → fail closed.
- Findings merge capped at ~50 rows (note if truncated).
- Untracked new `.kt` still **not** in git-diff (MUD-029); stage or pass `--files` outside verify.

```bash
# Hard-on-touched default (clean tree → pass; touch over-budget prod kt → fail)
./tools/verify_mud.sh --core

# Soft opt-out (report-only)
MUD_TOKEN_SOFT=1 ./tools/verify_mud.sh --fast
./tools/verify_mud.sh --core --token-soft

# Soft full-repo inventory (noisy; soft only)
MUD_TOKEN_SCOPE=full ./tools/verify_mud.sh --fast
```

## Run (standalone checker)

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

Always **exit 0** (even with error-threshold breaches). `exit_policy: report_only`. Verify owns hard.

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

## Overrides (MUD-031)

`config/quality/token_budget_kt.json` → `overrides` map. Known god files carry temporary higher caps with a **required** burn-down ticket (umbrella **MUD-034** until per-file split tickets land).

```json
"overrides": {
  "path/to/God.kt": {
    "ticket": "MUD-034",
    "tokens": {
      "file": { "warn": 2000, "error": 14209 },
      "function": { "warn": 200, "error": 800 }
    },
    "structure": {
      "file_loc": { "warn": 700, "error": 1100 },
      "fn_loc": { "warn": 55, "error": 120 },
      "cyclo": { "warn": 10, "error": 20 },
      "cognitive": { "warn": 15, "error": 30 }
    }
  }
}
```

| Rule | Behavior |
|------|----------|
| **Ticket required** | `ticket` must match `MUD-\d+`; missing/invalid → entry ignored + stderr note |
| **Apply** | Path caps merge over global before threshold checks |
| **Error compare** | Override error-tier: `metric > limit` (measured grandfather size holds). Global: `metric >= limit` |
| **New-file ban** | Git **Added** or missing at base → override **ignored** (anti same-PR grandfather) |
| **Caps may only lower** | Never raise override caps without a ticket; prefer lowering as splits land (AGENTS + DESIGN) |
| **Untracked** | Still invisible to git-diff; stage new `.kt` so verify sees them |

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

**`override_candidates`:** files over global file-token **error** and/or file-LOC **error** (still listed when under override for burn-down visibility).

Per-file function findings are capped (top severity) to limit noise; all file-level findings are kept.

## Heuristics (not full Kotlin parse)

| Concern | Approach | Caveat |
|---------|----------|--------|
| Function spans | Regex `fun` / modifiers + brace match on comment/string-stripped text | Misses some expression-body `=`, nested generics, unusual syntax |
| LOC | Non-blank lines | Includes comments |
| Cyclomatic | Base 1 + `if`/`when`/`for`/`while`/`catch`/`&&`/`\|\|` counts | Not Detekt PSI |
| Cognitive | Same keywords with nesting bonus via braces | Approximate |

Good enough for agent feedback and burn-down lists; not a compiler frontend.

## What this does *not* do

| Non-goal | Owner |
|----------|--------|
| God-file product splits | MUD-034+ (Wave Q3) |
| Auto-include untracked files | agents pass `--files` / stage |
| Checker non-zero exit | stays report_only; verify owns hard |
| Full-repo hard fail | never; scoped touch only |

## Related

- `docs/DOD_SUMMARY.md` — reserved `TOKEN_*` / `STRUCTURE_*` codes
- `docs/AGENT_QUALITY_GATES_DESIGN.md` — A6/A7 catalog + §7 thresholds
- `config/quality/dod_summary.schema.json` — findings shape (v2)
