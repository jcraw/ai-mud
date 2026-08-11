# MUD-010 Plan — Detekt + baseline (new-code hard, legacy soft)

**APPROVED by Astra** · 2026-08-10 ~21:15 AZ · common-sense OK (Gradle detekt + shared baseline, hard new smells, verify real not stub, regen Jason/explicit only; no mass smell fixes / no scope creep into 011–016).

**Ticket:** MUD-010 · **Worker:** grok · **Phase:** implementing  
**Impl = fresh session** after Astra/Jason approve. This plan turn is not resumed for product edits.  
**Plan path:** `plans/2026-08-10-ai-mud-MUD-010-detekt-baseline.md`  
**Worker mirror:** `tmp/workers/MUD-010/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh` (default/fast must run real detekt, not stub)

Status: APPROVED by Astra 2026-08-10 21:15 MST

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Detekt wired in Gradle | Plugin via version catalog + applied in `kotlin-jvm` convention |
| 2 | Baseline for existing issues | Committed `config/detekt/baseline.xml` (soft on legacy) |
| 3 | New code hard-fails | Baseline + `buildUponDefaultConfig`; findings not in baseline fail task |
| 4 | Fast verify runs detekt | Replace `maybe_stub "detekt"` with hard `run_gradle detekt` (fail on new) |
| 5 | Document baseline regen | Short `docs/DETEKT.md` + one AGENTS Verification line; mass regen = Jason/explicit only |

---

## 2. Current inventory

| Item | Status |
|------|--------|
| Detekt | **None.** `rg` shows no detekt ids/versions; only generic `plugins {` in modules |
| Verify | `tools/verify_mud.sh` L216: `maybe_stub "detekt" "MUD-010"` always SKIP |
| Convention | `buildSrc/.../kotlin-jvm.gradle.kts` — kotlin jvm + JUnit Platform quarantine filters only |
| Catalog | `gradle/libs.versions.toml` — kotlin **2.2.0**; no detekt entry |
| Modules | All product modules use `id("buildsrc.convention.kotlin-jvm")`: app, utils, llm, core, config, perception, reasoning, memory, action, testbot, **client** (also Compose Desktop 1.7.3) |
| Gradle | **8.14**; Java 17 toolchain |
| Deps | MUD-004 + MUD-008 **done** |
| Non-goal surface | Konsist 011, test-lock 012, PIT 014, CI 016; no mass smell fixes |

---

## 3. Design / recommended approach

### Plugin version
- Catalog: `detekt = "1.23.8"` (latest **stable**; compiled vs Kotlin 2.0.21 — usually fine as analysis plugin).
- **Fallback if apply fails on KGP 2.2.0:** `2.0.0-alpha.1` (Kotlin 2.2.x alignment per detekt compatibility table). Prefer stable; document pin chosen in closeout.
- Plugin id: `io.gitlab.arturbosch.detekt`.

### Where applied
- **Primary:** apply + configure inside `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` so every JVM module gets the same gate.
- `buildSrc/build.gradle.kts`: `implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:<ver>")` (or catalog equivalent).
- **Client / CMP:** still on `kotlin-jvm` + Compose Desktop (single JVM target — not KMP hierarchy). Expect detekt to work on `src/main`/`src/test`. If plugin or compose-generated paths fight:
  - first try exclude generated/`build` only (defaults);
  - last resort: `if (project.name != "client")` skip or soft-note + exclude client from verify detekt aggregate — call out in closeout, do not block rest of modules.

### Config + baseline (legacy soft / new hard)
- `config/detekt/detekt.yml` — thin; `buildUponDefaultConfig = true`; **`allRules = false`** (sensible default set, not full experimental noise).
- Optional light overrides only if first baseline is unusable (e.g. max method complexity) — **do not** mass-tune to zero findings.
- `config/detekt/baseline.xml` — **single shared root baseline** for all modules that apply the plugin (simplest regen story).
- Convention snippet (intent):
  - `buildUponDefaultConfig = true`
  - `allRules = false`
  - `parallel = true`
  - `config.setFrom(rootProject.files("config/detekt/detekt.yml"))`
  - `baseline = rootProject.file("config/detekt/baseline.xml")`
  - Source: main (+ test if cheap; prefer **main first** if full detekt is slow)
  - **No** type-resolution on fast path (keeps default/fast honest without full compile graph cost)
