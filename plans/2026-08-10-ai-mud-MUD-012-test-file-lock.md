# MUD-012 Plan — Test-file lock / allowlist (anti test-gaming)

**Ticket:** MUD-012 · **Worker:** grok · **Phase:** plan_review  
**Impl = fresh session** after Astra/Jason approve. This plan turn is not resumed for product edits.  
**Plan path:** `plans/2026-08-10-ai-mud-MUD-012-test-file-lock.md`  
**Worker mirror:** `tmp/workers/MUD-012/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh` (default/fast hard-fail unauthorized `src/test/**` edits)

Status: **APPROVED by Astra** (2026-08-10 ~22:35 AZ) — fresh IMPL authorized; do not resume plan session

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Mechanism blocks unauthorized `src/test/**` edits | Committed SHA-256 **manifest** of tracked test files; verify hard-fails on drift/missing baseline |
| 2 | Escape hatch documented | `MUD_ALLOW_TEST_CHANGES=1` and/or `./tools/test_lock.sh --write` (optional `-PallowTestChanges` pass-through doc only); never silent regen |
| 3 | AGENTS.md DoD bullet | One bullet: no test edits without explicit ticket/auth + lock regen |
| 4 | Fast verify fails closed | Replace `maybe_stub "testFileLock"` with hard step on default/fast/core/full; **skip quarantine** (like detekt/Konsist) |

---

## 2. Current inventory

