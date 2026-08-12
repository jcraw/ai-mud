# MUD-030 Plan — Wire token/structure into verify (pilot hard flag)

**Ticket:** MUD-030 · **Worker:** grok · **Phase:** plan_review  
**Status:** PLAN ONLY — not impl approval. Astra/Jason APPROVED → **fresh** impl session.  
**Plan:** `plans/2026-08-12-ai-mud-MUD-030-verify-wire-token-pilot.md` · **Mirror:** `tmp/workers/MUD-030/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` · **Depends:** MUD-029 (done+pushed `ba06d2d`)  
**Handoff:** plan_review → approve → fresh IMPL brief. **STOP** — no implementation this session.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Plan delivers |
|---|------------|---------------|
| 1 | default/fast/core/full runs token/structure **report-only**; findings in dod-summary v2 | `run_token_budget` after test-lock; merge checker `findings[]` → `append_finding` |
| 2 | `MUD_TOKEN_HARD=1` and/or `--token-hard` fails closed on **error-tier** (`*_E`) for **scoped** paths | Soft default; hard only on E-tier after `--git-diff` (touched) scan |
| 3 | Pilot documented; default soft | `docs/TOKEN_BUDGET_KT.md` + brief `DOD_SUMMARY` note; no hard-default (031) |
| 4 | AGENTS Verification one-liner | Token gate soft + pilot hard env/flag |
| 5 | `--core` exit 0 default soft | Soft never sets EXIT_CODE from token alone |
| 6 | Quarantine skips token | Same lane guard as detekt/konsist/test-lock |

---

## 2. Current inventory

| Piece | State |
|-------|--------|
| **Lanes** | default≡fast, core, full, pitest, quarantine |
| **Hard gates (non-quarantine)** | detekt, konsist, test_lock; pitest only on `--pitest` |
| **Findings** | `FINDINGS_JSON_PARTS` + `append_finding CODE PATH METRIC LIMIT REMEDIATION`; emit always; empty OK |
| **dod-summary** | v2; fixed gates compile/tests/detekt/konsist/test_lock/pitest; schema allows `additionalProperties` on gates |
| **Checker** | `tools/quality/check_token_budget_kt.py` — always exit **0**; `--files` / `--git-diff` / `--git-base` (def `origin/master`); `--json-out` + `--quiet-stdout`; codes `TOKEN_*_{W,E}`, `STRUCTURE_*_{W,E}` |
| **PIT soft/hard precedent** | soft note; `MUD_PITEST_HARD=1` fails — mirror for token |
| **Not wired** | verify never invokes checker; findings stay `[]` until 030 |

---

## 3. Design / recommended approach

### When to run
- **Run:** `default` / `fast` / `core` / `full` (ticket).  
- **Skip:** `quarantine` (required).  
- **pitest:** **skip** (ticket does not list it; KISS; optional stretch parity later).  
- **dry-run:** do not invoke checker; `token_budget` gate → `skipped` + note `dry-run`.  
- Placement: after test-lock block (~line 918), before PIT skip section / `write_dod_summary`.

### Scope (exact)
| Mode | Scope flags | Rationale |
|------|-------------|-----------|
| **Default soft** | `--git-diff --git-base "${MUD_TOKEN_GIT_BASE:-origin/master}"` | Touched prod `src/main/**/*.kt` only; empty touch → 0 findings, exit 0 |
| **Hard pilot** | **Same scoped git-diff** (never full-repo hard) | Avoid god-file cliff; only fail on E-tier in touch set |
| **Optional full soft** | `MUD_TOKEN_SCOPE=full` → no `--git-diff` | Inventory noise OK under soft only; hard+full → refuse / force scoped + note |

- Untracked new `.kt`: still **not** in git-diff (029); agents must stage or use future hook — document; no auto-`ls-files` in 030.
- Missing base: checker already falls back master → HEAD~1 + one stderr warn; still exit 0.

### Soft vs hard exit (verify owns policy; checker stays report_only)
| | Soft (default) | Hard (`MUD_TOKEN_HARD=1` **or** `--token-hard`) |
|--|----------------|--------------------------------------------------|
| Run checker | yes | yes |
| Merge findings W+E | yes | yes |
| Gate status | `pass` + note `E=n W=m scope=touched report-only` | `fail` if any finding code ends `_E`; else `pass` |
| `EXIT_CODE` | **never** from token | set **1** if any `*_E` |
| Checker crash / missing py/script | note + gate `skipped` or `pass` w/ note (no cliff) | fail closed (`EXIT_CODE=1`) |

Error-tier = codes matching `*_E` / severity error (`TOKEN_FILE_E`, `TOKEN_FN_E`, `STRUCTURE_*_E`). Warn (`*_W`) never hard-fails in pilot.

### Findings merge
1. `python3 tools/quality/check_token_budget_kt.py --root . --git-diff [--git-base …] --quiet-stdout --json-out tmp/token_budget_kt_verify.json`  
2. Parse report with small python3 (or bash+python) loop → `append_finding` per row (`code`,`path`,`metric`,`limit`,`remediation`; drop optional `name`).  
3. Cap merge at **~50** rows (note if truncated) to protect dod-summary size.  
4. Side JSON under `tmp/` gitignored; not committed.

