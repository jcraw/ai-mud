# MUD-013 Plan — Fast verify integration + compact dod-summary.json

**Ticket:** MUD-013 · **Worker:** grok · **Phase:** implementing  
**Impl = fresh session** after Astra/Jason approve. This plan turn is not resumed for product edits.  
**Plan path:** `plans/2026-08-10-ai-mud-MUD-013-fast-verify-dod-json.md`  
**Worker mirror:** `tmp/workers/MUD-013/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh` (fast/default emits `tmp/dod-summary.json` + stays green)

Status: **APPROVED by Astra** (2026-08-10 ~23:55 AZ) — common-sense OK: extend verify only, fast≡default honesty, compact JSON, no script auto-retry, AGENTS N=3 + coverage≠done bullets. Fresh IMPL session authorized.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Fast lane: compile + scoped tests + detekt + konsist + test-lock **as available** | Keep `fast` ≡ `default` (honest inventory). Bare run: compile smoke + hard gates; module args add compile/test; JSON records `skipped` when tests not run. **Do not** promote bare fast to `--core`/`--full` |
| 2 | Compact `dod-summary.json` (gates, quarantine count if cheap, durations) | Extend `verify_mud.sh`: per-gate status + wall-clock s; write `tmp/dod-summary.json` (gitignored); human `== verify_mud ==` kept additive |
| 3 | AGENTS: no coverage-% DoD; attach summary path in closeout | One DoD/Verification bullet each (path + “coverage % alone ≠ done”) |
| 4 | N=3 retry then escalate | Document in AGENTS Verification (agent ops only); script does **not** auto-retry flaky gates |

---

## 2. Current inventory

| Item | Truth |
|------|--------|
| Lanes | `default`/`fast`/`--fast` → same `LANE=default`. Bare: `:core:compileKotlin` only, then detekt + Konsist arch + test-lock. Modules: `:<m>:compileKotlin` + `:<m>:test` if `src/test`. `core`/`full`: multi-module tests (+ compile-only leaves on full). `quarantine`: debt only, **no** detekt/Konsist/lock |
| Summary | Text only (`== verify_mud ==` lane/steps/result/notes). **No JSON.** Exit code = hard-step fail aggregate |
| Timing | None — no per-step durations |
| Quarantine count | Doc truth: `docs/TEST_QUARANTINE.md` “Quarantine count: **23**” (MUD-008). Not emitted by verify |
| AGENTS gaps | No dod-summary path; no “never coverage % alone”; no N=3 escalate rule; Verification table already lists fast = default + gates |
| Prior art | MUD-010/011/012 hard steps; `run_gradle` / `run_test_lock` / `maybe_stub pitest` patterns |
| DIGEST | Pointer only: gates #1/#5/#8 (DIGEST-025) — do not dual-copy body |

**Gap vs ticket wording:** bare fast has **no** unit-test slice today (only compile smoke + static/arch/lock). “Scoped tests as available” = when modules passed or when lane is core/full — not invent a hidden full suite under `--fast`.

---

## 3. Design / recommended approach

**Choice: extend `tools/verify_mud.sh` only** (timing + gate records + JSON write at end). No second wrapper, no Gradle plugin.

### Fast-lane semantics
- **Keep alias:** `fast`/`--fast` → `LANE=default` (document in help + AGENTS: “fast ≡ default”).
- **Do not** add automatic unit-test fan-out on bare default (would become slow/`--core`-lite and lie about “fast”).
- JSON `gates.tests.status`:
  - `pass`/`fail` when any test Gradle step ran
  - `skipped` on bare default (note: “no module tests; pass modules or use --core/--full”)
- Konsist already runs `:core:test --tests 'com.jcraw.mud.architecture.*'` — record under `konsist` (or `arch`), **not** as general `tests`.

### JSON path + write policy
- **Default path:** `tmp/dod-summary.json` (`tmp/` gitignored → agent feedback without dirty tree).
- Override: env `MUD_DOD_SUMMARY=/path` (optional, small).
- **Always write** at end (PASS and FAIL), including `--dry-run` with `result: DRY_RUN` or gates `skipped` + note dry-run (prefer: dry-run still emits schema with statuses `skipped`/`would_run` and `dry_run: true` so agents can parse path without running Gradle).
- Print one line in human summary: `dod_summary: tmp/dod-summary.json`.
- Fail-closed: overall exit code **unchanged**; JSON `result` mirrors PASS/FAIL; `exit_code` field = script exit.

### Schema (minimal, stable, <<2kB)

```json
{
  "schema_version": 1,
  "tool": "verify_mud",
  "lane": "default",
  "result": "PASS",
  "exit_code": 0,
  "generated_at": "2026-08-10T12:00:00Z",
  "duration_s": 42,
  "gates": {
    "compile":  { "status": "pass", "duration_s": 5 },
    "tests":    { "status": "skipped", "duration_s": 0, "note": "no module tests on bare default" },
    "detekt":   { "status": "pass", "duration_s": 12 },
    "konsist":  { "status": "pass", "duration_s": 18 },
    "test_lock":{ "status": "pass", "duration_s": 1 },
    "pitest":   { "status": "skipped", "note": "MUD-014 stub" }
  },
  "quarantine_count": 23,
  "steps": ["./gradlew :core:compileKotlin", "..."]
}
```

- `status` enum: `pass` | `fail` | `skipped` (only; keep tiny).
- Omit empty notes. `steps` optional short list (or drop if size pressure — prefer keep ≤ few lines).
- Quarantine lane: detekt/konsist/test_lock = `skipped` with note “quarantine lane”; tests = debt suite result.

