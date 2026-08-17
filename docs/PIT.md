# PIT mutation testing (pure modules)

**Ticket:** MUD-014 (wire) · **MUD-035** (threshold schedule) · **Plugin:** `info.solidsoft.pitest` 1.19.0 · **JUnit5 plugin:** 1.2.2

Mutation testing measures **assertion strength**, not line coverage. A high line-coverage suite can still leave weak asserts; PIT flips code (mutants) and checks whether tests kill them.

**This file is the schedule SoT** for the 60 → 70 → 80 ratchet. Live numbers stay in `tools/verify_mud.sh`. Do not invent a second fail policy in Gradle.

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
- **Score:** **min** of the three modules’ mutation coverage % (conservative). Parsed from `build/reports/pitest/mutations.xml` (`detected='true'` / total `<mutation` nodes)
- **Live after MUD-035 (R0):** soft **60**; hard-opt-in **60**; `PITEST_HARD_DEFAULT=0`
- **Hard opt-in:** `MUD_PITEST_HARD=1` (or env `mud_pitestHard=true`) → fail if min score &lt; `PITEST_HARD_THRESHOLD` (60 at R0)
- **R2b only:** `PITEST_HARD_DEFAULT=1` makes `--pitest` always hard (not live)

## Threshold schedule (60 soft → 70 soft → 80 hard)

| Rung | Policy | Live after MUD-035? | Promote when **all** hold |
|------|--------|---------------------|---------------------------|
| **R0 (now)** | soft **60**; hard-opt-in **60** | **yes** | already |
| **R1** | soft **70**; hard-opt-in **70** | no | remasured min ≥ **72** (2pp buffer); same modules/mutators; Jason/Astra OK |
| **R2a** | soft 70 or 80; hard-opt-in **80** (flag still required) | no | remasured min ≥ **82**; bake on opt-in |
| **R2b (target)** | **hard 80 default** on `--pitest` (`PITEST_HARD_DEFAULT=1`) | no | R2a baked; Jason/Astra OK (human threshold policy) |

Never put PIT (soft or hard) on default/fast/core. Never put 80-hard on `--full` while core PIT &gt;45s.

**Headroom** = `mutation_score` (min of three) from a green `--pitest` `tmp/dod-summary.json`, not line coverage, not “splits landed.”

Q3 god-splits (MUD-034a–n, including 034c perception and 034m memory/core) **landed**. Splits do **not** raise mutation %. Flip 70/80 only after remasured min clears the buffer **and** a human says so.

### Promote checklist

**R0 → R1**

- Remeasured min ≥ 72 (2pp buffer)
- Same modules (`:core` / `:perception` / `:memory`) and `STRONGER` mutators
- Jason/Astra OK
- Then set `PITEST_SOFT_THRESHOLD=70` and `PITEST_HARD_THRESHOLD=70`

**R1 → R2a**

- Remeasured min ≥ 82
- Bake on opt-in (`MUD_PITEST_HARD=1`) without flipping default
- Then set `PITEST_HARD_THRESHOLD=80` (soft may stay 70 or move to 80)

**R2a → R2b**

- R2a baked
- Jason/Astra OK (threshold policy is human)
- Then set `PITEST_HARD_DEFAULT=1` (and hard 80)

Do **not** silently flip constants because a structure wave finished.

### Remeasure instruction

```bash
./tools/verify_mud.sh --pitest
# read tmp/dod-summary.json → gates.pitest.mutation_score + per-module note
```

Use that min. If it is ≥ 72, **stop and ask Astra** before changing live rungs.

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

## Nightly / CI sketch (docs-only — do not land YAML)

MUD-016 required CI is `--core` only. Do **not** add `.github/workflows` for PIT in this ticket: Actions minutes, ~7m wall, memory TIMED_OUT history. Jason can add the file later in one PR.

