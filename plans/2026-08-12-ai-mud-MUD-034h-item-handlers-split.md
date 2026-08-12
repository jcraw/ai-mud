# MUD-034h Plan — Item handlers parity split (Wave Q3)

**Ticket:** MUD-034h · plan_review · grok  
**Status:** APPROVED by Astra 2026-08-12 06:53 MST → fresh IMPL session
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034h-item-handlers-split.md` · `tmp/workers/MUD-034h/PLAN.md`  
**Depends:** MUD-034, MUD-031 · 034a–g done (do not reopen)  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · pure moves; no features  
**Baseline:** `tmp/workers/MUD-034h/token_baseline.json`  
**Pattern:** 034e pure-move facades + lockstep (`tmp/workers/MUD-034e/CLOSEOUT.md`)

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract both hosts, behavior-preserving | Pure-move clusters; thin public facades; no item gameplay change |
| 2 | Console+GUI **parity** | Lockstep app↔client; keep public `handle*` on hosts → zero dispatch churn |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | `--files <touched>`; never raise; no Added override |
| 5 | Residual ticket → `MUD-034h` | If host still needs override |
| 6 | New `.kt` ≤ global E | tok 2500 / LOC 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none |

---

## 2. Inventory

| path | file_tok | loc* | ov file_E | ov peak fn_E | ticket |
|------|--------:|-----:|----------:|-------------:|--------|
| `app/.../handlers/ItemHandlers.kt` | **6253** | 583 | 6253 | handleLoot **1352** | MUD-034 |
| `client/.../handlers/ClientItemHandlers.kt` | **4667** | 402 | 4667 | handleInventory **1084** | MUD-034 |

\*token-tool `file_loc` (wc: 658/451). Global E: 2500 / 1100 / 250. Ranked #13/#18.

**Peak FN tok:** App Loot 1352 · LootAll 997 · Inv 933 · Take 515 · TakeAll 411 · format 401 · Equip 379 · Give 321 · Use 230. Client Inv 1084 · Take 674 · TakeAll 541 · Equip 466 · Use 454 · format 401 · Give 363 · Drop 212.

**App public (MudGameEngine — keep on host):** Inventory · Take · TakeAll · Drop · Give · Equip · Use · Loot · LootAll (+ private formatItemInfo; package floorTake/DropTemplates).

**Client public (EngineGameClient):** Inventory · Take · TakeAll · Drop · Give · Equip · Use (+ same helpers).

**Parity gaps (do not “fix”):**
- Loot/LootAll **app-only**. Live `Intent.LootCorpse` → `CorpseHandlers.handleLootCorpse` (out of family). Client still stubs. Pure-move loot only; no rewire/delete.
- `GameServerItemHandlers` / `GameServerItemTake` out of family.
- Existing `ItemUseHandlers.kt` ≠ consumable `handleUse` — avoid name collision.

**Floor helpers:** takeTemplates → take/takeAll; dropTemplates → drop/give/use. Keep package-`internal`; co-locate with floor extract or tiny shared helper per package.

---

## 3. Design / approach

**Principles:** same packages `com.jcraw.app.handlers` / `com.jcraw.mud.client.handlers` (flat); thin facade hosts keep public `handle*`; pure-move to sibling objects; fragment FN>250; lockstep pairs; `@file:Suppress` carry; no mass detekt.

| step | cluster | app | client | notes |
|-----:|---------|-----|--------|-------|
| 1 | Inventory+format | `ItemInventoryHandlers` (+frag) | `ClientItemInventoryHandlers` (+frag) | client peak 1084 |
| 2 | Floor take | `ItemTakeHandlers` (+frag) | `ClientItemTakeHandlers` (+frag) | take+takeAll+floorTake |
| 3 | Drop/give | `ItemDropGiveHandlers` | `ClientItemDropGiveHandlers` | +floorDrop or `*ItemFloorTemplates` |
| 4 | Equip | `ItemEquipHandlers` | `ClientItemEquipHandlers` | |
| 5 | Use consumable | `ItemConsumableHandlers` | `ClientItemConsumableHandlers` | **not** ItemUseHandlers |
| 6 | Loot | `ItemLootHandlers` (+frag) | _(none)_ | app-only; leave CorpseHandlers |
| 7 | Thin hosts | residual facades | residual facades | prefer **remove** both overrides |

Facades stay sole dispatch targets (one-liner delegates). Residual host >E → lower override + `ticket: MUD-034h` (prefer remove).

---

## 4. Files to create/touch

**Edit:** both hosts; `config/quality/token_budget_kt.json` **only** those 2 override rows.

**Create (≤E each; names flexible):** Inventory(+frag) / Take(+frag) / DropGive / Equip / Consumable / app Loot(+frag) — ~10–16 `.kt`; optional floor-templates helper files.

**Not:** MudGameEngine/EngineGameClient logic (unless forced), GameServer* item, CorpseHandlers, ItemUseHandlers, Trade/Treasure/Pickpocket, 034i–n, mass detekt, `src/test/**`, other overrides.

**Overrides:** remove if ≤ global E; else lower to remeasured peaks + `ticket: MUD-034h`; never raise; no Added. Stage new `.kt` for hard-on-touched.

---

## 5. Non-goals

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · outside family · reopen 034a–g · 034i–n · wire LootCorpse · delete loot · merge Corpse/GameServer · features · commit/push unless Jason asks.

---

## 6. Acceptance checklist (impl)

- [ ] Hosts = thin public `handle*` only; bodies in extracts
- [ ] Paired clusters Inventory/Take/DropGive/Equip/Consumable; Loot app-only
- [ ] Dispatch signatures/call sites unchanged (facade one-liners)
- [ ] `./tools/verify_mud.sh --core` = 0
- [ ] Remeasure → `tmp/workers/MUD-034h/token_remeasure.json`
- [ ] Overrides removed or lowered+`MUD-034h`; never raised; no Added
- [ ] New `.kt` file_tok≤2500, peak fn≤250
- [ ] No unauthorized `src/test/**`
- [ ] CLOSEOUT: paths, before/after tokens, residual risk

---

## 7. Ordered impl steps

1. Confirm baseline JSON  
2. Inventory pair (+format; fragment) → compile `:app`+`:client`  
3. Take pair → Drop/Give pair  
4. Equip pair → Consumable pair  
5. App Loot multi-file fragment  
6. Collapse hosts to delegates; remeasure  
7. Override remove or lower+retarget `MUD-034h`  
8. Stage new `.kt`; `--core` (N≤3 flaky then escalate)  
9. CLOSEOUT + ticket/board done (**fresh impl session**, post-APPROVED)

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| App/client drift | Lockstep; pure-move; keep side-effect order |
| FN_E on Added (Loot/Inv) | Fragment; no grandfather |
| Call-site thrash | Keep facade `handle*` names |
| Name clash with ItemUseHandlers | Use `ItemConsumableHandlers` |
| “Fix” loot routing/dead code | Pure-move; leave CorpseHandlers + client stub |
| Out-of-family thrash | Only this family + 2 override rows |
| Override raise / wrong ticket | Remeasure first; lower-only; retarget 034h |
| Serial tree | One builder; no parallel 034i on same checkout |
| Detekt ID shift | `@file:Suppress` carry; no mass regen |

---

**Handoff:** APPROVED by Astra 2026-08-12 06:53 MST. Fresh IMPL authorized — execute plan; do not re-plan unless blocked.
