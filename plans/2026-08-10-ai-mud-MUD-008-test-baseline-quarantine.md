# MUD-008 Plan — Test baseline + quarantine reasoning failures

**Ticket:** MUD-008 · **Worker:** grok · **Phase:** plan_review  
**Impl = fresh session** after Astra/Jason approve. This plan turn is not resumed for product edits.  
**Plan path:** `plans/2026-08-10-ai-mud-MUD-008-test-baseline-quarantine.md`  
**Worker mirror:** `tmp/workers/MUD-008/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core`

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Fresh test run recorded (date + command + counts) | Artifact under `tmp/workers/MUD-008/` (+ short summary in docs list) |
| 2 | Known `:reasoning` fails `@Tag("quarantine")` + reason comments | JUnit5 tags on failing methods/classes only; no assert edits |
| 3 | Default/core lanes exclude quarantine | Convention filter default `excludeTags("quarantine")`; verify lanes honest-green |
| 4 | README / AGENTS honest numbers + quarantine count | Replace stale “773 / 100%” (and AGENTS debt wording) with baseline counts |
| 5 | Quarantine list in `docs/` or ticket resolution | Prefer `docs/TEST_QUARANTINE.md` (path noted in ticket Resolution) |
| 6 | No assert-weakening | Tag-only; root fixes → MUD-017 |

---

## 2. Current inventory

| Item | Status |
|------|--------|
| `tools/verify_mud.sh` | **Done (MUD-004).** core = `:core:test :perception:test :memory:test` (omits reasoning); full = same + compile leafs; quarantine = bare `:reasoning:test` (may fail) |
| Quarantine tags | **None.** No `@Tag` / `excludeTags` / `includeTags` in tests or Gradle |
| Framework | JUnit Platform via `buildSrc/.../kotlin-jvm.gradle.kts` `useJUnitPlatform()`. Mix: mostly `kotlin.test.*`; some `org.junit.jupiter.api.*`. Jupiter API **5.10.3** on cache (via `kotlin("test")`) |
| Reasoning surface | **~41** `*.kt` under `reasoning/src/test` (~40 `*Test.kt`); ~1.2k `@Test` lines; one disabled file `CombatResolverTest.kt.disabled` |
| Stale claims | README: “**773 tests / 100%**”; `docs/TESTING.md` Current Status: ~793 / 100%; CLAUDE-era: ~621 core green, **~22–23 / 644** reasoning reds (2025-12-ish — **must re-baseline**) |
| AGENTS Verification | Already points at verify_mud; notes “omits `:reasoning` until MUD-008 tags” |
| Non-goal surface | testbot `@Disabled` exists but out of scope; Detekt/Konsist/PIT = later tickets |

---

## 3. Design / recommended approach

### Tag mechanism (JUnit5 — project is already on Platform)
- Annotate **failing** tests (prefer **method**; class only if entire class red) with:
  - `import org.junit.jupiter.api.Tag`
  - `@Tag("quarantine")`
  - Short comment: `// quarantine: <one-line reason>` (e.g. “V3 graph API drift”, “expects V2 Room”, “flaky seed / LLM mock”)
- **Do not** use `@Disabled` for debt (hides from quarantine lane). Do **not** delete/weaken asserts.
- If a method uses only `kotlin.test.Test`, still add Jupiter `@Tag` (Platform honors tags on Jupiter descriptors; kotlin.test maps to Jupiter engine — verify one pilot method early in impl).

### Gradle filter (convention plugin)
- Edit `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` so every `Test` task:
  - **Default:** `useJUnitPlatform { excludeTags("quarantine") }`
  - **Override property:** `-Pmud.quarantineOnly=true` → `includeTags("quarantine")` only (no exclude)
- Optional: `-Pmud.includeQuarantine=true` for “run all including quarantine” debug (not required by lanes).
- After this, bare `./gradlew :reasoning:test` is **honest green** for non-quarantined tests.

### verify_mud.sh lane wiring (post-tags)
| Lane | Behavior after MUD-008 |
|------|------------------------|
| default/fast | unchanged compile smoke |
| **core** | Keep current modules **or** add `:reasoning:test` (default excludeTags) if runtime acceptable; ticket verify is `--core` — prefer **include green reasoning** once tagged so core is truthier. If suite too slow, keep omit and document; full must include green reasoning. |
| **full** | Add `:reasoning:test` under default excludeTags; still no testbot/client thrash |
| **quarantine** | `./gradlew :reasoning:test -Pmud.quarantineOnly=true` — **hard-fail OK** (known debt). Update notes (drop “tags later”) |

### How baseline is recorded (impl session)
1. **Before tags:** one long run (expect reds):
   ```bash
   mkdir -p tmp/workers/MUD-008
   ./gradlew :reasoning:test --continue 2>&1 | tee tmp/workers/MUD-008/baseline-reasoning-$(date +%Y%m%d).log
   ```
   Timeout budget: **30–90 min** ok; do not chase flakes multi-hour. Also capture core lane:
   ```bash
   ./tools/verify_mud.sh --core 2>&1 | tee tmp/workers/MUD-008/baseline-core-$(date +%Y%m%d).log
   ```
