# MUD-025 — Modernization program closeout (PLAN)

**Status: APPROVED by Astra 2026-08-11 18:21 MST**  
**Ticket:** `issues/MUD-025-modernization-closeout.md`  
**Phase:** implementing → done (fresh impl session)  
**Depends:** MUD-022 `2b265ca` · 023 `85a1af1` · 024 `54bcce4` (all DONE+PUSHED)  
**Verify (post-impl):** `./tools/verify_mud.sh --core`  
**Worker out:** `tmp/workers/MUD-025/`

---

## 1. Goal / acceptance mapping

| Acceptance | Impl action |
|------------|-------------|
| `TEST_QUARANTINE.md` = **0** | Confirm header/count already post-022; no re-open of cleared list |
| `TESTING.md` Current Test Status real | Replace stale **621 / 23 fail** with **644 green / quarantine 0** |
| BOARD Wave G complete; Open = human_gated only | Mark 025 done; Wave G empty/complete; Open empty (harness: none) |
| `OVERNIGHT_HANDOFF.md` Live=none + complete note | Live=none; goals 022–025 done; disarm note for cron |
| One-pager modernization status | Create `docs/MODERNIZATION_STATUS.md` (gates on + residual human-only) |
| `./tools/verify_mud.sh --core` exit 0 | Run once; cite `tmp/dod-summary.json` |
| No product redesign | Docs/issues/plans only — **no `*.kt`** |

---

## 2. Current truth audit (2026-08-11 plan session)

| Source | Truth | Drift? |
|--------|-------|--------|
| Live `@Tag("quarantine")` in `*.kt` | **0** | No |
| `docs/TEST_QUARANTINE.md` | Post-MUD-022 count **0**; history retained | OK |
| `docs/TESTING.md` § Current Test Status | Still **621** green + **23** quarantine | **STALE** |
| `AGENTS.md` quarantine blurb | Still “8 tagged after MUD-021…” | **STALE** (one-line) |
| BOARD Open Wave G | Only **MUD-025** (planning) | 025 unfinished |
| BOARD Plan review | empty | → plan_review this turn |
| BOARD Scheduled | MUD-025 planning | → move to plan_review |
| BOARD Blocked | empty (harness) | OK |
| `OVERNIGHT_HANDOFF` | Live=MUD-025 planning; goals 022–024 done, 025 PLANNING | Needs post-impl close |
| `PUSHED.md` Wave G | 022–024 rows; no 025 yet | Astra post-push only |
| `KNOWN_ISSUES.md` | Product writes fixed 019/023/024; residual **PlayerState V1 fields** optional delete; MUD-007 playtest optional future | Residual note only |
| `docs/TODO.md` / `V2_REMOVAL_PLAN` Remaining | V1 field hard-delete + skills map residual; stale “23 fails” still in TODO body | Optional one-line pointer from one-pager; **do not** full rewrite TODO |
| `docs/MODERNIZATION_STATUS.md` | **missing** | Create |

**Wave G product/debt:** closed. This ticket = docs/board truth only.

---

## 3. Design / recommended approach

Docs-first closeout; **tiny surgery** only.

**One-pager outline** (`docs/MODERNIZATION_STATUS.md`, ≤~80 lines):
1. **Program complete** date + Wave A–G pointer (link BOARD / PUSHED)
2. **Gates on:** verify lanes (default/core/full/pitest/quarantine), detekt, Konsist, test-lock, CI (`verify.yml` core)
3. **Quarantine:** **0** · link `TEST_QUARANTINE.md`
4. **Product inventory path:** V2 Success writes (019 take / 023 drop / 024 give-equip-use-buy)
5. **Residual human-only / optional (not gates):**
   - PlayerState V1 **field** hard-delete (optional)
   - PIT soft→hard (out of scope; day-one soft)
   - Detekt baseline burn-down (Jason/explicit)
   - MUD-007 GUI/console playtest (Jason product phase)
6. **Posture:** harness-first; no drain on playtest opinion
7. **Verify:** `./tools/verify_mud.sh --core`

