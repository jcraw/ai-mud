# AI MUD — Agent-native quality gates design

**Status:** design **accepted** (Jason 2026-08-11) · implementation via Wave Q tickets  
**Audience:** Astra + builders · human never expected to read product code  
**Sources:** LoC DIGEST-007, DIGEST-025, DIGEST-009/016 · game_jam `tools/check_*` + `config/quality/*` · jam GJ-005 gap report · current `AGENTS.md` / verify lanes (MUD-004…016)

### Accepted decisions (Jason 2026-08-11)
1. **Token-primary ceilings** — file warn 2.0k / err 2.5k tok; fn 200/250; temporary god-file overrides + burn-down tickets  
2. **Hard-on-touched in Q2 before new features** — yes  
3. **PIT hard 80%** only after structure/split waves start — yes (stay soft 60% until then)  
4. **Product E-tier** (headless play smoke) after Q2 — not a drain blocker until product phase  
5. **File Wave Q tickets** and spare-drain — yes (MUD-026+)

## 1. North star

Optimize the repo so an **LLM coder** can:

1. **Change one behavior safely** without loading the whole tree  
2. Get **machine-actionable** fail feedback (compact JSON + remediation codes)  
3. Prove **behavior + assertion strength**, not coverage theater  
4. Stay inside **context budgets** (file/fn/plan/brief size)  
5. Be **blocked from gaming** gates (test delete, baseline regen, infinite retry)

**Not goals:** human prose style, pretty formatting for review, “looks clean in IDE.”  
**Human role:** product taste, playtest, risk, threshold policy — **not** line-by-line code review.

## 2. Principles (from research + jam)

| Principle | Source | AI MUD application |
|-----------|--------|-------------------|
| Deterministic gates → compact DoD JSON | DIGEST-007/025 | Extend `tmp/dod-summary.json`; every gate emits stable codes |
| **Token ceilings > LOC-only** | DIGEST-007; jam measures both | Primary: chars/4 token estimate; secondary: LOC/cyclo |
| Mutation / contracts > coverage % | DIGEST-025 | PIT pure modules; behavior tests; no done-on-% |
| Architecture blast-radius | DIGEST-025 + jam import matrix | Keep Konsist; tighten residual allowlist policy |
| Touched-scope fast lane | DIGEST-007 + jam `codex_verify` | Fast = changed modules + static on touched paths |
| Anti-game | DIGEST-007/025 + jam test lock | Test-lock, N=3, ban silent suppressions, Jason-only baseline |
| Measure before thrash | jam GJ-005 | Don’t hard-fail 2.5k tok on 1.2k-line hosts day one — ratchet |
| Repo is system of record | DIGEST-007/009 | AGENTS + ticket + plan; no tribal chat DoD |

## 3. Current state (honest)

| Gate | Live? | What it actually means today |
|------|-------|------------------------------|
| compile / module tests | yes | Real correctness signal |
| Detekt | yes | **Ratchet only** — ~1478 baselined smells (LongMethod, LargeClass, MagicNumber…) |
| Konsist | yes | Encodes *current* graph; empty allowlist; no size rules |
| Test-lock | yes | Anti test-delete/edit without ticket |
| PIT | yes (soft 60%, not in `--core`) | Assertion strength on pure modules only |
| Kotest PBT | yes (in suite) | Combat/graph hot paths |
| dod-summary.json | yes | Compact lane result |
| Quarantine | 0 | Cleared; keep lane for future debt |
| **Token / file ceilings** | **no** | Guideline “~1000 lines” only — not enforced |
| **Fn complexity hard on new code** | **partial** | Detekt rules exist but legacy baselined |
| **Duplication / dead code** | **no** | Jam has these |
| **Touched-path static** | **no** | Whole-module detekt, not path-scoped JSON |
| **Contract/play smoke in DoD** | **no** | Product playtest deferred; no headless command contract gate |
| **Doc/AGENTS sync gate** | **no** | Process only |
| **Remediation codes** | **weak** | Gradle text, not jam-style finding codes |

**Why “everything passes”:** we installed a **ratchet**, not a cleanup. Green ≠ agent-optimal structure.

## 4. Target quality definition (LLM-coder)

A change is high quality when:

1. **Locality** — behavior lives in ≤1–2 modules; file fits in model context with room for tests + ticket  
2. **Navigability** — package boundaries hold; no god-handlers absorbing every intent  
3. **Provability** — pure logic has strong tests (mutation-resistant); I/O mocked  
4. **Feedback thrift** — fail in seconds with one JSON blob an agent can fix from  
5. **No silent debt growth** — new LongMethod/LargeClass/token blowups fail closed  
6. **Parity** — console/GUI/GameServer shared apply paths (already a product lesson)

## 5. Gate catalog (design)

Emit all automated gates into `tmp/dod-summary.json` (extend schema v2).  
Runtime schema: `config/quality/dod_summary.schema.json` (MUD-027); short ops note: `docs/DOD_SUMMARY.md`.

```json
{
  "schema_version": 2,
  "lane": "fast|core|full|pitest|quarantine",
  "result": "PASS|FAIL",
  "gates": { "<id>": { "status": "pass|fail|warn|skipped", "duration_s": 0, "findings": 0 } },
  "findings": [
    { "code": "TOKEN_FILE_E", "path": "…", "metric": 3200, "limit": 2500, "remediation": "split …" }
  ]
}
```

### Tier A — always on (fast / default) · target <30s warm

| ID | Gate | Hard? | Kotlin / tool sketch | Threshold (start → target) |
|----|------|-------|----------------------|----------------------------|
| A1 | `compile_touched` | hard | Gradle compile changed modules | exit 0 |
| A2 | `test_touched` | hard when `src/test` exists | module tests, exclude quarantine | exit 0 |
| A3 | `detekt_new` | hard | Detekt; **baseline stays for legacy**; new IDs fail | maxIssues 0 unbaselined |
| A4 | `konsist_arch` | hard | existing ModuleBoundaryTest | empty residual preferred |
| A5 | `test_lock` | hard | existing test_lock.sh | manifest match |
| A6 | **`token_budget_src`** | hard on **touched** prod `.kt` | **`tools/quality/check_token_budget_kt.py`** (chars/4; MUD-028 report-only — see `docs/TOKEN_BUDGET_KT.md`) | file **warn 2.0k / err 2.5k tok**; fn **warn 200 / err 250 tok** · path overrides JSON for known gods during burn-down |
| A7 | **`structure_kt`** | hard on touched | same checker (structure secondary; MUD-028 report-only) and/or Detekt without baseline for *touched files only* | fn LOC W55/E90; cyclo W10/E16; cognitive W15/E25; file LOC W700/E1100 (secondary to tokens) |
| A8 | **`findings_json`** | hard if any E | unify A3–A7 into remediation JSON | stable `code` enum |

**Jam analogue:** `check_quality_gates.py` + `check_token_budget_gates.py` + selector JSON.

### Tier B — core lane (PR / drain default for engine)

| ID | Gate | Hard? | Notes |
|----|------|-------|-------|
| B1 | A-tier full modules | hard | core+perception+memory+reasoning (green) |
| B2 | `no_live_llm_unit` | hard | grep/Konsist-ish: unit tests must not call real OpenAI |
| B3 | `handler_parity_smoke` | hard (phased) | contract tests that console/GUI/GameServer share apply for inventory/combat intents (extend MUD-019/023/024 pattern) |
| B4 | `known_issues_regress` | hard | KNOWN_ISSUES fixed paths stay covered by named tests |
| B5 | `dod_schema` | hard | dod-summary validates against `docs/or config schema` |

### Tier C — full / release

| ID | Gate | Hard? | Notes |
|----|------|-------|-------|
| C1 | PIT pure modules | soft→hard | start 60% (today); target **80%** core when burn-down allows; keep out of fast |
| C2 | PBT hot paths | hard | existing Kotest; expand graph/combat/inventory math |
| C3 | `duplication_kt` | warn→hard | jam-style block clone on `app`/`client` handlers |
| C4 | `dead_code_kt` | warn | unused public in pure modules (detekt / toolchain) |
| C5 | quarantine_count | info/hard | must be 0 on full unless ticket opens debt |
| C6 | CI `--core` + scheduled `--pitest` | hard | already have core CI; add nightly PIT |

### Tier D — agent context / process (not product bytecode)

