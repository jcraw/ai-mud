# MUD-011 Plan — Konsist architecture tests (module/package boundaries)

**APPROVED by Astra** · 2026-08-10 ~21:55 AZ · common-sense OK (Konsist 0.17.3 on :core tests, real package roots incl. app/sophia.llm, hard verify step not stub, residual allowlist only if needed; no mass package moves / no scope creep into 012–016).

**Ticket:** MUD-011 · **Worker:** grok · **Phase:** implementing  
**Impl = fresh session** after Astra/Jason approve. This plan turn is not resumed for product edits.  
**Plan path:** `plans/2026-08-10-ai-mud-MUD-011-konsist-architecture.md`  
**Worker mirror:** `tmp/workers/MUD-011/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` (must run real arch/Konsist tests, not stub)

Status: APPROVED by Astra 2026-08-10 21:55 MST

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Konsist (or equiv) dependency rules as tests | JUnit tests using Konsist Layer/`assertArchitecture` (or import guards) |
| 2 | Rules match real packages + Gradle edges | Layers = real roots (`com.jcraw.mud.*` + **app** / **llm** exceptions); edges = honest `implementation(project)` graph |
| 3 | Core verify lane includes arch tests | Replace `maybe_stub "konsist"` with hard Gradle step; `--core` green includes them |
| 4 | Document deliberate exception | Short `docs/KONSIST.md` + one AGENTS Verification pointer |

---

## 2. Current inventory

| Item | Status |
|------|--------|
| Konsist | **None.** Catalog has detekt only; no konsist dep/tests |
| Verify | L225: `maybe_stub "konsist" "MUD-011"` always SKIP; header notes stubs remain |
| Core lane | `:core:test :perception:test :memory:test :reasoning:test` + hard detekt |
| Modules | `:app :utils :llm :core :config :perception :reasoning :memory :action :testbot :client` |
| Deps (Gradle) | **config** ∅ · **utils** ∅ · **llm**→config · **core**→config · **action**→core · **memory**→core+llm · **perception**→core+llm · **reasoning**→config+core+llm+memory · **app**→core+config+perception+reasoning+memory+action+llm+utils · **client**→core+perception+reasoning+memory+llm+utils+config+action · **testbot**→core+config+llm+perception+reasoning+action+memory+**app** |
| Package roots (real) | `com.jcraw.mud.{core,config,perception,reasoning,memory,action,client,testbot}` · **app** = `com.jcraw.app` · **llm** = `com.jcraw.sophia.llm` · **utils** = empty main sources (leaf jar only) |
| Smoke | No quick illegal core→upward imports spotted; encode full graph, don’t assume clean |
| Prior art | MUD-010 detekt pattern (catalog pin + docs + verify hard step) |

---

## 3. Design / recommended approach

### Version
- Catalog pin: `konsist = "0.17.3"` (`com.lemonappdev:konsist`) — Maven latest stable as of plan inventory.
- `testImplementation` only (not production).

### Test home (prefer)
- **Primary:** `core/src/test/kotlin/com/jcraw/mud/architecture/` — already in `--core` via `:core:test`; no new module; scans **production** sources via `Konsist.scopeFromProject()` / `scopeFromProduction()`.
- **Avoid** new `:architecture` module unless core test classpath / scope quirks force it (then thin test-only module + add to verify).

### Rule set (encode **current** allowed graph; fail **new worse** edges)
- One layer per **module package root** (not research/sample names):

| Layer | Package include |
|-------|-----------------|
| config | `com.jcraw.mud.config..` |
| core | `com.jcraw.mud.core..` |
| llm | `com.jcraw.sophia.llm..` |
| action | `com.jcraw.mud.action..` |
| memory | `com.jcraw.mud.memory..` |
| perception | `com.jcraw.mud.perception..` |
| reasoning | `com.jcraw.mud.reasoning..` |
| app | `com.jcraw.app..` |
| client | `com.jcraw.mud.client..` |
| testbot | `com.jcraw.mud.testbot..` |

- Allowed `dependsOn` = Gradle edges above (transitive not re-listed unless Konsist requires explicit parents — prefer **declared** edges only; verify Konsist semantics in impl: “may depend” vs exclusive).
- Leaves: config + utils (utils has no package → skip layer or empty-scope note).
- Scope: **main/production only** (exclude `src/test`, buildSrc).
- **Do not** mass-refactor illegal imports this ticket. First green path:
  1. Run rules → if pre-existing violations, add **ticket-scoped allowlist** (FQCN or import prefix + comment `// allow: MUD-011 residual → MUD-XXX`) in test companion — still fail any non-allowlisted new edge.
  2. Prefer zero allowlist if graph already clean.

