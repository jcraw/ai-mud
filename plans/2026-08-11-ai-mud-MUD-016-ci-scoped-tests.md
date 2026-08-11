# MUD-016 Plan — CI workflow compile + scoped tests on PR

**Ticket:** MUD-016 · **Worker:** grok · **Phase:** APPROVED by Astra 2026-08-11 02:00 MST  
**Impl = fresh session** after Astra approve. Do not resume plan session for product edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-016-ci-scoped-tests.md`  
**Worker mirror:** `tmp/workers/MUD-016/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` (+ workflow present / dry sanity)  
**Jason push OK:** plan in-tree only; first workflow push = overnight allowlist (not this plan session).

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | GHA runs compile + core/fast verify on PR/push | Single workflow `.github/workflows/verify.yml`: job runs `./tools/verify_mud.sh --core` on `pull_request` + `push` to `master` (and `main` for parity) |
| 2 | No secret leakage; no live OpenAI in CI unit path | No `secrets.*`, no `OPENAI_API_KEY`, no `local.properties` commit; unit path is offline (verify `--core` never needs API) |
| 3 | Quarantine excluded from **required** check | Required job = `--core` only (default `excludeTags("quarantine")`); never `--quarantine` as required |
| 4 | Badge/README note optional | **Recommend skip badge** day-one; optional 1–2 line README “CI” blurb under Development → Verify if desired (½-screen max) |

---

## 2. Current inventory

| Item | Truth |
|------|--------|
| **CI host** | **None.** No `.github/`, no Circle/Jenkins/Travis/Azure/etc. First workflow file. Repo: `jcraw/ai-mud`, default branch **`master`**. |
| **Verify lanes (MUD-004)** | `default/fast` = compile smoke + detekt + Konsist + test-lock (no unit suite). **`core`** = `:core/:perception/:memory/:reasoning:test` + same gates (exclude quarantine). `full` = stable green + compile-only extras. `quarantine` = debt only (hard-fail OK). `pitest` = local/nightly only. |
| **Java** | Toolchain **17** (`buildSrc` `jvmToolchain(17)`). **Risk:** committed `gradle.properties` sets `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk` (machine path) — **breaks GHA Temurin** unless overridden/removed at impl. |
| **Quarantine (MUD-008)** | 23 `@Tag("quarantine")` on `:reasoning`; green lanes exclude by default. Docs: `docs/TEST_QUARANTINE.md`. Repair → MUD-017. |
| **Secrets surface** | `local.properties` gitignored; runtime reads `OPENAI_API_KEY` / `openai.api.key` in app/client/testbot only — **not** on `--core` path. Never inject secrets in YAML. |
| **Doc pointers** | `docs/DETEKT.md` / `KONSIST.md` / `TEST_LOCK.md` already say “CI → MUD-016”. No workflow yet. |

---

## 3. Design / recommended approach

### Workflow (lock for Astra)
- **File:** `.github/workflows/verify.yml` (name: `Verify`)
- **Triggers:**
  - `pull_request` (default types) targeting `master` / `main`
  - `push` to `master` / `main`
  - optional `workflow_dispatch` for manual re-run (cheap; include)
- **Permissions:** `contents: read` only (no write, no `id-token`, no packages)
- **Concurrency (optional, recommended):** group `verify-${{ github.ref }}`, `cancel-in-progress: true` to save minutes on force-push/amend

### Required job: `core`
```
runs-on: ubuntu-latest
steps:
  1. actions/checkout@v4
  2. actions/setup-java@v4  — Temurin 17, distribution temurin
  3. gradle/actions/setup-gradle@v4  — cache deps/wrapper (no credential caches)
  4. ./tools/verify_mud.sh --core
