# MUD-031 Plan — Token/structure hard-on-touched default (Wave Q2)

**Ticket:** MUD-031 · **Worker:** grok · **Phase:** plan_review  
**Status:** APPROVED by Astra 2026-08-12 01:26 MST
**Prior:** PLAN ONLY — fresh impl authorized.  
**Paths:** `plans/2026-08-12-ai-mud-MUD-031-token-hard-on-touched.md` · mirror `tmp/workers/MUD-031/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` · **Depends:** MUD-030 done+pushed `d8463f4` (bookkeep `344cc2d`)  
**STOP** — no product/impl this session.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Deliver |
|---|------------|---------|
| 1 | default/fast/core hard-fail `*_E` on **touched** prod `.kt` | Invert 030: hard default, scoped git-diff only |
| 2 | Named overrides + burn-down ticket each | Apply overrides in checker; fill gods with `ticket: MUD-034` |
| 3 | New files cannot use overrides | Ignore override for Added/new-at-base paths |
| 4 | Caps may only lower | Doc rule AGENTS + DESIGN anti-gaming |
| 5 | Full-repo report soft; not hard w/o flag | `MUD_TOKEN_SCOPE=full` always soft; hard forces scoped |
| 6 | AGENTS + DESIGN updated | Lane table hard-on-touched; §7.1 pointer |
| 7 | `--core` green clean tree | Empty touch → pass; overrides protect first-touch gods |

---

## 2. Current inventory

| Piece | State |
|-------|--------|
| Verify 030 | Soft default on default/fast/core/full; hard via `MUD_TOKEN_HARD`/`--token-hard` scoped `*_E`; quarantine+pitest skip; findings cap ~50; gate `token_budget` |
| Checker | Always exit 0; verify owns hard — **keep** |
| Overrides | `overrides: {}` **loaded, never applied** (impl gap) |
| God signal | ~251 prod kt; **55** file_token≥2500 candidates; full soft E≈567 W≈226; tops ~9–14k tok (EngineGameClient, GraphGenerator, IntentRecognizer, MudGameEngine, GameServer, SkillQuest*). No path dump. |
| Clean tree | Empty git-diff → 0 findings → hard would already pass |
| Residual 030 | Untracked new `.kt` not in git-diff; full soft noisy |

---

## 3. Design / recommended approach

### Default hard-on-touched
- **Hard-by-default:** default / fast / core (ticket). **full lane** also hard when scope=touched (same 030 path; never full-repo hard).
- **Soft opt-out:** `MUD_TOKEN_SOFT=1` or `--token-soft` → report-only.
- **Back-compat:** `MUD_TOKEN_HARD=1` / `--token-hard` still accepted (redundant hard).
- **Scope:** `--git-diff` vs `MUD_TOKEN_GIT_BASE` (def `origin/master`). Hard+`SCOPE=full` → force scoped + note.
- **Skip:** quarantine, pitest, dry-run — unchanged.
- **Fail:** any `*_E` in touch set → EXIT 1. `*_W` never hard-fails. Empty touch → pass. Crash closed under hard.

### Override schema + apply (checker)
```json
"overrides": {
  "path/God.kt": {
    "ticket": "MUD-034",
    "tokens": { "file": { "warn": W, "error": E } },
    "structure": { "file_loc": { "warn": W, "error": E } }
  }
}
```
- **Required** `ticket` ~ `MUD-\d+`; missing → ignore entry + stderr note.
- **Apply in `analyze_file`:** merge path caps before thresholds. Override E compare as **`metric > limit`** so measured size holds; global stays `>=`.
- **Fill:** all **55** candidates; `ticket: MUD-034` (umbrella OK). Raise fn caps to max measured in-file if first-touch would still cliff on fn E. No product splits.
- **Burn-down:** children later replace ticket ids as splits land.

### New-file ban
- New = git-diff **Added** or missing at base. **Ignore overrides** for those paths (anti same-PR grandfather). Untracked still invisible (stage; residual 029).

### Full-repo soft
- `MUD_TOKEN_SCOPE=full` → inventory soft only. Standalone checker remains exit 0.

### Clean-tree green
- Empty touch → hard pass without overrides. Overrides exist so **touching** a god under default hard does not cliff until MUD-034+.

### Flag migration 030→031

| 030 | 031 |
|-----|-----|
| Soft default | **Hard** default (touched E) |
| `MUD_TOKEN_HARD` / `--token-hard` | Keep (redundant) |
| — | **`MUD_TOKEN_SOFT` / `--token-soft`** opt-out |
| `SCOPE=full` soft | unchanged soft-only |

### Docs
- AGENTS Verification: hard-on-touched + soft opt-out + lower-only one-liner.
- DESIGN: surgical Q2 hard live + lower-only (already #6).
- TOKEN_BUDGET_KT: replace pilot soft table; override schema; new-file ban.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/verify_mud.sh` | Hard default; soft opt-out; usage |
| `tools/quality/check_token_budget_kt.py` | Apply overrides; ticket req; new-file ban |
| `config/quality/token_budget_kt.json` | Fill 55 + `ticket: MUD-034` + measured caps |
| `docs/TOKEN_BUDGET_KT.md` | Hard-default policy |
| `docs/AGENT_QUALITY_GATES_DESIGN.md` | Surgical hard-on-touched live |
| `AGENTS.md` | Verification token lines |
| Ticket/BOARD/closeout | impl session |

**No:** product `*.kt`, checker non-zero exit, git commit/push.

---

## 5. Non-goals

God splits (034+) · PIT 80% (035) · no-live-LLM (032) · plan preflight (033) · 036–038 · full-repo hard · untracked auto-include · product kt · commit/push.

---

## 6. Acceptance confirmation

- [ ] Clean `--core` → exit 0; `token_budget` pass; hard note `E=0`
- [ ] Staged E-tier prod kt + default hard → exit 1; clean up
- [ ] Same + `MUD_TOKEN_SOFT=1` → exit 0 report-only
- [ ] Listed god under override at measured size → no file E cliff
- [ ] New/Added over-budget + override present → override ignored → hard E
- [ ] `SCOPE=full` → soft pass (noisy OK)
- [ ] AGENTS + DESIGN + TOKEN_BUDGET_KT updated
- [ ] Quarantine skips token

---

## 7. Ordered impl steps

1. Checker: override merge + ticket + new-file ban; CLI probes (avoid test-lock churn).
2. Config: scripted fill 55 candidates from scan + measured caps + `MUD-034`.
3. `verify_mud.sh`: hard default + soft opt-out + help.
4. Docs: TOKEN_BUDGET_KT, AGENTS, DESIGN surgical.
5. Smoke §6; clean `--core` green.
6. Closeout (impl session).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Override thrash | 55 only; 034 shrinks; no ad-hoc without ticket |
| Untracked new `.kt` | Document stage; residual 029 |
| Missing `origin/master` | Existing base fallback |
| First-touch fn E on gods | Raise max-fn caps in fill |
| AGENTS drift | Same session lane/soft names |
| Soft-opt abuse | Escape hatch only; hard is DoD |
| Cap raise attempts | Doc: only lower |

---

## Handoff

Approve → fill implement-brief → **fresh** impl (do not resume plan session).  
**verify:** `./tools/verify_mud.sh --core` · **Unblocks:** 032, 034, 036–038.