### Exception doc
- `docs/KONSIST.md` (short, DETEKT-shaped): pin, where tests live, allowed graph table, how to add exception (allowlist + ticket id), when **not** to weaken rules.
- AGENTS Verification: one line — Konsist real on default/fast/core/full; exceptions → `docs/KONSIST.md` (needs explicit approval for AGENTS edit per AGENTS protected rule — still in ticket scope as acceptance #4).

### Verify hook
- Remove `maybe_stub "konsist" "MUD-011"`.
- Hard step after lane body (mirror detekt), lanes **default/fast/core/full** (not quarantine):
  - `run_gradle :core:test --tests 'com.jcraw.mud.architecture.*'` (or exact class name).
- `--core` already runs full `:core:test` → filtered step is redundant but **clear** in summary; acceptable if suite stays fast (&lt; few seconds). Alt: core/full rely on `:core:test`, filtered step only on default/fast — either OK if documented; prefer **explicit filtered step all four lanes** for visible gate.
- Update help/header comments that mention konsist stub.

### Keep fast
- Single small test class (~1–2 files), project scope once, no full compile of client for rule run if Konsist file-scope suffices.
- No dual-stack ArchUnit.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add `konsist` version + library |
| `core/build.gradle.kts` | `testImplementation(libs.konsist)` |
| `core/src/test/kotlin/com/jcraw/mud/architecture/ModuleBoundaryTest.kt` (name flexible) | Rules + optional allowlist |
| `docs/KONSIST.md` | Exception + graph + verify usage |
| `AGENTS.md` | One Verification line (Konsist live; stub note retire) |
| `tools/verify_mud.sh` | Hard konsist step; drop stub; help text |

No product gameplay, no detekt/PIT/test-lock/CI.

---

## 5. Non-goals

- Detekt (MUD-010), test-file lock (012), PIT (014), CI (016)
- Mass package moves / fixing all historical layering debt
- Product/gameplay, MUD-007 GUI, MUD-009 git hygiene
- Full ArchUnit dual-stack
- New network/CI wiring

---

## 6. How impl confirms acceptance

- [ ] `./gradlew :core:test --tests 'com.jcraw.mud.architecture.*'` exits 0
- [ ] Illegal edge smoke: temporarily add forbidden import in a temp/local check or document that a deliberate wrong import fails (no committed poison) — optional; at least rules assert known forbidden pairs
- [ ] `./tools/verify_mud.sh --core` exits 0; summary shows real konsist/arch step (not `SKIP stub: konsist`)
- [ ] `./tools/verify_mud.sh --dry-run` prints arch gradle command
- [ ] `docs/KONSIST.md` + AGENTS pointer present
- [ ] Layers/packages match inventory in §2 (incl. `com.jcraw.app`, `com.jcraw.sophia.llm`)

---

## 7. Ordered impl steps

1. Pin Konsist in catalog; wire `testImplementation` on `:core`.
2. Add architecture test: layers + allowed graph from §2; production scope only.
3. Run test; if failures → inventory violations → ticket-scoped allowlist (no silent broaden of “dependsOn”).
4. Wire `verify_mud.sh`: hard filtered `:core:test` on default/fast/core/full; remove maybe_stub konsist; touch help/header.
5. Write `docs/KONSIST.md`; one-line AGENTS Verification update.
6. Confirm `./tools/verify_mud.sh --core` + dry-run; closeout note (paths, pin version, allowlist residual if any).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Pre-existing illegal edges | Encode real graph; allowlist residuals with ticket path — no mass fix |
| Wrong package assumptions | Real roots: `com.jcraw.app`, `com.jcraw.sophia.llm` (not `com.jcraw.mud.llm` / `mud.app`) |
| CMP/client scan cost | File-scope Konsist; production only; avoid compiling full client for arch gate |
| Slow full-source scan | One suite, one scope; filtered test in verify |
| Konsist `dependsOn` semantics surprise | Read 0.17.x Layer docs in impl; fall back to import-prefix guards per module if Layer DSL too coarse |
| utils empty | No package layer; still allowed dep target for app/client only via Gradle (no package asserts) |
| testbot→app upward | Encode honestly (unusual but real) |
| Double-run on `--core` | Accept cheap re-run or skip filtered step when lane already ran `:core:test` |

---

## Handoff

**Next:** Astra/Jason approve → **fresh** impl session (not this planner).  
**Verify after impl:** `./tools/verify_mud.sh --core`