```
- **Why `--core` not bare `--fast`:** ticket wants compile **+** core/fast unit green; bare `--fast` is compile-smoke only (weaker PR signal). `--core` = honest unit green + detekt + Konsist + test-lock; still excludes quarantine.
- **Fail closed:** non-zero exit fails the job; logs surface in Actions UI as-is (no secret echo).
- **Env:** do **not** set `OPENAI_API_KEY`. Prefer no `local.properties` on runner.
- **Java home fix (required for green):** at impl, either (A) **remove** machine-specific `org.gradle.java.home` from committed `gradle.properties` (prefer; toolchain + setup-java enough; locals can use `~/.gradle/gradle.properties`), or (B) workflow step `echo "org.gradle.java.home=$JAVA_HOME" >> gradle.properties` / `-Dorg.gradle.java.home=$JAVA_HOME`. **Recommend (A)** — keeps repo portable.

### Optional / non-required
- **No second job day-one.** Skip `--full` job unless free; PIT/`--pitest` never in required CI. Quarantine never required.
- **Badge:** skip unless impl has 30s free; if added, one line under README Development pointing at `verify.yml` Actions badge for `master`.

### Secrets posture
- No `secrets:` block, no `env: OPENAI_*`, no commit of keys/db/logs.
- Grep workflow for `sk-`, `OPENAI`, `password`, `token` before closeout.
- Do not run testbot / live LLM scenarios in CI.

### Docs
- Optional README 1–2 lines: “PRs run GitHub Actions `verify.yml` → `./tools/verify_mud.sh --core` (quarantine excluded).”
- AGENTS: **no** expansion unless one link needed; already has Verification table.
- Do **not** edit DETEKT/KONSIST/TEST_LOCK beyond existing MUD-016 pointers (already present).

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `.github/workflows/verify.yml` | **Create** — sole workflow |
| `gradle.properties` | **Touch only if needed** — strip/fix machine `org.gradle.java.home` so CI + toolchain work |
| `README.md` | **Optional** — 1–2 line CI note and/or badge under Development |
| Product `*.kt` / `src/test/**` / `tools/verify_mud.sh` / test-lock manifest | **Do not touch** |

---

## 5. Non-goals

- MUD-007 playtest, MUD-009 dirty-tree/PR policy, MUD-017 quarantine repair, MUD-018 CLAUDE/CODEX deprecation
- PIT / `--pitest` / `--quarantine` / `--full` as required checks
- Live OpenAI, testbot CI, secret store provisioning, deploy/release workflows
- Multi-OS matrix, Android/iOS, coverage upload (Codecov etc.)
- Force-push, commits of secrets, weakening verify lanes, deleting quarantine tags to green CI
- Branch-protection rules setup (Jason later once workflow exists and has run)

---

## 6. How impl confirms acceptance

- [ ] `.github/workflows/verify.yml` exists; triggers include PR + push to `master` (and `main`)
- [ ] Required job runs `./tools/verify_mud.sh --core` (not `--quarantine`, not bare `--fast` alone)
- [ ] `rg -n 'OPENAI|secrets\.|sk-|api[_-]?key|password|token' .github/workflows/` → empty / no secrets
- [ ] Workflow YAML has no `local.properties` commit step; permissions ≤ `contents: read`
- [ ] Quarantine: required path uses excludeTags via `--core`; no required `--quarantine` job
- [ ] Local: `./tools/verify_mud.sh --core` exit 0 after any `gradle.properties` fix (same command CI will run)
- [ ] Optional: README CI blurb/badge ≤½ screen
- [ ] No product `.kt` / unauthorized `src/test` / test-lock churn

---

## 7. Ordered impl steps

1. Confirm still no other CI; default branch `master`.
2. Fix Java portability: remove machine-only `org.gradle.java.home` from committed `gradle.properties` (or document CI override) so Temurin 17 works.
3. Add `.github/workflows/verify.yml` per §3 (checkout → Java 17 Temurin → setup-gradle → `./tools/verify_mud.sh --core`).
4. Sanity: `bash -n` / YAML structure check; grep secrets; confirm quarantine not required.
5. Run `./tools/verify_mud.sh --core` locally (exit 0).
6. Optional README one-liner under Development → Verify.
7. Closeout note: paths, verify result, residual risk (first Actions run needs push; branch protection later). **No push from impl unless Jason allowlist.**

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| **Cold Gradle cache / CI minutes** | `setup-gradle` cache; single job; concurrency cancel-in-progress; no matrix |
| **`org.gradle.java.home` machine path** | Strip from committed props (prefer) or override on runner before verify |
| **Dirty tree unrelated files** | Impl only touches workflow (+ props/README as above); do not stage product/MUD-007 noise |
| **First workflow + branch protection** | Ship file first; Jason enables required check after one green run on `master` |
| **Flaky/env fail on runner** | N=3 local/CI re-run then escalate; no auto-retry in workflow day-one |
| **Quarantine leak into required** | Job must call `--core` only; never drop excludeTags / never required `--quarantine` |
| **Push access** | Plan/impl in-tree; remote first-run needs Jason overnight allowlist — not a plan blocker |

---

**Handoff:** Astra common-sense approve → **fresh** impl brief (`issues/_templates/implement-brief.md` → `tmp/workers/MUD-016/`). This plan session **stops** here.