```yaml
# optional — not in this ticket
name: PIT nightly
on:
  schedule: [{ cron: "0 8 * * *" }]   # ~01:00 AZ
  workflow_dispatch:
jobs:
  pitest:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      # same JDK17 / Gradle / rg as verify.yml
      - run: ./tools/verify_mud.sh --pitest
# required check: never, until R2b
# continue-on-error: yes while still R0/R1
```

## Anti-game

Forbidden as a way to “make 70/80”:

- delete or weaken tests
- shrink `targetClasses` / exclude packages
- drop `STRONGER`
- raise Gradle `mutationThreshold` to hide survivors
- skip a module and take **max** instead of **min**

Excludes only with measured thrash **and** Jason. Coverage % / mutation score alone ≠ done (see `AGENTS.md`).

## dod-summary

When PIT runs:

```json
"pitest": {
  "status": "pass",
  "duration_s": 180,
  "mutation_score": 9.1,
  "note": "core=26.0 perception=9.1 memory=… (min); soft 60% (schedule R0; next 70 when min≥72) — docs/PIT.md; below 60% soft threshold"
}
```

When skipped: `"status":"skipped"`, honest note (`not in lane` or `nightly via --pitest (core PIT >45s)`).  
**No** eternal `MUD-014 stub`. `mutation_score` is omitted when skipped.

Soft-below-threshold is **pass + note** at R0. Hard fail only when hard mode is on **and** min &lt; `PITEST_HARD_THRESHOLD`.

## Measured baseline (2026-08-11, MUD-014)

| Module | Wall-clock (approx) | Mutation coverage | Notes |
|--------|---------------------|-------------------|-------|
| `:perception` | ~26s analysis | ~9% | Smallest module; often the **min** score |
| `:core` | ~125s analysis / ~130s single-module wall | ~25–26% | **>45s → full skips PIT** |
| `:memory` | first run ~37m; with `threads=NCPU` ~6–7m analysis | ~39% | SQLite repos; TIMED_OUT minions; keep module (ticket scope) |

**`--pitest` three-module wall (2026-08-11, threaded):** ~**440s** (~7m). Dominated by `:memory`. Nightly only — never default/fast/core.

Soft 60% is **not** met day-one (**min ≈ 9.1%** perception) — expected; ticket accepts soft pass + note.

### Remeasure (impl, MUD-035)

From `tmp/dod-summary.json` after `./tools/verify_mud.sh --pitest` (2026-08-16). Gradle PIT tasks were cache/UP-TO-DATE (~1s); scores from `*/build/reports/pitest/mutations.xml`. Honest wall budget remains the 2026-08-11 ~440s three-module run.

| Module | Mutation coverage | Detected / total | Notes |
|--------|-------------------|------------------|-------|
| `:perception` | **9.8%** | 151 / 1548 | **min** |
| `:core` | 25.9% | 979 / 3776 | still &gt;45s analysis when not cached |
| `:memory` | 39.9% | 752 / 1886 | |
| **min (headroom)** | **9.8%** | — | << 72; **stay R0** — do not flip 70/80 |

## Follow-on (do not file here)

Assertion-strength tickets: **perception first** (current min), then core, then memory. This ticket only points at that path. Do not chase 70/80 by editing `src/test/**` under a schedule ticket.

## Non-goals

- Mutating app/client/testbot/reasoning
- 80% hard as a **day-one** CI threshold (80 hard is the **target**, not live)
- Raising live soft to 70 or hard/default to 80 on this ticket
- Editing `src/test/**` to “fix” the score under this ticket
- Landing nightly/CI YAML (sketch above is docs-only)
- Putting PIT on default/fast/core/full
- Weakening `STRONGER` / narrowing targets / changing score = min

## Related

- Verify lanes: `tools/verify_mud.sh` · ops contract: `AGENTS.md`
- Design (accepted): `docs/AGENT_QUALITY_GATES_DESIGN.md` §7.4 / C1 (schedule lives here)
- Test-lock: `docs/TEST_LOCK.md` (build config ≠ tests)
- Detekt / Konsist: `docs/DETEKT.md`, `docs/KONSIST.md`
