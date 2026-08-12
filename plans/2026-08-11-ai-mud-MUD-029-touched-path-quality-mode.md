# MUD-029 Plan — Touched-path mode for token/structure checks

**Ticket:** MUD-029 · **Worker:** grok · **Phase:** implementing  
**Status:** **APPROVED by Astra 2026-08-11 23:53 MST** (common-sense). Fresh IMPL only — do **not** resume plan session.  
**Plan:** `plans/2026-08-11-ai-mud-MUD-029-touched-path-quality-mode.md` · **Mirror:** `tmp/workers/MUD-029/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` · **Depends:** MUD-028 (done+pushed `1e93751`)  
**Handoff:** APPROVED → fresh impl + implement-brief. **STOP** — no implementation in plan session.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Plan delivers |
|---|------------|---------------|
| 1 | `--files` and/or `--git-diff` | CLI path scope; default base **`origin/master`**, override `--git-base REF` |
| 2 | Prod `.kt` only unless flagged | Reuse MUD-028 `*/src/main/**/*.kt` filter; optional `--include-tests` only if cheap |
| 3 | JSON findings ⊂ touched set | Analyze only resolved paths; empty touch → empty findings |
| 4 | Report-only | Keep `exit_policy: report_only`, exit **0**; reserve 030/031 (no invent hard fail) |
| 5 | Docs + `--help` | `docs/TOKEN_BUDGET_KT.md` + argparse help |
| 6 | `--core` green | No product `*.kt`; no verify hard-wire |
| 7 | No product `*.kt` | Tooling/docs/bookkeep only |

---

## 2. Current inventory (MUD-028)

| Piece | State |
|-------|--------|
| `tools/quality/check_token_budget_kt.py` (~734 LOC) | Full-repo `discover_kt_files(root)`; CLI: `--root/--config/--json-out/--quiet-stdout`; always exit 0 |
| `config/quality/token_budget_kt.json` | Thresholds + empty `overrides{}` |
| `docs/TOKEN_BUDGET_KT.md` | Run/scope/JSON; lists 029 as future “Touched-only” |
| Envelope | `tool`, `exit_policy: report_only`, `summary.files_scanned`, `findings[]`, `override_candidates[]` |
| Verify | **Not** wired (030); hard-on-touched (031) later |
| Design | A6/A7 hard-on-**touched** is Q2; this ticket is Q1 path scope only |

**Jam (process only):** many jam tools use `--files nargs="+"`; no Godot path copy. Invent `--git-diff` for agents.

---

## 3. Design / recommended approach

**Minimal delta:** path resolver → intersect/filter → existing `analyze_file` loop. No new config schema required.

### CLI
```
--files PATH [PATH ...]     # explicit (repo-rel or under --root); repeatable OK
--git-diff                   # enable git name-only mode
--git-base REF               # default: origin/master
# If neither --files nor --git-diff: keep full-repo discover (backward compatible)
```
- **Both** `--files` + `--git-diff`: **union**, then filter.
- **`--include-tests`:** optional; default **off** (prod `src/main` only). If skipped for KISS, document “prod only” and drop flag (acceptance: “unless flagged” satisfied by documenting no flag = prod-only).

### Default git base
- **Default:** `origin/master` (matches board push target / ticket option).
- **Override:** `--git-base HEAD~1` / branch / SHA.
- **Missing base:** if `git rev-parse --verify origin/master` fails → try `master`, then `HEAD~1`; print **one stderr warning**; still exit 0. Document in help + TOKEN_BUDGET_KT.

### Git path collection
```bash
# Working tree + index content vs base (tracked); untracked NOT included
git -C <root> diff --name-only --diff-filter=ACMR <base>
# Plus committed range on branch tip if working tree clean of WIP vs HEAD only?
# Prefer single: git diff --name-only --diff-filter=ACMR <base>   # vs working tree
```
- **Renames:** `ACMR` + name-only shows **new** path (skip missing on-disk).
- **Deletes:** filtered out (not on disk → skip).
- **Untracked new `.kt`:** **not** in git diff → agents pass `--files path/to/New.kt` (document). Optional stretch: `git ls-files --others --exclude-standard` **only if** trivial; default omit to keep KISS.
- **Non-kt / docs / tools noise:** drop after filter.

