# MUD-036 Plan — Duplication gate on app/client handlers (Wave Q4 · C3)

**Ticket:** MUD-036 · **Phase:** plan_review (await Astra approve)  
**Plan:** `plans/2026-08-16-ai-mud-MUD-036-duplication-gate-handlers.md`  
**Mirror:** `tmp/workers/MUD-036/PLAN.md`  
**Wave artifact:** this file  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · depends **MUD-031** (done)  
Not impl this turn. No product `*.kt`. No merge of handlers (that is **MUD-037**).

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | `tools/quality/check_duplication_kt.py` (or equiv) on `app/**/handlers` + `client/**/handlers` | New Python checker; stdlib only; globs exact |
| 2 | Start **warn-only** in verify; document path to hard | `gates.duplication_kt` pass on clones; `docs/DUPLICATION_KT.md` ratchet |
| 3 | Findings JSON codes | `DUP_BLOCK_W` (live); `DUP_BLOCK_E` reserved for hard |
| 4 | `--core` green default | Warn never sets `EXIT_CODE`; missing checker → skip not fail |

---

## 2. Current inventory

| Piece | Truth |
|-------|--------|
| **Handlers** | **61** `app/.../handlers/*.kt` · **43** `client/.../handlers/*.kt` · ~40 `X`/`ClientX` stem twins |
| **Already extracted (037, keep)** | take/drop/equip/use applies + `CombatHitApply` + `EmoteApply` + contracts. Residual copy remains in IO wrappers |
| **Poster clones** | `ItemInventoryFormat` ≈ `ClientItemInventoryFormat` (near-identical body). `CombatAttackHit` / `ClientCombatAttackHit` share `resolveWeapon` / `triggerCounterAttack`; IO + `HANDS_BOTH` already drifted |
| **Out of glob** | `GameServer*.kt` live under `app/` not `handlers/`. Do not expand |
| **App-only (no Client twin)** | loot/use/pickpocket/rest/check/disposition/trade-buy-sell-list/victory/post-move — intra-app only; **v1 ignores** |
| **Checker** | **missing.** `tools/quality/` has token / no_live_llm / preflight only |
| **Verify** | optional gates via `additionalProperties` (`token_budget`, `no_live_llm_unit`). Required `known` tuple **unchanged** |
| **DESIGN C3** | `duplication_kt` warn→hard · jam-style **block clone** · map `check_duplication_gates.py` → `check_duplication_kt.py` |
| **Jam source** | not in this tree; do not vendor. Replicate: normalize → sliding window hash → report pairs |

---

## 3. Design / recommended approach

### Verdict
**New report-only checker + warn wire. Do not extract/merge handlers.**

Mirror token (MUD-028/031): checker always **exit 0**; verify owns policy. v1 policy = **always warn**.

### Algorithm (jam-style block clone, KISS)

1. Scan `app/**/handlers/**/*.kt` and `client/**/handlers/**/*.kt` only (`src/main`; skip `build/`).
2. Normalize per file: strip `/* */` and `//`; drop `package` / `import`; drop `@file:Suppress(...)` (11-line identical headers are FP); drop blanks; collapse inner ws. **Do not** rename identifiers (literal copy-paste, not AST clone).
3. Sliding window of **10** consecutive normalized lines; SHA-1 the window.
4. Report a clone **only if both sides appear** (app path + client path). Intra-app / intra-client out of v1.
5. Merge adjacent windows per `(app_file, client_file)` into one region. One finding per pair if merged lines ≥ **10**.
6. Sort by `metric` desc; cap **50** (same as token).

No CPD, no detekt-copy, no new Gradle dep, no Konsist test (avoids test-lock).

### Findings

```json
{ "code": "DUP_BLOCK_W", "path": "app/…/Foo.kt", "metric": 42, "limit": 10,
  "remediation": "clone of client/…/ClientFoo.kt (42 lines); extract shared apply or thin one side — do not merge in MUD-036" }
```

`DUP_BLOCK_E` documented, **not emitted** in v1. `path` = app file; peer in remediation.

### Checker CLI / JSON (copy token shape)

`python3 tools/quality/check_duplication_kt.py --root . --json-out tmp/duplication_kt.json`  
`--quiet-stdout` for verify. Report: `tool`, `exit_policy: report_only`, `summary` (`files_scanned`, `pairs`, `findings_warn`, `window`, `min_block`), `findings[]`.

Config `config/quality/duplication_kt.json`: `{ "window": 10, "min_block_lines": 10, "allowlist": [] }`. Allowlist = `{ "app": "…", "client": "…", "ticket": "MUD-xxx" }` for **later** hard; empty now. New pairs cannot be silently dropped.

### Verify wire

| Lane | v1 |
|------|----|
| default / fast / core / full | **run warn-only** (cheap; agents need findings on `--core`) |
| quarantine / pitest / preflight | **skip** |