2. Extract fails from log + `reasoning/build/reports/tests/test/index.html` / XML → list `Class#method`.
3. Write `docs/TEST_QUARANTINE.md`: date, commands, totals (tests/pass/fail/skip), table of quarantined tests + reason.
4. Optional short pointer in `docs/TESTING.md` “Current Test Status” only (≤~15 lines edit) — not a novel rewrite.
5. Re-run after tags: green `:reasoning:test` (exclude) + non-zero quarantine lane acceptable.

### Docs honesty
- README: kill “773 / 100%”; state core-green counts + “N quarantined in `:reasoning` (see `docs/TEST_QUARANTINE.md`)”.
- AGENTS Verification table: core/full exclude quarantine tags; quarantine lane = includeTags debt hard-fail; drop “until MUD-008 tags” hedging.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `reasoning/src/test/**/*Test.kt` | **Tag only** known reds (`@Tag` + reason comment) |
| `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` | Default excludeTags + `-Pmud.quarantineOnly` include path |
| `tools/verify_mud.sh` | core/full notes + optional green reasoning; quarantine uses `-Pmud.quarantineOnly=true` |
| `docs/TEST_QUARANTINE.md` | **Create** — list + baseline metadata |
| `docs/TESTING.md` | Surgical “Current Test Status” honesty only |
| `README.md` | Replace stale pass-rate claims |
| `AGENTS.md` | Verification lane wording only (tags live) |
| `tmp/workers/MUD-008/baseline-*.log` | **Create** (gitignored ok; path noted in docs) |
| Ticket / BOARD | Closeout bookkeeping at impl end |

**No** product logic, assert rewrites, testbot E2E, CI, Detekt/PIT.

---

## 5. Non-goals

- Fix root causes of reasoning fails (**MUD-017**)
- Weaken / delete asserts or `@Disabled` everything red to fake green
- testbot E2E green; Detekt / Konsist / PIT; CI (**MUD-016**)
- MUD-007 GUI / MUD-009 git hygiene
- Multi-hour flaky bisect beyond **one** recorded baseline run + tag pass

---

## 6. How impl confirms acceptance

- [ ] Baseline log exists: `tmp/workers/MUD-008/baseline-reasoning-YYYYMMDD.log` (date + command + pass/fail counts in `docs/TEST_QUARANTINE.md`)
- [ ] Every known red from that baseline has `@Tag("quarantine")` + reason comment; no assert-only “fixes”
- [ ] `./gradlew :reasoning:test` (default exclude) **exits 0**
- [ ] `./tools/verify_mud.sh --core` **exits 0** (ticket verify)
- [ ] `./tools/verify_mud.sh --full` **exits 0** and includes green reasoning (or documents why not)
- [ ] `./tools/verify_mud.sh --quarantine` runs includeTags path; **non-zero OK** if debt remains
- [ ] README + AGENTS numbers match baseline; quarantine **count** stated
- [ ] `docs/TEST_QUARANTINE.md` lists all quarantined tests; ticket Resolution points at it
- [ ] Closeout: paths changed, verify result, residual risk (flakes / count drift)

---

## 7. Ordered impl steps

1. **Scaffold artifacts dir** `tmp/workers/MUD-008/`; note start time.
2. **Baseline run (long):** `:reasoning:test --continue` → tee log; parse FAIL list. Also `--core` for stable green counts. If daemon/OOM, re-run with `--no-daemon` and record.
3. **Pilot tag:** one known-fail method + confirm Platform filter: default exclude skips it; `-Pmud.quarantineOnly=true` runs it.
4. **Wire convention filter** in `kotlin-jvm.gradle.kts` (default exclude; property include-only).
5. **Tag all baseline reds** (method-level; comment reason). Do not touch green tests.
6. **Update `verify_mud.sh`:** quarantine property; full (+ core if chosen) run green reasoning; refresh help/notes.
7. **Write `docs/TEST_QUARANTINE.md`**; surgical README / AGENTS / TESTING status.
8. **Confirm checklist** (§6); ticket → done + BOARD; residual risk note (counts may drift; MUD-017 owns repairs).

**Baseline timeout/artifact plan:** allow up to ~90m for reasoning suite; keep full console log under `tmp/workers/MUD-008/`; summarize counts into docs (do not commit huge logs unless asked).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Suite runtime long / agent timeout | One recorded run with tee; `--continue`; no multi-hour flake hunt |
| Wrong tag filter (tags ignored; still red or all skipped) | Pilot one test; confirm JUnit report shows tag; if kotlin.test ignores Jupiter `@Tag`, switch pilot to `@org.junit.jupiter.api.Test` + `@Tag` or add explicit `testImplementation("org.junit.jupiter:junit-jupiter")` |
| Flaky counts vs baseline | Quarantine only **consistent** fails from the recorded run; note flakes in docs without silent disable |
| Assert-weakening temptation | Hard rule: tag-only; repairs = MUD-017 |
| Over-quarantine (green tests tagged) | Only tag methods that failed in baseline log |
| core too slow if reasoning added | Prefer full includes reasoning; core may stay lean if needed — document choice |
| AGENTS/README over-edit | Touch claim lines + Verification table only |

---

## Impl session note

**Do not implement in the plan session.** Fresh impl after Astra/Jason approve this plan. Bookkeeping-only until then.


---

Status: APPROVED by Astra 2026-08-10 20:45 MST