| ID | Gate | Hard? | Jam analogue |
|----|------|-------|--------------|
| D1 | plan token budget | hard on plan approve | plan ≤~2k warn / 3.5k fail tok |
| D2 | brief token budget | hard | brief ≤1.2k / 2k |
| D3 | AGENTS.md size | warn/fail | keep lean; deep docs linked |
| D4 | BOARD slim drain | process | jam snapshot --slim lesson |
| D5 | N=3 verify retry | process | already in AGENTS |
| D6 | no mass baseline regen | hard policy | Jason-only (exists) |
| D7 | mandatory-read pack preflight | hard when spawning builders | jam token preflight |

### Tier E — product truth (later; not fake-green)

| ID | Gate | Hard? | Notes |
|----|------|-------|-------|
| E1 | headless command script | hard when product phase | scripted `look/take/inv/attack` without GUI |
| E2 | testbot golden playthrough | soft/nightly | LLM cost; mock where possible |
| E3 | multi-user local smoke | soft | two sessions same world |

Do **not** block harness drains on E-tier until Jason opens product phase (standing posture).

## 6. What we explicitly do *not* gate

- Human comment density / “clean naming taste”  
- Full-repo detekt baseline burn-down as one ticket  
- Live OpenAI in CI  
- 100% typed perfection beyond Kotlin defaults  
- GUI pixel diffs day one  
- Global token hard-fail at research numbers **without overrides** (GJ-005 lesson)

## 7. Threshold policy (ratchet, not cliff)

### 7.1 Source token budgets (new)

Estimate: `tokens ≈ ceil(chars / 4)` (OpenClaw / jam convention). Prod `src/main` only for hard fail; tests looser.

| Surface | Warn | Error | Notes |
|---------|------|-------|-------|
| File | 2000 | 2500 | DIGEST-007; overrides for listed gods during split wave |
| Function | 200 | 250 | Prefer extract pure functions in `:core`/`:reasoning` |
| Plan file | 2000 | 3500 | process |
| Impl brief | 1200 | 2000 | process |

**Overrides file:** `config/quality/token_budget_kt.json`  
- List today’s offenders (`GraphGenerator`, `EngineGameClient`, …) with temporary higher caps  
- Each override **requires** a burn-down ticket id  
- New files: **no** override privilege — must meet target  
- **Caps may only lower** over time (anti-gaming; never raise without ticket)  
- **Live (MUD-031):** hard-on-touched default on default/fast/core/full via `./tools/verify_mud.sh`; soft opt-out `MUD_TOKEN_SOFT=1` / `--token-soft`; checker + overrides applied — `docs/TOKEN_BUDGET_KT.md`

### 7.2 Structure (secondary)

Align with jam static_quality_thresholds spirit:

| Metric | Warn | Error |
|--------|------|-------|
| File LOC | 700 | 1100 |
| Fn LOC | 55 | 90 |
| Cyclomatic | 10 | 16 |
| Cognitive | 15 | 25 (tighter than jam GD — Kotlin agents drown in branches) |

Enforcement mode: **touched-first hard**; repo-wide report-only until overrides cleared.

### 7.3 Detekt baseline

- Keep baseline for untouched legacy  
- **Optional burn-down waves** by rule family (LongMethod → LargeClass → MagicNumber last)  
- Never agent-default regen  
- Goal: shrink baseline count in dod-summary over time (`baseline_count` field)

### 7.4 Mutation

| Phase | Core PIT | Notes |
|-------|----------|-------|
| Now | soft 60% | exists |
| After structure wave | soft 70% | |
| Target | hard 80% pure modules | DIGEST-007/025; nightly if >45s |

## 8. Verify lanes (revised)

| Lane | Command (proposed) | Gates |
|------|-------------------|-------|
| **fast** | `./tools/verify_mud.sh` | A1–A8 touched-aware; no full reasoning suite |
| **core** | `--core` | B-tier + full engine tests + A on all engine modules |
| **full** | `--full` | C without forced PIT if slow |
| **pitest** | `--pitest` | C1 + A |
| **quarantine** | `--quarantine` | debt only |
| **agent-preflight** | new | D1–D3, D7 on plan/brief paths |

Keep single entrypoint `verify_mud.sh`; add subcheckers under `tools/quality/`.

## 9. Implementation waves (tickets to file)

Do **not** one-shot thrash. Serial waves:

### Wave Q0 — Design lock (this doc)
- Jason accepts north star + catalog + thresholds philosophy  
- Ticket: design spike acceptance only  