- Gate name: **`duplication_kt`** (DESIGN C3). Optional; do **not** add to required schema tuple.
- `record_gate` **pass** if checker ran (even when W>0). Note: `warn-only W=n pairs=m`.
- Merge findings (cap 50) via `append_finding`.
- Missing python3 / checker → **skip** (do not brick `--core`). Crash / empty JSON → **fail** (script bug).
- Placement: after `token_budget` (or after `no_live_llm_unit`). Dry-run: add to `finalize_gates` list; help one-liner.
- **No** `--dup-hard` flag this ticket. Document env only.

### Path to hard (docs only; do not flip)

| Rung | Policy | This ticket? |
|------|--------|--------------|
| **R0 (now)** | warn-only on default/fast/core/full; `--core` never fails on clones | **yes** |
| **R1** | `MUD_DUP_HARD=1` fails **`--full` only** on `DUP_BLOCK_E` (emit E = same pairs, or W above a later cap) | no — follow-on |
| **R2** | hard default on `--full`; core stays warn until Jason/Astra | no |

Never hard-fail default/fast/core in this ticket (DESIGN C3 is Tier C). Do not force handler merges to go green.

---

## 4. Files to create/touch

| Action | Path |
|--------|------|
| Create | `tools/quality/check_duplication_kt.py` |
| Create | `config/quality/duplication_kt.json` |
| Create | `docs/DUPLICATION_KT.md` |
| Edit | `tools/verify_mud.sh` — `run_duplication_kt` / skip / dry-run / help / `write_dod_summary` optional gate / `finalize_gates` |
| Edit | `docs/DOD_SUMMARY.md` — `DUP_BLOCK_W` / `DUP_BLOCK_E` + optional `gates.duplication_kt` |
| Edit | `docs/AGENT_QUALITY_GATES_DESIGN.md` — surgical C3 “live” pointer |
| Edit | `AGENTS.md` — Verification one-liner (warn-only; not in required tuple) |
| Bookkeeping | ticket + `issues/BOARD.md` + closeout in impl |

**No:** product `*.kt`, `src/test/**`, test-lock regen, GameServer expansion, handler merges, schema `required` change, git commit/push.

---

## 5. Non-goals

- Forced merge of console/GUI handlers (037 / later extracts)
- GameServer / non-`handlers/` trees; intra-module clones
- Hard-fail on `--core` / default; inventing E thresholds that fail today
- CPD / detekt CopyPaste / new deps
- Headless smoke (038); PIT (035); token policy edits
- Live LLM; test-lock; baseline regen

---

## 6. How impl confirms acceptance

- [ ] `python3 tools/quality/check_duplication_kt.py --root .` exit 0; JSON has `DUP_BLOCK_W` for `ItemInventoryFormat` ↔ `ClientItemInventoryFormat` (or equivalent ≥10-line body clone)
- [ ] `@file:Suppress` / `import` / `package` **not** the sole reason for a finding
- [ ] Intra-app-only files (e.g. `ItemLootTake`) produce **no** finding unless a client peer shares a block
- [ ] `./tools/verify_mud.sh --core` exit **0**; `gates.duplication_kt` **pass** with `W≥1` (or honest `W=0` if measure disagrees — then fix window, do not merge handlers)
- [ ] Findings in `tmp/dod-summary.json`; cite path in closeout
- [ ] `--quarantine` / `--pitest`: gate **skipped**
- [ ] `--dry-run --core`: gate listed, checker not invoked
- [ ] Hide/rename checker → `--core` **skip** not fail
- [ ] Docs name R0→R1/R2; AGENTS one-liner; DESIGN C3 pointer
- [ ] No `src/test` / lock / product kt diffs

---

## 7. Ordered impl steps

1. Add checker + config; standalone run; sanity on Format twin + suppress-header FP.
2. Wire `run_duplication_kt` into `verify_mud.sh` (lanes, dry-run, finalize, dod optional gate, help).
3. Docs: `DUPLICATION_KT.md`, DOD_SUMMARY codes, DESIGN C3, AGENTS one-liner.
4. Smokes in §6; clean `--core`; cite `tmp/dod-summary.json`.
5. Ticket → done; BOARD; closeout `tmp/workers/MUD-036/CLOSEOUT.md`.
6. **No** commit/push unless Jason asks.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Warn flood (40 twins) | One finding **per pair**; cap 50; window 10; strip suppress/imports |
| `--core` fail on clones | Warn-only; W never sets EXIT_CODE |
| Missing checker bricks CI | Skip if missing (R0); crash still fails |
| Scope creep into merges | Ticket non-goal; remediation text says do not merge here |
| GameServer left out | Honest; glob is handlers-only |
| Structural clones miss (println vs emit) | By design (block clone); 037 contracts cover apply parity |
| Schema churn | Optional gate only |
| Token E on new `.kt` | N/A — Python + docs |

---

## Impl handoff

Fresh session after **APPROVED by Astra**. Brief: `issues/_templates/implement-brief.md` → `tmp/workers/MUD-036/IMPL_BRIEF.md`. Serial one builder. No commit/push this plan turn.

## Learn

bite: none