| Item | Status |
|------|--------|
| Verify stub | L234: `maybe_stub "testFileLock" "MUD-012"` always SKIP; header L5 notes test-lock stub |
| Lock tooling | **None** — no `tools/test_lock.sh`, no baseline dir |
| Test roots | **8** modules: `app client core memory perception reasoning testbot utils` (`*/src/test`) |
| Tracked test files | **~107** under `*/src/test/*` (mostly `.kt`; small set — always-hash is cheap) |
| AGENTS gap | DoD has no “no unauthorized test edits” bullet; Verification still says “PIT/test-lock remain stubs” |
| Prior art | MUD-010 detekt baseline (explicit regen only); MUD-011 hard verify step pattern |
| DIGEST | Pointer only: `docs/research/DIGEST-025-kotlin-quality-gates-POINTER.md` (gate catalog #2) |

---

## 3. Design / recommended approach

**Choice: content-hash manifest gate in verify** (not Gradle property alone, not git-hook-primary).

**Why**
- Durable without dirty-git assumptions (agents can stage/unstage; uncommitted + committed drift both fail).
- Same path for local drains + future CI (MUD-016 calls verify).
- Fail-closed if baseline missing.
- ~107 files → full rehash is fast enough; no need for dirty-only shortcut as primary (optional micro-opt later).

**Mechanism**
1. Baseline: `tools/test-lock/manifest.sha256` — lines `sha256  path` (sorted paths; POSIX `sha256sum`/`shasum` style), **tracked files only** via `git ls-files` matching `*/src/test/*` (covers kotlin/java/resources under production modules).
2. Scope: **all** tracked `**/src/test/**` (default lock everything). Exclude only `tmp/`, build outputs (never in `git ls-files`). No quarantine path carve-out.
3. Check script: `tools/test_lock.sh`  
   - default / `--check`: rehash current tree for listed paths + detect **new** tracked test paths not in manifest + **missing** paths still in manifest → exit 1 with clear paths.  
   - Fail if baseline file absent.  
   - `--write`: rewrite baseline **only** if `MUD_ALLOW_TEST_CHANGES=1` (or `ALLOW_TEST_CHANGES=1`); else refuse. Document Gradle `-PallowTestChanges` as synonym agents may set before verify (verify maps env from property if present, or docs say “set env”).
4. **Verify hook** (`tools/verify_mud.sh`): on lanes `!= quarantine`, run `./tools/test_lock.sh --check` as hard step (`run_*` / same fail path as detekt); remove `maybe_stub "testFileLock"`. Leave `pitest` stub. Update header/help/usage one-liners.
5. **Dirty-tree note:** check is content vs baseline, not `git status`. Unrelated dirty product code does **not** false-positive. Editing tests without `--write` + committed baseline update → fail.
6. **Untracked tests:** `git ls-files` only — brand-new untracked test files **won’t** enter check until `git add`. Mitigation (small, in-scope): also fail if `git status --porcelain` shows untracked paths matching `src/test/` (optional second clause; **prefer include** so agents can’t bypass by leaving tests untracked). Document in `docs/TEST_LOCK.md`.
7. **AGENTS / docs:** DoD bullet + Verification row/pointer; short `docs/TEST_LOCK.md` (check, write, env, when tickets may authorize).

**Not chosen as primary**
- Git hook only — easy to skip (`--no-verify`); not shared with CI.
- Gradle-only property — agents skip by not passing property; no content truth.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/test_lock.sh` | **Create** — check/write, env gate, clear errors |
| `tools/test-lock/manifest.sha256` | **Create** — initial baseline (`--write` once under allow env in impl) |
| `tools/verify_mud.sh` | **Edit** — hard step; drop stub; header/help |
| `docs/TEST_LOCK.md` | **Create** — ops doc (~1 screen) |
| `AGENTS.md` | **Edit** — DoD bullet + Verification note (test-lock live; PIT still stub) |
| Ticket / BOARD | Impl session closeout only (plan turn: plan_review bookkeeping) |

No `*.kt` product/test rewrites. No detekt/Konsist/PIT changes beyond stub removal for testFileLock.

---

## 5. Non-goals

- MUD-007 GUI, MUD-009 git hygiene, MUD-013 dod-json, MUD-014 PIT, MUD-015 PBT, MUD-016 CI YAML (note: CI will call same verify later)
- Mass test rewrites / quarantine clear (MUD-017)
- Detekt/Konsist rule changes
- Product/gameplay code
- Silent baseline regen; force-push; commit secrets

---

## 6. How impl confirms acceptance

**Checklist**
- [ ] Baseline committed; missing baseline → verify fail
- [ ] Clean tree + matching hashes → default/fast **PASS** (lock step green)
- [ ] Intentional fail: touch one tracked test file content → `./tools/verify_mud.sh` (or `--fast`) **FAIL** with path named
- [ ] Escape: `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` updates baseline; without env, `--write` refuses
- [ ] Untracked `*/src/test/**` file (if second clause shipped) fails check
- [ ] `--quarantine` does **not** run lock (and does not soft-pass via stub)
- [ ] AGENTS DoD + `docs/TEST_LOCK.md` present
- [ ] `maybe_stub "testFileLock"` gone

**Commands**
```bash
./tools/verify_mud.sh              # green after baseline
./tools/verify_mud.sh --fast
./tools/verify_mud.sh --core
./tools/verify_mud.sh --full
# demo fail: echo // >> core/src/test/.../SomeTest.kt && ./tools/verify_mud.sh ; restore file
./tools/verify_mud.sh --quarantine # no test-lock step
./tools/verify_mud.sh --dry-run    # shows check (not SKIP stub)
```

---

## 7. Ordered impl steps

1. Author `tools/test_lock.sh` (`--check` / `--write`, env gate, untracked test-path porcelain clause, missing-baseline fail).
2. Generate `tools/test-lock/manifest.sha256` under allow env; commit path ready for impl commit later.
3. Wire `verify_mud.sh`: hard check on non-quarantine lanes; remove testFileLock stub; refresh header/help/AGENTS pointer language in script comments.
4. Add `docs/TEST_LOCK.md`.
5. Patch `AGENTS.md` DoD + Verification (one bullet + “test-lock live”).
6. Self-check: green verify; intentional dirty test fail; restore; dry-run; quarantine skip.
7. Closeout note in worker dir; ticket → done only after approve+impl+verify (not this plan turn).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Dirty tree false positives | Hash vs baseline, not whole-repo dirty; product edits OK |
| Large baseline churn | Only ~107 files; regen only when ticket **authorizes** test edits |
| Bypass via untracked tests | Porcelain untracked `src/test` fail clause |
| Bypass via deleting tests from git index | Manifest still lists path → missing file fails check |
| Agents set allow env always | Doc + DoD: only when ticket scopes tests; still need committed baseline change in PR (reviewable) |
| Performance | Full hash of ~107 small files; keep under ~1s; no Gradle spin for lock |
| Platform sha tools | Prefer `sha256sum` then `shasum -a 256` fallback |
| Parallel tickets editing tests | Serial one-builder; baseline merge conflicts visible |

---

**Handoff:** Astra/Jason approve this plan → **fresh impl session** with implement-brief. Plan session stops here.
