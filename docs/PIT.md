# PIT mutation testing (pure modules)

**Ticket:** MUD-014 · **Plugin:** `info.solidsoft.pitest` 1.19.0 · **JUnit5 plugin:** 1.2.2

Mutation testing measures **assertion strength**, not line coverage. A high line-coverage suite can still leave weak asserts; PIT flips code (mutants) and checks whether tests kill them.

## Scope (pure-ish only)

| Module | `targetClasses` | Notes |
|--------|-----------------|-------|
| `:core` | `com.jcraw.mud.core.*` | Purest domain model |
| `:perception` | `com.jcraw.mud.perception.*` | Thin parser / intents |
| `:memory` | `com.jcraw.mud.memory.*` | I/O-ish (SQLite) but ticket-scoped |

**Not mutated:** `app`, `client`, `testbot`, `reasoning`, `llm`, `action`, `config`, `utils`.  
PIT is **not** applied via the shared `kotlin-jvm` convention (that would mutate-the-world). Shared config lives in `buildSrc/.../pitest-pure.gradle.kts`, applied only by the three modules above.

## Config highlights

- **Mutators:** `STRONGER` group
- **Gradle `mutationThreshold`:** `0` (report-only; policy is in `tools/verify_mud.sh`)
- **Reports:** `*/build/reports/pitest/` (`timestampedReports=false` for stable paths)
- **Soft threshold:** 60% (day-one). Below 60% still **passes** with a note
- **Hard opt-in:** `MUD_PITEST_HARD=1` (or env `mud_pitestHard=true`) → fail if min score &lt; 60%

## How to run

```bash
# Nightly / deep gate (always runs pure-module PIT)
./tools/verify_mud.sh --pitest

# Direct Gradle (same three modules)
./gradlew :core:pitest :perception:pitest :memory:pitest

# Default / fast / core — never run PIT
./tools/verify_mud.sh
./tools/verify_mud.sh --core
```

## Lanes

| Lane | PIT? |
|------|------|
| default / fast | **Never** |
| core | **Never** (drain stays free of PIT wall-time) |
| full | **Skipped** — measured `:core:pitest` wall ~**130s** (2026-08-11) &gt; 45s budget |
| **`--pitest`** | Always runs the three pure modules |
| quarantine | Never |

**Nightly entrypoint:** `./tools/verify_mud.sh --pitest` (not bare verify / not `--full`).

## dod-summary

When PIT runs:

```json
"pitest": {
  "status": "pass",
  "duration_s": 180,
  "mutation_score": 9.1,
  "note": "core=26.0 perception=9.1 memory=… (min); soft threshold 60%; below 60% soft threshold"
}
```

When skipped: `"status":"skipped"`, honest note (`not in lane` or `nightly via --pitest (core PIT >45s)`).  
**No** eternal `MUD-014 stub`. `mutation_score` is omitted when skipped.

Score = **min** of the three modules’ mutation coverage % (conservative). Parsed from `build/reports/pitest/mutations.xml` (`detected='true'` / total `<mutation` nodes).

## Measured baseline (2026-08-11)

| Module | Wall-clock (approx) | Mutation coverage | Notes |
|--------|---------------------|-------------------|-------|
| `:perception` | ~26s analysis | ~9% | Smallest module; often the **min** score |
| `:core` | ~125s analysis / ~130s single-module wall | ~25–26% | **>45s → full skips PIT** |
| `:memory` | first run ~37m; with `threads=NCPU` ~6–7m analysis | ~39% | SQLite repos; TIMED_OUT minions; keep module (ticket scope) |

**`--pitest` three-module wall (2026-08-11, threaded):** ~**440s** (~7m). Dominated by `:memory`. Nightly only — never default/fast/core.

Soft 60% is **not** met day-one (**min ≈ 9.1%** perception) — expected; ticket accepts soft pass + note. Raise score later by strengthening tests (separate tickets), not by weakening mutators or test-lock.

## Non-goals

- Mutating app/client/testbot/reasoning
- 80% hard CI threshold day one
- Editing `src/test/**` to “fix” the score under this ticket
- CI YAML wiring (MUD-016 may consume `--pitest` later)

## Related

- Verify lanes: `tools/verify_mud.sh` · ops contract: `AGENTS.md`
- Test-lock: `docs/TEST_LOCK.md` (build config ≠ tests)
- Detekt / Konsist: `docs/DETEKT.md`, `docs/KONSIST.md`