### Wave Q1 — Feedback shape (low thrash, high ROI)
1. **MUD-Q01** dod-summary v2 + finding code enum + schema check  
2. **MUD-Q02** `tools/check_token_budget_kt.py` report-only on all main sources + JSON  
3. **MUD-Q03** touched-path mode for token + structure (git diff / ticket paths)  
4. **MUD-Q04** wire report-only into verify; fail closed only with `MUD_TOKEN_HARD=1` pilot  

### Wave Q2 — Hard ratchet on new/touched
5. **MUD-Q05** token+structure **hard on touched** prod files (overrides list for gods)  
6. **MUD-Q06** Detekt “touched files ignore baseline” mode OR unbaseline new smells only (already) + document  
7. **MUD-Q07** no-live-LLM-in-unit-tests gate  
8. **MUD-Q08** builder preflight token gate (plans/briefs)  

### Wave Q3 — Split the gods (real refactors — product-shaped)
9. **MUD-Q09…** split tickets per oversized file (GraphGenerator, EngineGameClient, SkillQuestHandlers, GameServer, IntentRecognizer, …) — one file family per ticket; keep behavior tests green  
10. Remove overrides as each split lands  

### Wave Q4 — Strength
11. **MUD-Q20** PIT threshold raise schedule 60→70→80  
12. **MUD-Q21** duplication gate warn→hard on handlers  
13. **MUD-Q22** handler parity contract pack (inventory/combat/social)  
14. **MUD-Q23** optional headless command smoke (product phase)  

## 10. Anti-gaming rules (standing)

1. Test edits require ticket scope + lock regen (`MUD_ALLOW_TEST_CHANGES=1`)  
2. N=3 verify retries then escalate — no infinite loops  
3. No `@Suppress` / detekt ignore without ticket + comment `MUD-xxx`  
4. No baseline regen without Jason  
5. No deleting tests to pass mutation  
6. No raising override caps — only lowering  
7. Coverage % / “tests passed” alone ≠ done  
8. One problem per ticket; serial one builder  

## 11. Mapping: jam tools → mud tools

| game_jam | ai-mud target |
|----------|----------------|
| `codex_verify.py` selector | `verify_mud.sh` lanes + optional path args |
| `check_quality_gates.py` | `tools/quality/check_structure_kt.py` (+ Detekt) |
| `check_token_budget_gates.py` | `tools/quality/check_token_budget_kt.py` + process D-tier |
| `check_import_boundaries.py` | Konsist (keep) + optional JSON matrix mirror |
| `check_duplication_gates.py` | `tools/quality/check_duplication_kt.py` |
| `check_mutation_gates.py` / PIT | existing PIT lane |
| `check_test_edit_policy.py` | test_lock.sh (keep) |
| `builder_dod` / schema | dod-summary v2 |
| `config/quality/*.json` | `config/quality/*.json` (new tree) |
| fp_playable_gate | E-tier later — don’t cargo-cult Android smoke |

## 12. Success metrics (for Jason, not vanity)

| Metric | Now (approx) | 90d target |
|--------|--------------|------------|
| Core verify | PASS / ratchet | PASS / meaningful fails on bad agent edits |
| Detekt baseline IDs | ~1478 | trending down each wave |
| Prod files >2500 tok | many (e.g. 1200 LOC hosts) | 0 without override ticket |
| Override list size | n/a | → 0 |
| PIT core | soft 60% | hard ≥80% pure |
| Quarantine | 0 | 0 |
| Agent time-to-fix from JSON | Gradle log scrape | <1 screen findings[] |
| Human code reading required | still high for gods | near-zero for routine tickets |

## 13. Decisions (closed 2026-08-11)

All five open questions **accepted as recommended** — see status block. Implementation order: Q0 stamp → Q1 feedback → Q2 hard-touched → Q3 splits → Q4 strength/E-tier.

## 14. References

- LoC: `DIGEST-007`, `DIGEST-025`, `DIGEST-009`, `DIGEST-016`  
- jam: `docs/research/2026-08-03-ai-native-quality-gates-gap-report.md`, `config/quality/*`, `tools/check_*.py`  
- mud: `AGENTS.md`, `docs/DETEKT.md`, `docs/KONSIST.md`, `docs/PIT.md`, `docs/TEST_LOCK.md`, `docs/MODERNIZATION_STATUS.md`