**Do not:** re-open finished tickets, rewrite CLAUDE.md, full TODO archaeology, detekt/PIT policy changes.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `docs/MODERNIZATION_STATUS.md` | **CREATE** one-pager |
| `docs/TESTING.md` | Fix § Current Test Status table + repair-wave sentence |
| `docs/TEST_QUARANTINE.md` | Confirm 0; optional AGENTS-crosslink if missing (no count rewrite if already true) |
| `AGENTS.md` | One-line quarantine count → **0** (post-022) |
| `issues/BOARD.md` | 025 → done; Wave G complete; Plan/Scheduled empty; Recently done blurb |
| `issues/OVERNIGHT_HANDOFF.md` | Live=none; program complete; agent queue empty |
| `issues/MUD-025-…md` | status done + acceptance checked (impl) |
| `tmp/workers/MUD-025/CLOSEOUT.md` | Paths, verify, residual risk, dod-summary |
| `KNOWN_ISSUES.md` / `TODO.md` / `V2_REMOVAL` | **Touch only if** one-line “program closed → MODERNIZATION_STATUS” helps; else leave (residuals already accurate) |
| `issues/PUSHED.md` | **Not** impl job — Astra after allowlisted push |

**Never this ticket:** `*.kt`, test-lock regen (no test edits), secrets, force-push.

---

## 5. Non-goals

- PIT hard threshold / score chase  
- Detekt baseline mass burn-down  
- MUD-007 playtest / product play  
- PlayerState V1 field hard-delete  
- git commit/push (Astra Wave G)  
- Re-open Wave F/G product tickets  
- Product redesign / handler changes  

---

## 6. How impl confirms acceptance

Checklist + commands:

```bash
# Live quarantine
rg -c '@Tag\("quarantine"\)' --glob '*.kt' -g '!**/build/**' || true   # expect 0 / no matches

# Doc truth greps (must NOT match stale fail narrative in status sections)
rg -n '23 `@Tag|23 fail|quarantine\) \| 23' docs/TESTING.md && exit 1 || true
rg -n 'Post-MUD-022 count:\*\* \*\*0\*\*|quarantine count \*\*0\*\*' docs/TEST_QUARANTINE.md
rg -n 'quarantine \*\*0\*\*|Post-MUD-022.*\*\*0\*\*' AGENTS.md docs/MODERNIZATION_STATUS.md

# Board / handoff
rg -n 'Live:\*\* none|program complete|Wave G.*complete' issues/OVERNIGHT_HANDOFF.md
rg -n 'MUD-025' issues/BOARD.md   # only in Recently done (not Open/Scheduled)

# Verify
./tools/verify_mud.sh --core   # exit 0; cite tmp/dod-summary.json
```

Acceptance boxes on ticket all `[x]`. Closeout lists residual optional items only.

---

## 7. Ordered impl steps

1. Re-audit live quarantine count + DEPENDS SHAs still on master (sanity).  
2. Create `docs/MODERNIZATION_STATUS.md` from outline §3.  
3. Patch `docs/TESTING.md` Current Test Status → reasoning **644** green; quarantine lane **0** / empty-set OK.  
4. Patch `AGENTS.md` quarantine one-liner → **0** after MUD-022 (keep historical “was 8/12/20/23”).  
5. Confirm `TEST_QUARANTINE.md` header already 0; no false “active debt” wording.  
6. BOARD: move MUD-025 → Recently done; clear Open Wave G + Plan + Scheduled; note Wave G complete.  
7. OVERNIGHT_HANDOFF: Live=none; 025 DONE; program complete; queue empty; cron disarm note (Astra).  
8. Ticket frontmatter `status: done`, phase done, acceptance checked; optional 1-line pointer in KNOWN_ISSUES residual → MODERNIZATION_STATUS.  
9. `./tools/verify_mud.sh --core` → 0; write `tmp/workers/MUD-025/CLOSEOUT.md`.  
10. **STOP for push** — Astra allowlist + PUSHED.md row.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Over-edit CLAUDE/TODO novels | Touch only listed status surfaces + one-pager |
| Claim product “playtest ready” | One-pager + posture: harness done ≠ play-ready |
| Stale counts elsewhere (TODO body, research spike) | Out of scope unless one-pager links “historical”; no mass rewrite |
| Accidental `*.kt` / test-lock | Docs-only discipline |
| Cron re-drains 025 after done | Handoff Live=none + BOARD empty Open; Astra disarms |
| Confusing plan_review with impl approval | This plan ≠ ship; fresh impl session |

---

**Handoff:** Astra approve this plan → **fresh** impl brief (`issues/_templates/implement-brief.md` → `tmp/workers/MUD-025/`). Do not continue plan session into product/docs ship.