- New smells = fail; baselined = soft. **No** mass-fix of legacy in this ticket.

### Verify hook
- Default + fast (and preferably core/full): after lane gradle steps, **hard** `run_gradle detekt` (root multi-project runs all subproject `detekt` tasks).
- Remove `maybe_stub "detekt" "MUD-010"`; leave konsist / testFileLock / pitest stubs.
- Update help text / AGENTS Verification: drop “Detekt stub until later”; state detekt is real on default/fast.
- Dry-run must print the real gradle detekt command, not SKIP stub.

### Docs / regen policy
- **`docs/DETEKT.md`** (short): purpose, config paths, verify behavior, regenerate command, **mass baseline regen = Jason/explicit only** (agents fix new smells or shrink baseline intentionally — not “regenerate to silence”).
- AGENTS Verification: one line + link to `docs/DETEKT.md` (ticket acceptance authorizes this lean AGENTS touch).

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add detekt version (+ optional plugin alias) |
| `buildSrc/build.gradle.kts` | Detekt plugin on classpath |
| `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` | Apply + configure detekt |
| `config/detekt/detekt.yml` | **Create** thin config |
| `config/detekt/baseline.xml` | **Create** via first `detektBaseline` run; commit |
| `tools/verify_mud.sh` | Real detekt step; drop detekt stub; usage note |
| `docs/DETEKT.md` | **Create** regen + policy |
| `AGENTS.md` | Verification table/line only (detekt live) |
| Ticket Resolution (impl) | Paths + verify result |

---

## 5. Non-goals

- Fixing existing Detekt findings (baseline absorbs)
- Konsist (011), test-file lock (012), PIT (014), CI (016)
- Full ktlint/spotless unless detekt formatting subset is already free
- Agent-default mass baseline regeneration
- Product/gameplay; MUD-007 GUI; MUD-009 git hygiene

---

## 6. How impl confirms acceptance

- [ ] `./gradlew detekt` exits 0 on clean tree with committed baseline
- [ ] Introduce temporary smell in a module → `detekt` fails → remove smell → green
- [ ] `./tools/verify_mud.sh` / `--fast` runs detekt (no `SKIP stub: detekt`); fail propagates to exit 1
- [ ] `./tools/verify_mud.sh --dry-run` shows detekt gradle command
- [ ] `docs/DETEKT.md` + AGENTS Verification mention regen policy
- [ ] Baseline + config committed; no product logic rewrites
- Commands: `./tools/verify_mud.sh`, `./tools/verify_mud.sh --fast`, `./gradlew detekt`

---

## 7. Ordered impl steps

1. Catalog version + buildSrc plugin dependency.
2. Apply/configure detekt in `kotlin-jvm` convention; add `config/detekt/detekt.yml`.
3. **First baseline generation** (impl session):
   ```bash
   ./gradlew detektBaseline --continue
   # if multi-module needs explicit:
   # ./gradlew detektBaselineMain --continue
   # ensure config/detekt/baseline.xml exists & is non-empty; commit as intentional soft debt
   ```
4. Confirm `./gradlew detekt` green on baselined tree.
5. Wire `verify_mud.sh`: hard detekt on default/fast (and note on core/full); remove detekt stub.
6. Smoke: temporary new finding fails; revert.
7. Write `docs/DETEKT.md`; patch AGENTS Verification one-liner.
8. If client/CMP breaks: exclude client from detekt apply or source set; document residual risk.
9. Closeout: paths, verify log, version pin (stable vs alpha fallback), residual risk.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| CMP/client plugin clash | Exclude client or generated sources; keep other modules hard |
| Baseline churn / noisy PRs | Single shared baseline; regen only Jason/explicit; agents fix new smells |
| Slow full-project detekt | No type resolution on fast; parallel; main-only if needed; still fail on new |
| Kotlin 2.2 vs detekt 1.23.8 mismatch | Fallback pin `2.0.0-alpha.1`; record chosen version |
| buildSrc classpath vs root plugins block | Prefer buildSrc implementation of detekt-gradle-plugin for convention apply |
| Accidental mass “fix” via empty baseline | Generate baseline **before** claiming green; never ship without baseline file if debt exists |

---

**Handoff:** plan only. No product implementation this turn.