### Gate key
- Add optional **`token_budget`** gate via `record_gate` + emit in `write_dod_summary` (after pitest or before close of gates object).  
- Schema: keep 6 **required** keys; `additionalProperties` already allows `token_budget`.  
- `finalize_gates` / dry-run: fill `skipped` when not run (quarantine / pitest / dry-run).  
- `validate_dod_summary`: only requires fixed 6 — no schema edit **required**; optional doc note only.

### Env + CLI
- Env: `MUD_TOKEN_HARD=1` (truthy).  
- Flag: `--token-hard` (parse next to `--dry-run` / lane flags).  
- Optional: `MUD_TOKEN_GIT_BASE`, `MUD_TOKEN_SCOPE=full|touched` (default touched).  
- Help text: soft default; pilot hard; quarantine skip.

### Docs
- `docs/TOKEN_BUDGET_KT.md`: replace “does not fail verify yet” with verify wire + soft/hard table + scope.  
- `docs/DOD_SUMMARY.md`: findings no longer always empty; pointer to token merge.  
- `AGENTS.md` Verification: one-liner after test-lock/PIT sentence.  
- No DESIGN essay rewrite (optional one-line Q04 done pointer if cheap).

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/verify_mud.sh` | `run_token_budget`; lane guard; soft/hard; findings merge; usage; optional gate emit/finalize |
| `docs/TOKEN_BUDGET_KT.md` | Verify pilot section |
| `docs/DOD_SUMMARY.md` | findings wired note |
| `AGENTS.md` | Verification one-liner |
| Ticket / BOARD / closeout | impl session bookkeep (not this plan turn beyond plan_review) |

**No:** product `*.kt`, checker exit-policy change, schema hard-required new gate, overrides auto-fill, god splits, git commit/push.

---

## 5. Non-goals

- Hard default / hard-on-touched permanent policy → **MUD-031**  
- God file splits / override auto-fill  
- Live-LLM unit ban (**032**), plan preflight (**033**)  
- Product `*.kt`; git commit/push  
- Changing checker to non-zero exit (verify owns hard)

---

## 6. How impl confirms acceptance

- [ ] Soft default: `./tools/verify_mud.sh --core` → exit **0**; `tmp/dod-summary.json` has `findings` array (maybe empty if clean touch) + `gates.token_budget.status` pass/skipped appropriately  
- [ ] Soft with known dirty god: findings present, exit still 0  
- [ ] Hard: `MUD_TOKEN_HARD=1 ./tools/verify_mud.sh --fast` (or `--token-hard`) on tree with only docs touch → exit 0 (empty prod kt); with synthetic/touched E-tier path → exit 1 + `*_E` in findings  
- [ ] `./tools/verify_mud.sh --quarantine` → no token run; gate skipped / findings not from full-repo token  
- [ ] Help mentions `--token-hard` / soft default  
- [ ] AGENTS one-liner present  
- [ ] TOKEN_BUDGET_KT documents pilot + scope  
- [ ] No product `*.kt` in diff  

**Smoke (impl session):**
```bash
./tools/verify_mud.sh --core
MUD_TOKEN_HARD=1 ./tools/verify_mud.sh --fast   # expect 0 if no prod-kt E on touch
./tools/verify_mud.sh --quarantine              # token skipped
python3 -c "import json;d=json.load(open('tmp/dod-summary.json')); assert d['schema_version']==2; print(d.get('gates',{}).get('token_budget'), len(d['findings']))"
```

---

## 7. Ordered impl steps

1. Parse `--token-hard`; read `MUD_TOKEN_HARD` / scope env at top of `verify_mud.sh`.  
2. Implement `run_token_budget` (invoke checker, time duration, merge findings, soft/hard status).  
3. Call from non-quarantine default/core/full path; skip quarantine (+ pitest).  
4. Extend `write_dod_summary` + `finalize_gates` (+ dry-run loop) for `token_budget` gate.  
5. Update `usage()` help.  
6. Docs: TOKEN_BUDGET_KT, DOD_SUMMARY, AGENTS one-liner.  
7. Smoke checklist above; write closeout under `tmp/workers/MUD-030/`.  
8. Ticket → done only after green verify (fresh session); no push unless Jason.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Full-repo soft noise | Default **touched** (`--git-diff`); full only via `MUD_TOKEN_SCOPE=full` soft |
| Missing `origin/master` | Checker fallback + warn; still soft-pass |
| Hard pilot false-fail on gods | Hard uses **scoped** touch only; don’t hard full-repo; 031 owns default hard |
| dod-summary size | Cap ~50 findings; prefer git-diff |
| Quarantine double-skip | Single `LANE != quarantine` guard; pitest also skip |
| Untracked new `.kt` invisible | Document 029 rule; agents pass files / stage |
| Schema / validate | Keep 6 required gates; optional 7th via additionalProperties |
| Checker always 0 | Parse JSON for E-count; don’t trust process exit for hard |

---

**End plan.** Approve → fresh impl; do not resume this plan session for product/tooling edits.


---
Status: APPROVED by Astra 2026-08-12 00:48 MST
