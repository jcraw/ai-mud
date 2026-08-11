# MUD-014 Plan — PIT mutation on pure modules (core/perception/memory)

**Ticket:** MUD-014 · **Worker:** grok · **Phase:** implementing  
**Impl = fresh session** after Astra approve. Do not resume plan session for product/Gradle edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-014-pit-mutation-pure-modules.md`  
**Worker mirror:** `tmp/workers/MUD-014/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` (must stay free of full PIT) + `./tools/verify_mud.sh --pitest` (or full if wired)

**APPROVED by Astra** · 2026-08-11 ~00:35 AZ · common-sense OK: PIT only on core/perception/memory (not kotlin-jvm), STRONGER + soft 60%, default/fast/core never PIT, new `--pitest` lane, full wire iff core ≤45s else nightly, kill eternal stub, no src/test edits. Fresh IMPL authorized.

Status: **APPROVED by Astra** (2026-08-11 ~00:35 AZ) — fresh IMPL session authorized.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | PIT STRONGER (or equiv) on `:core` / `:perception` / `:memory` only | Official `info.solidsoft.pitest` + JUnit5 plugin; **per-module** apply on those three — never app/client/testbot/llm/reasoning |
| 2 | Threshold ~60% local measure (not 80% day-one hard CI) | Measure once; document baseline; soft threshold 60% (see §3 fail policy) |
| 3 | If core PIT >~45s → nightly-only; fast free of full PIT | Time `:core:pitest` in impl; if >45s → `docs/PIT.md` nightly note; **default/fast never run PIT** |
| 4 | Full or nightly lane wires pitest; dod-summary records score | New `--pitest` lane always; `--full` wires iff measure ≤45s core else skip with honest note. JSON: score when run, `skipped` (not forever-stub) when not |
| 5 | No mutate-the-world / null-check thrash | `targetClasses` pure packages; mutators `STRONGER`; exclude noisy ops only if measure thrash |

---

## 2. Current inventory

| Item | Truth |
|------|--------|
| **verify pitest** | `maybe_stub "pitest" "MUD-014"` always → `gates.pitest.status=skipped`, note `"MUD-014 stub"`. Header: “PIT remains a stub until MUD-014.” Never runs Gradle. |
| **Lanes** | default/fast: compile smoke + detekt + konsist + test-lock. core: 4-module tests. full: same tests + compile-only leaves. **No** `--pitest` flag today. |
| **Catalog** | `gradle/libs.versions.toml`: detekt/konsist only — **no pitest** plugin/version. |
| **Convention** | `buildSrc/.../kotlin-jvm.gradle.kts` applies kotlin-jvm + detekt + JUnit Platform to **all** JVM modules. Do **not** hang PIT on this (would mutate-the-world). |
| **Modules** | All three use `buildsrc.convention.kotlin-jvm` + serialization. **core** → config; **perception** → core+llm; **memory** → core+llm+sqlite. Main kt ≈67 / 2 / 35; tests ≈25 / 4 / 20. |
| **Packages** | `com.jcraw.mud.core.*` (+crafting/handlers/world/repository); `com.jcraw.mud.perception.*`; `com.jcraw.mud.memory.*` (+item/combat/world/social/skill). |
| **Purity** | core purest; perception thin parser; memory **I/O-ish** (SQLite repos) but ticket-scoped — scope classes, don’t drop module. |
| **Plugin truth** | Maintained: `info.solidsoft.pitest` **1.19.0** (2026-03; Gradle 8.14/9 OK) + `junit5PluginVersion` (PIT junit5 plugin ~1.2.x). |
| **Timing risk** | Unknown until measure. core 67 sources + 25 tests could exceed 45s. Plan assumes measure step is **blocking** for lane choice. |
| **DIGEST** | Pointer DIGEST-025 gate #6 — do not dual-copy body. |
| **AGENTS** | “PIT remains a stub until later tickets (MUD-014)”; coverage/mutation alone ≠ done (keep). |

---

## 3. Design / recommended approach

### Plugin + version
- Catalog: `pitestGradle = "1.19.0"` (confirm latest at impl), plugin id `info.solidsoft.pitest`.
- Optional pin: PIT core / `junit5PluginVersion` per solidsoft docs (e.g. `1.2.1`+).
- **Apply only** in `core` / `perception` / `memory` `build.gradle.kts` (or thin `buildSrc` convention `pitest-pure.gradle.kts` applied by those three — **not** kotlin-jvm).
- Prefer shared pitest block via small convention **or** copy-paste 15-line block ×3 (KISS: convention if >1 file drift risk).

### Target classes / mutators
```
core:       targetClasses = ["com.jcraw.mud.core.*"]
perception: targetClasses = ["com.jcraw.mud.perception.*"]
memory:     targetClasses = ["com.jcraw.mud.memory.*"]
```
- Mutators: **`STRONGER`** (or default STRONGER group).
- Exclude only if thrash after first run: e.g. null-return / void-method noise — document in `docs/PIT.md`, not preemptively gut.
- Exclude generated/`**/build/**` (plugin default); no app/client/testbot targets.

### Threshold / fail policy (KISS — won’t brick default drain)
| Condition | Gate status |
|-----------|-------------|
| Lane does not run PIT | `skipped` (note: e.g. `not in lane` — **never** eternal `MUD-014 stub`) |
| PIT Gradle tasks exit 0 | `pass` + `mutation_score` (aggregate or min of modules — pick **min** of three scores as conservative) |
| Score &lt; 60% | Still **`pass`** + `note: "below 60% soft threshold"` (day-one soft). Optional hard: env `MUD_PITEST_HARD=1` or `-Pmud.pitestHard=true` → fail if min &lt; 60 |
| Task crash / 0 mutations / unparseable report | `fail` |
| **Never** run on default/fast/core | default drain stays green without PIT wall-time |

Gradle `mutationThreshold` leave **0** (report-only at Gradle); threshold policy lives in verify script + docs.

### Lanes
| Lane | PIT? |
|------|------|
| default / fast | **Never** |
| core | **Never** (ticket `verify:` stays fast-enough for drain) |
| full | Run **iff** measured `:core:pitest` ≤~45s; else skip + note `nightly: use --pitest` |
| **`--pitest`** (new) | Always: `./gradlew :core:pitest :perception:pitest :memory:pitest` (order fixed; fail-closed on task error) |
| quarantine | Never |

Impl measure step: wall-clock `:core:pitest` once; write result into `docs/PIT.md` + plan closeout. If &gt;45s → full stays skip; `--pitest` = nightly entrypoint.

### dod-summary fields
Extend existing `gates.pitest` object (already has status/duration_s/note):

```json
"pitest": {
  "status": "pass",
  "duration_s": 52,
  "mutation_score": 61.2,
  "note": "core=63.0 perception=70.1 memory=61.2 (min)"
}
```

When skipped: `"status":"skipped","duration_s":0,"note":"not in lane"` (or `nightly via --pitest`).  
**Drop** `"MUD-014 stub"` forever once wired. Optional `mutation_score` key only when run (omit when skipped).

Score parse: prefer PIT XML/HTML summary under each module `build/reports/pitest/` (solidsoft default); bash grep/sed — no jq required (match MUD-013 style).

### Docs
- New **`docs/PIT.md`** (½–1 screen): how to run, modules, STRONGER, soft 60%, nightly note, dod-summary fields, hard-opt-in flag.
- **AGENTS.md**: replace “PIT remains a stub…” with one line → `docs/PIT.md`; keep “mutation score alone ≠ done”.
- Help text in `verify_mud.sh` for `--pitest`.

### Test-lock
- PIT config is **build/docs/tools only** — not `src/test/**`. Do not touch tests or weaken lock. No `MUD_ALLOW_TEST_CHANGES` needed.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add pitest plugin version (+ optional junit5 plugin version pin) |
| `buildSrc/build.gradle.kts` | If convention: `implementation` pitest plugin classpath (mirror detekt) |
| `buildSrc/.../pitest-pure.gradle.kts` | **Optional** shared convention applied by 3 modules |
| `core/build.gradle.kts` | Apply + configure pitest |
| `perception/build.gradle.kts` | Apply + configure pitest |
| `memory/build.gradle.kts` | Apply + configure pitest |
| `tools/verify_mud.sh` | Replace `maybe_stub pitest`; add `--pitest` lane; parse score → JSON; full optional wire; stop stub notes |
| `docs/PIT.md` | **Create** short runbook |
| `AGENTS.md` | Surgical: stub line → PIT.md pointer; Verification table row for `--pitest` if table stays accurate |
| `plans/` + ticket | Closeout only (impl session) |

**Do not touch:** product `*.kt` gameplay, `src/test/**`, test-lock manifest, app/client/testbot/reasoning build files, CI YAML (MUD-016).

---

## 5. Non-goals

- MUD-007 playtest / GUI, MUD-009 git hygiene
- MUD-015 Kotest PBT, MUD-016 CI YAML (may note later consumes `--pitest`), MUD-017 quarantine, MUD-018 deprecation
- 80% hard threshold day one; mutate app/client/testbot/reasoning
- Coverage % as DoD; live LLM; force-push; secrets
- Multi-hour mutation in plan turn; product gameplay changes; weakening test-lock

---

## 6. How impl confirms acceptance

**Checklist**
- [ ] `./gradlew :core:pitest :perception:pitest :memory:pitest` succeeds; reports under `build/reports/pitest/`
- [ ] Target classes limited to three pure package roots; STRONGER (or documented equiv)
- [ ] `./tools/verify_mud.sh` and `--fast` → `pitest.status=skipped` (no stub forever text; no PIT runtime)
- [ ] `./tools/verify_mud.sh --core` → exit 0; pitest still skipped
- [ ] `./tools/verify_mud.sh --pitest` → runs PIT; JSON has `mutation_score` number; status pass|fail per policy
- [ ] If core timed ≤45s: `--full` also runs PIT; else `--full` skip + nightly note in docs + JSON note
- [ ] Soft 60%: score recorded; below-threshold does not fail unless hard flag
- [ ] `docs/PIT.md` + AGENTS pointer; no `src/test` edits; test-lock green
- [ ] Measure wall-clock logged in closeout

**Verify commands**
```bash
./tools/verify_mud.sh              # pitest skipped; exit 0
./tools/verify_mud.sh --core       # ticket verify; pitest skipped
./tools/verify_mud.sh --pitest     # real PIT + score in tmp/dod-summary.json
./tools/verify_mud.sh --full       # PIT or honest skip per measure
./tools/verify_mud.sh --dry-run --pitest
```

**Sample JSON fragment (when run)**
```json
"pitest": {
  "status": "pass",
  "duration_s": 48,
  "mutation_score": 61.2,
  "note": "min of core/perception/memory; soft threshold 60%"
}
```

---

## 7. Ordered impl steps

1. Catalog: pin `info.solidsoft.pitest` (+ junit5 plugin version).
2. Apply pitest to core/perception/memory only (convention or ×3); `targetClasses` + STRONGER + junit5; threshold 0 at Gradle.
3. Smoke: run `:perception:pitest` first (smallest) → fix config; then core; then memory.
4. **Measure** wall-clock `:core:pitest` (and total three); record numbers.
5. Parse reports → score helper in `verify_mud.sh` (min of three).
6. Replace `maybe_stub`; add `--pitest` lane; default/fast/core skip honestly; full wire iff ≤45s else skip+nightly.
7. dod-summary: emit `mutation_score` when run; kill “MUD-014 stub” notes.
8. Write `docs/PIT.md`; surgical AGENTS Verification/PIT lines.
9. Run acceptance verify set; write closeout + `tmp/dod-summary.json` path; ticket → done only after green.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| **Slow PIT** (core &gt;45s or multi-min total) | Nightly/`--pitest` only; never default/fast/core; document in PIT.md |
| **Flaky mutants** / non-determinism | Avoid time/random-heavy targets; re-run N≤3 then escalate; don’t inflate threshold |
| **Wrong purity** (memory SQLite thrash) | Keep module; narrow `targetClasses` / excludedClasses after first report — not drop ticket scope |
| **Default-lane pollution** | Explicit lane table; no PIT in kotlin-jvm convention; CI later must not call bare verify for PIT |
| **Test-lock confusion** | Build config ≠ tests; never edit `src/test` or weaken lock for “better” score |
| **Score parse brittle** | Prefer stable XML field; fail closed if missing when lane ran |
| **Kotlin/PIT oddities** | data class / null mutators noisy → exclude only after evidence |
| **Plugin version drift** | Pin in catalog; impl re-checks plugins.gradle.org at apply time |

---

**Handoff:** **APPROVED by Astra** → **fresh impl session** with this plan (do not resume plan session).