### Path filter (align 028)
Accept path iff relative path matches prod pattern:
- contains `src/main` segment and ends `.kt`
- not under `src/test`, `build/`, `buildSrc/`, `.git`, etc. (reuse skip set)
- resolve under `--root`; reject escapes outside root

### JSON when empty touch set
- `files_scanned: 0`, `findings: []`, `override_candidates: []`, modules `[]`
- Add summary fields (optional, schema-free report tool):  
  `scope: "full" | "touched"`, `git_base` (if used), `touched_input_count`, `touched_prod_kt_count`
- Still **exit 0**, `exit_policy: report_only`
- Quiet line: `files=0 … scope=touched`

### Hard-fail reservation (do not implement)
- Leave comments only: future `MUD_TOKEN_HARD` / hard-on-touched → 030/031.
- **Do not** change exit on E findings in this ticket.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/quality/check_token_budget_kt.py` | **Edit** — argparse + resolve_touched + wire `main` scan list; small helpers (~80–120 LOC) |
| `docs/TOKEN_BUDGET_KT.md` | **Edit** — flags, default base, empty-scope, untracked note; mark 029 done |
| Ticket / BOARD / OVERNIGHT | plan_review → later impl bookkeep |

**No:** product `*.kt`, `verify_mud.sh` wire, config threshold changes, test-lock, detekt, commit/push.

---

## 5. Non-goals

- Hard-on-touched default (**031**)
- Verify wire / `MUD_TOKEN_HARD` pilot (**030**)
- God splits / override auto-fill
- Detekt touched-ignore-baseline
- Full Kotlin PSI / new Gradle plugin
- git commit/push

---

## 6. How impl confirms acceptance

Checklist:
- [ ] `--help` documents `--files`, `--git-diff`, `--git-base` (default `origin/master`)
- [ ] `--files core/.../Foo.kt` → findings paths only under that file (or empty if clean)
- [ ] Non-prod path in `--files` (e.g. `docs/x.md` or `*/src/test/*`) → skipped; files_scanned reflects prod only
- [ ] `--git-diff` on clean tree vs base with no prod kt → `files_scanned=0`, findings `[]`, exit 0
- [ ] Full-repo (no scope flags) still works as 028
- [ ] Exit always 0 with E breaches on scoped god file
- [ ] Docs updated
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] `git diff --stat` shows no product `*.kt`

Smoke:
```bash
python3 tools/quality/check_token_budget_kt.py --help
python3 tools/quality/check_token_budget_kt.py --root . --files \
  core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt --quiet-stdout --json-out tmp/token_budget_kt_touched.json
python3 tools/quality/check_token_budget_kt.py --root . --git-diff --git-base origin/master \
  --quiet-stdout --json-out tmp/token_budget_kt_git.json
# empty / nonsense path
python3 tools/quality/check_token_budget_kt.py --root . --files docs/TOKEN_BUDGET_KT.md --quiet-stdout
./tools/verify_mud.sh --core
```

---

## 7. Ordered impl steps

1. Add helpers: `is_prod_main_kt(rel)`, `normalize_paths(root, raws)`, `git_touched_paths(root, base)` (subprocess `git`, handle rc≠0 → stderr + empty list, exit 0).
2. Extend `parse_args` + `main`: resolve file list (full | files | git | union) → filter → existing analyze loop.
3. Envelope summary: `scope` + optional git metadata; empty-set behavior.
4. Update `docs/TOKEN_BUDGET_KT.md` + ensure `--help` text is enough for agents.
5. Smoke commands above; `./tools/verify_mud.sh --core`.
6. Closeout: paths, smoke, dod-summary path, residual risks; ticket → done in **fresh** impl session only.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Empty diff (clean tree) | Valid: 0 files, empty findings, exit 0; document |
| `origin/master` missing (shallow/no remote) | Fallback `master` → `HEAD~1` + stderr warn |
| Renames | `--diff-filter=ACMR`; analyze new path if exists |
| Untracked new files | Document `--files`; optional ls-others stretch only if free |
| Non-kt noise in git | Filter to prod main `.kt` |
| Path outside root / absolute vs rel | Resolve + must stay under root |
| Accidental hard-fail | Do not touch exit path; leave 030/031 comments only |
| Merge-base confusion | Use `git diff base` (worktree vs tree), not three-dot unless documented; prefer simple two-arg form |

---

**STOP.** Plan only. No product edits. Approve → fresh impl brief under `tmp/workers/MUD-029/`.
