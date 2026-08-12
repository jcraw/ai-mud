# MUD-028 Plan — Kotlin token/structure report-only checker

**Ticket:** MUD-028 · **Worker:** grok · **Phase:** implementing  
**Status:** **APPROVED by Astra 2026-08-11 23:13 MST** (common-sense). Fresh IMPL only — do **not** resume plan session.  
**Plan:** `plans/2026-08-11-ai-mud-MUD-028-token-budget-kt-report.md` · **Mirror:** `tmp/workers/MUD-028/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` · **Depends:** MUD-027 (done+pushed)

---

## 1. Goal / acceptance mapping

| # | Acceptance | Delivers |
|---|------------|----------|
| 1 | `tools/quality/check_token_budget_kt.py` | Stdlib Python3 CLI; scan prod `*/src/main/**/*.kt` |
| 2 | `config/quality/token_budget_kt.json` | File tok W2000/E2500; fn W200/E250; structure A7 (fn LOC W55/E90; cyclo W10/E16; cognitive W15/E25; file LOC W700/E1100) |
| 3 | Report-only exit 0 | Even with E breaches |
| 4 | JSON findings stdout/`tmp/` | `{code,path,metric,limit,remediation}` + summary |
| 5 | God-file override candidates | List files over file-tok E (and/or file-LOC E); config `overrides{}` empty |
| 6 | Short docs + DESIGN link | `docs/TOKEN_BUDGET_KT.md` |
| 7 | `--core` green | **Not** hard-wired to verify (MUD-030) |
| 8 | No mass splits | Tooling/docs only; no product `*.kt` |

---

## 2. Current inventory

**`src/main` modules:** `action`, `app`, `client`, `config`, `core`, `llm`, `memory`, `perception`, `reasoning`, `testbot`, `utils`. Exclude `buildSrc/**`.

| Piece | Truth |
|-------|--------|
| `tools/verify_mud.sh` | Live; empty `findings[]` until token family wires |
| `config/quality/dod_summary.schema.json` | v2 finding shape (MUD-027) |
| `docs/DOD_SUMMARY.md` | Codes reserved `TOKEN_*` / `STRUCTURE_*` |
| `tools/quality/` | **Missing** — create |
| Token checker | **None** (jam process-doc only; invent Kotlin-src tool) |

Known gods (>>2500 tok): `EngineGameClient`, `GraphGenerator`, `IntentRecognizer`, engines/handlers — report as candidates only.

---

## 3. Design / approach

**Minimal:** one Python3 stdlib script + JSON config + short doc. No Gradle plugin, no detekt wire, no product edits.

- **Tokens:** `max(0, (len+3)//4)` ≡ ceil(chars/4); raw UTF-8 source (no comment strip).
- **Scope:** `*/src/main/**/*.kt`; exclude `build/`, `src/test/**`, `buildSrc/**`.
- **Fn spans (heuristic):** regex `fun`/`suspend fun` + brace match; not full PSI. Document caveats.
- **Structure:** LOC exact; cyclo/cognitive **keyword/nest heuristics** (if/when/for/while/catch/`&&`/`||`); secondary; never affect exit.
- **Config:** thresholds above; `overrides: {}` schema-ready (`path` → raised caps + optional `ticket`); **do not auto-fill**.
- **Codes:** `TOKEN_FILE_W/E`, `TOKEN_FN_W/E`, `STRUCTURE_FILE_LOC_W/E`, `STRUCTURE_FN_LOC_W/E`, `STRUCTURE_CYCLO_W/E`, `STRUCTURE_COGNITIVE_W/E`. Path for fn: `file.kt:line` or `file.kt#name`.
- **CLI:** `python3 tools/quality/check_token_budget_kt.py [--root .] [--config …] [--json-out tmp/token_budget_kt.json]` → always exit 0. Envelope: `tool`, `exit_policy: report_only`, `summary`, `findings[]`, `override_candidates[]`. Cap per-file fn rows (top N) if noisy; always keep all file-level.
- **Do not** write/merge `tmp/dod-summary.json` (verify owns; MUD-030).
- **Docs:** `docs/TOKEN_BUDGET_KT.md` (run, thresholds, formula, heuristics, override policy → DESIGN §7.1). Surgical DESIGN A6/A7 or §7.1 tool path. Optional one line in `DOD_SUMMARY.md`.
- **Verify:** no hard path. Optional header comment only if free.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/quality/check_token_budget_kt.py` | **Create** |
| `config/quality/token_budget_kt.json` | **Create** |
| `docs/TOKEN_BUDGET_KT.md` | **Create** |
| `docs/AGENT_QUALITY_GATES_DESIGN.md` | Surgical pointer |
| `docs/DOD_SUMMARY.md` | Optional 1 line |
| Ticket / BOARD | plan_review → later impl |

**No:** `*.kt` product, detekt baseline, test-lock, verify hard gate, commit/push.

---

## 5. Non-goals

Hard fail (031) · touched-only (029) · verify/`MUD_TOKEN_HARD` (030) · god splits · full Kotlin parse · auto overrides · live LLM · detekt baseline · git commit/push.

---

## 6. Impl acceptance checklist

- [ ] Script exit **0** with/without breaches
- [ ] Multi-module scan (core+app+client present)
- [ ] Config numbers match ticket/DESIGN
- [ ] Findings use reserved codes; metric/limit set
- [ ] `override_candidates` non-empty on current tree
- [ ] Docs + DESIGN link
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] No product `*.kt` diffs

---

## 7. Ordered impl steps

1. Add `token_budget_kt.json` (thresholds; empty overrides).
2. Implement checker: discover → file metrics → fn spans → heuristics → findings/candidates → JSON + exit 0.
3. Smoke-run; fix globs/encoding; cap noisy fn lists if needed.
4. Docs + DESIGN (+ optional DOD_SUMMARY) pointers.
5. `./tools/verify_mud.sh --core`; closeout with dod-summary path.
6. Ticket → done in **fresh** impl session.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Fn regex misses/generics/comments | Document; report-only |
| Noisy cyclo/cognitive | Label heuristic; tokens primary |
| Huge FN dump | Top-N per file + file-level complete |
| Accidental verify hard-wire | Explicit non-goal |
| Empty overrides later surprise hard mode | Candidates list prepares MUD-031/034 |

---

**Handoff:** APPROVED → fresh impl + implement-brief. **STOP** — no implementation this session.
