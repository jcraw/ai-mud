# MUD-034l Plan — Social/trade/treasure split (Wave Q3)

**Ticket:** MUD-034l · plan_review · grok  
**Status:** APPROVED by Astra 2026-08-12 08:34 MST → fresh IMPL session
**Paths:** `plans/2026-08-12-ai-mud-MUD-034l-social-trade-treasure-split.md` · `tmp/workers/MUD-034l/PLAN.md`  
**Depends:** MUD-034, MUD-031 · 034k done (no reopen 034a–k) · not 034m/n  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · pure moves  
**Pattern:** 034k thin facades · same-package · stable public FQCN

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract 5 hosts | Pure-move; thin public entrypoints; no features |
| 2 | Console+GUI parity (pairs) | Lockstep Social↔ClientSocial; Treasure↔ClientTreasure |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | never raise; no Added override |
| 5 | Residual ticket → `MUD-034l` | If still needed |
| 6 | New `.kt` ≤ global E | 2500 / 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none |

Trade = **app host only** (see §2 gap).

---

## 2. Inventory

| path | file_tok | loc | ov file_E | peak FN | ov fn_E |
|------|--------:|----:|----------:|---------|--------:|
| `app/.../SocialHandlers.kt` | **3491** | 404 | 3491 | intim **513** · pers 511 · ask 441 | 513 |
| `client/.../ClientSocialHandlers.kt` | **2879** | 295 | 2879 | say **557** · ask 479 · talk 345 | 557 |
| `app/.../TradeHandlers.kt` | **2545** | 281 | 2545 | buy **544** · sell 540 · list 461 | 544 |
| `app/.../TreasureRoomHandlers.kt` | **3125** | 319 | 3125 | exam **773** · take 652 · ret 606 | 773 |
| `client/.../ClientTreasureRoomHandlers.kt` | **3274** | 303 | 3274 | exam **812** · take 663 · ret 608 | 812 |

Overrides all `ticket: MUD-034`. Global E: **2500 / 1100 / 250**.

**Trade client gap:** `ClientTradeHandlers.kt` exists (file_tok **2401**, under file E) but **not** a 034l host / **no** override. Peak `handleBuy` **1171 FN_E**. **Do not touch** this ticket (hard-on-touched fails without split; no grandfather). Note residual in CLOSEOUT.

**Public keep:**  
`SocialHandlers` talk/say/emote/ask/persuade/intimidate · `ClientSocialHandlers` same + `handleCheck` stub + public `isQuestion` · `TradeHandlers` `handleTrade`/`handleListStock` · Treasure app+client take/return/examine.

**Callers:** `MudGameEngine` · `EngineGameClient` · `ItemTakeHandlers`/`ClientItemTakeHandlers`. Not `GameServerSocialHandlers` (outside family).

**Parity note:** client persuade/intimidate/check are stubs; app has full CHA — preserve stubs; no port.

---

## 3. Design / approach

Same-package flat (`app.handlers` / `client.handlers`); pure-move; fragment FN>250; no engine rewire. One ticket; stages serial.

### A. Social pair

| # | extract | notes |
|--:|---------|-------|
| 1 | `SocialDialogueHandlers` / `ClientSocialDialogueHandlers` | talk/say/emote/ask + context; client + merchantResponse |
| 2 | `SocialDispositionHandlers` | app persuade/intimidate bodies |
| 3 | client stubs | keep on thin host (or tiny stub extract) — no fill-in |
| 4 | resolve helpers | fold into dialogue **or** `*NpcResolve` |
| 5 | thin hosts | public → extract; FQCN stable |

### B. Treasure pair

| # | extract | notes |
|--:|---------|-------|
| 6 | `TreasureTakeHandlers` / `ClientTreasureTakeHandlers` | |
| 7 | `TreasureReturnHandlers` / `ClientTreasureReturnHandlers` | |
| 8 | `TreasureExamineHandlers` / `ClientTreasureExamineHandlers` | frag if still fat |
| 9 | `TreasurePedestalSupport` / `ClientTreasurePedestalSupport` | templates/names/barrier/stats; client + `emitStatusUpdate` |
| 10 | thin hosts | 3 public → extracts |

No app↔client shared module (different game types). Mirror names for review.

### C. Trade (app only)

| # | extract |
|--:|---------|
| 11 | `TradeBuyHandlers` · `TradeSellHandlers` · `TradeListStockHandlers` |
| 12 | `TradeMerchantSupport` (findMerchant · buildTemplateMap) |
| 13 | thin `TradeHandlers` public entrypoints |

Prefer **remove** all 5 overrides if ≤E; else **lower** + `ticket: MUD-034l`. Never raise; no Added override.

---

## 4. Files to create/touch

**Edit:** 5 hosts; `token_budget_kt.json` only those 5 rows.

**Create (~10–16 `.kt` ≤E):** Social dialogue/disposition (+resolve) · client dialogue mirror · Treasure take/return/examine/support pairs · Trade buy/sell/list + merchant support.

**Not:** ClientTradeHandlers · GameServerSocial · reasoning Trade/Treasure handlers · 034a–k/m/n · mass detekt · `src/test/**` · other overrides.

---

## 5. Non-goals

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · outside family · reopen 034a–k · 034m/n · fill client social stubs · app/client trade unify · features · commit/push unless Jason asks.

---

## 6. Acceptance checklist (impl)

- [ ] 5 hosts thin; bodies in same-package extracts
- [ ] Social + Treasure lockstep app↔client
- [ ] Trade app-only; ClientTrade gap in CLOSEOUT
- [ ] Public FQCN/signatures unchanged
- [ ] Client persuade/intimidate/check stubs unchanged
- [ ] `./tools/verify_mud.sh --core` = 0
- [ ] Remeasure → `tmp/workers/MUD-034l/token_remeasure.json`
- [ ] Overrides removed or lowered+`MUD-034l`; never raised; no Added
- [ ] New `.kt` ≤E (file/fn)
- [ ] No unauthorized `src/test/**`
- [ ] CLOSEOUT: paths, tokens before/after, residual risk

---

## 7. Ordered impl steps

1. Baseline → `tmp/workers/MUD-034l/token_baseline.json`
2. Social dialogue pair (+ resolve)
3. Social disposition app; client stubs stay thin
4. Treasure support + take/return/examine lockstep
5. Trade buy/sell/list + merchant → thin host
6. Remeasure; override remove or lower+`MUD-034l`
7. Stage new `.kt`; `--core` (N≤3 flaky → escalate)
8. CLOSEOUT + ticket/board done (**fresh impl**, post-APPROVED)

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Social app/client drift | Lockstep pure-move; leave stubs |
| Treasure action order | Pure-move; same call/print/emit order |
| Examine FN still >250 | Fragment format/stats |
| Accidental ClientTrade touch | Explicit non-host |
| Override raise / wrong ticket | Remeasure; lower-only; retarget 034l |
| `isQuestion` / take redirects break | Keep host FQCN |
| Detekt ID shift | Suppress carry; no mass regen |
| Serial tree | One builder; no parallel 034m/n |

---

**Handoff:** APPROVED by Astra 2026-08-12 08:34 MST. Fresh IMPL authorized — execute plan; do not re-plan unless blocked. Do not resume plan session.