### Timing
- Wall-clock seconds via `date +%s` (or `$SECONDS` delta) around each hard `run_gradle` / `run_test_lock` call.
- Aggregate multi-module compile/test into one `compile` and one `tests` gate (sum durations; first fail → gate `fail`).
- Total `duration_s` from script start → JSON write.

### Quarantine count (cheap only)
1. Prefer: `rg -c '@Tag\("quarantine"\)' --glob '*.kt'` (or `grep -R` fallback) — O(source), not suite run.
2. Fallback: parse `docs/TEST_QUARANTINE.md` for `Quarantine count: **N**`.
3. If both fail: omit field or `null` + note — **never** run `--quarantine` suite on fast.

### AGENTS bullets (impl session; surgical)
- **DoD:** never mark done on coverage % / mutation score alone; closeout should cite verify result + `tmp/dod-summary.json` (or `MUD_DOD_SUMMARY`).
- **Verification:** point at dod-summary path; **N=3:** agent may re-run failed verify up to 3 times for transient/env issues, then escalate to human — verify script does **not** loop retries.
- Keep AGENTS lean; optional ½-screen `docs/DOD_SUMMARY.md` only if schema needs a home (prefer inline comment in verify + AGENTS pointer — KISS).

### N=3 (ops, not script)
- Document only. No `while` retry inside `verify_mud.sh`.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/verify_mud.sh` | **Edit** — gate records, timing, JSON emit, help one-liner, header note MUD-013 |
| `AGENTS.md` | **Edit** — DoD + Verification bullets (coverage ≠ done; dod path; N=3) |
| `docs/DOD_SUMMARY.md` | **Optional** — only if schema docs would bloat AGENTS; else skip |
| Ticket / BOARD | Plan turn: `plan_review`; impl turn: closeout |

No `*.kt` product. No detekt/Konsist/test-lock **policy** changes. No PIT unstub.

---

## 5. Non-goals

- MUD-007 playtest/GUI, MUD-009 git hygiene
- MUD-014 PIT (leave stub), MUD-015 PBT, MUD-016 CI YAML (note: CI can consume same JSON later)
- Mass test rewrites / clear quarantine (MUD-017)
- Coverage % gates, mutation scores, live LLM
- Second verify wrapper; new Gradle plugin
- Auto-retry loops in the script
- Changing what detekt/Konsist/test-lock enforce — reporting only

---

## 6. How impl confirms acceptance

**Checklist**
- [ ] `./tools/verify_mud.sh` and `--fast` green; both write `tmp/dod-summary.json` with `result: PASS`, gates present
- [ ] Bare default: `tests.status=skipped`; detekt/konsist/test_lock = pass; compile = pass
- [ ] `./tools/verify_mud.sh default core` (or module with tests): `tests` = pass/fail as appropriate
- [ ] Forced fail (e.g. break lock temporarily or fake): exit ≠ 0, JSON `result: FAIL`, matching gate `fail`
- [ ] `--quarantine`: no detekt/konsist/lock hard run; those gates `skipped`; exit may be non-zero (debt OK)
- [ ] `quarantine_count` present and cheap (matches ~23 or doc); no suite run on fast
- [ ] Durations are non-negative ints/numbers; file <<2kB
- [ ] AGENTS bullets present; N=3 documented; no coverage-% DoD
- [ ] Human `== verify_mud ==` still prints; `dod_summary:` line present
- [ ] Exit codes unchanged vs pre-JSON behavior for green tree

**Commands**
```bash
./tools/verify_mud.sh
./tools/verify_mud.sh --fast
./tools/verify_mud.sh --dry-run
./tools/verify_mud.sh --core
./tools/verify_mud.sh --full
./tools/verify_mud.sh --quarantine   # may fail; still emits JSON
cat tmp/dod-summary.json             # or $MUD_DOD_SUMMARY
```

**Sample (bare default PASS)** — see schema above.

---

## 7. Ordered impl steps

1. Refactor `verify_mud.sh` internals: record gate name + status + duration around `run_gradle` / `run_test_lock` (map steps → compile | tests | detekt | konsist | test_lock; stubs → skipped).
2. Add cheap `quarantine_count` helper (rg/grep → fallback doc parse).
3. JSON writer (pure bash + printf; no `jq` required for emit; optional pretty if `jq` present — prefer fixed printf for zero deps).
4. Write path `tmp/dod-summary.json` (mkdir -p `tmp`); honor `MUD_DOD_SUMMARY`; print path in summary.
5. Help/header: fast ≡ default; dod-summary note; MUD-013.
6. AGENTS.md surgical bullets (DoD + Verification: path, coverage rule, N=3).
7. Self-check checklist §6; worker CLOSEOUT with sample JSON snippet; ticket → done only after approve+impl (not this plan turn).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| False “fast” that is actually full | Keep bare default = compile smoke + gates only; document tests need modules/`--core` |
| Stale quarantine_count | Prefer live `rg -c` over hardcoding 23; doc parse as fallback |
| tmp vs build path | Prefer `tmp/` (gitignored, agent-local); document; optional env override — not `build/` (Gradle wipes) |
| Agents ignore JSON | AGENTS closeout bullet + print path in human summary |
| JSON on fail never written | Always write in summary block before `exit` (trap EXIT optional if mid-script die — prefer `trap write_dod EXIT` for robustness) |
| Token bloat from steps | Cap steps list or omit on size; schema stays tiny |
| Dry-run confuses CI | Dry-run writes `dry_run: true` / skipped gates; exit 0 still |

---

**Handoff:** **APPROVED by Astra** → **fresh impl session** with this plan (do not resume plan session).
