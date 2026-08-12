# MUD-024 Plan — Purge V1 inventory/equip production write paths

**Ticket:** MUD-024 · **Worker:** grok · **Phase:** implementing (APPROVED by Astra 2026-08-11 17:45 MST)  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-024-v1-inventory-write-purge.md`  
**Worker mirror:** `tmp/workers/MUD-024/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core`  
**Depends:** MUD-023 (done+pushed `85a1af1`)  
**Impl = fresh session** from IMPL_BRIEF. Do not resume the plan session.

Status: **APPROVED by Astra** — implement via fresh session + IMPL_BRIEF.md

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Inventory of prod write sites (app/client/reasoning) | §2 table + closeout grep dump |
| 2 | Every Success write uses `inventoryComponent` / equip-slot APIs only | Migrate give / equip-legacy / use / GUI buy; leave pure V2 paths alone |
| 3 | Grep gate in closeout: zero V1 Success writes in handlers | Exact greps in §6; list test-only exceptions |
| 4 | Smoke/contract green; thin tests only where path had none | Prefer pure-apply contracts for give (+ equip if extracted); regen test-lock if tests added |
| 5 | `docs/V2_REMOVAL_PLAN.md` + `docs/TODO.md` remaining-work updated | Fields may stay `@Deprecated`; note write-purge done, field-delete optional follow-up |
| 6 | `./tools/verify_mud.sh --core` exit 0 | Hard gate |

---

## 2. Current inventory (write sites; V1 vs V2)

### Already V2 Success (do not rework; only re-verify no V1 slip)
| Path | File(s) | API |
|------|---------|-----|
| Floor take | `FloorItemTakeApply` + thin handlers | `addItemInstance` |
| Floor drop | `FloorItemDropApply` + thin handlers | `removeItem` + floor dual-write |
| Treasure take/return | `TreasureRoomStateApply` / Handler | `inventoryComponent` |
| Trade buy/sell (app) | `TradeHandlers` + `TradeHandler` | `copy(inventoryComponent=…)` |
| Pickpocket | `PickpocketHandlers` | V2 component swap |
| Craft / quest rewards | `SkillQuestHandlers` (+ client twin) | `addItem` / `removeItem` on component |
| Corpse loot (console) | `ItemHandlers` loot/loot-all | `addItemInstance` / `addGoldV2` |
| Corpse (reasoning) | `CorpseManager.lootCorpse` | `inventoryComponent.addItem` |
| Death strip/respawn | `DeathHandler` | V2 starter inventory |
| Equip primary (console/GUI) | `handleEquip` V2 branch | `invComp.equip` + `copy(inventoryComponent=)` |

### V1 Success writes (MUST fix)
| Cluster | Path | Today |
|---------|------|--------|
| **Give** | `app/.../ItemHandlers.kt` `handleGive` | V1 `inventory.find` + **`removeFromInventory`** |
| **Give** | `client/.../ClientItemHandlers.kt` `handleGive` | same |
| **Give** | `app/.../GameServer.kt` `handleGive` | same + quest track |
| **Equip (multi)** | `GameServer.handleEquip` | **V1-only** `equipWeapon`/`equipArmor` (no V2 branch) |
| **Equip legacy** | `ItemHandlers` / `ClientItemHandlers` post-V2 `return` | Dead-ish (`inventoryComponent` non-null) but still **callable Success** via `equipWeapon`/`equipArmor` if V2 find fails into legacy — **delete branch** |
| **Use consumable** | same 3 surfaces `handleUse` | V1 `inventory.find` + **`useConsumable`** (writes V1 `inventory`) |
| **GUI buy** | `ClientTradeHandlers` buy loop | gold via V2 then **`addToInventory(entityItem)`** — items never land in component |

### Reads only (out of write-purge; OK keep briefly)
- Inventory display fallbacks: `player.inventory` lists in ItemHandlers / ClientItemHandlers / GameServer look
- `equippedWeapon`/`equippedArmor` display + combat narration (`CombatHandlers`, `ClientCombatHandlers`, movement examine)
- Victory inventory count
- Core `PlayerState` deprecated method **definitions** (not call sites)

### Test / non-prod
- `testbot/.../BehaviorTestBase.givePlayerItem` → `addToInventory` — exception OK if still bridging
- Unit tests constructing PlayerState with V1 fields

### V2 equip APIs (Success target)
- `InventoryComponent.equip(instance, slot)` / `.unequip(slot)` / `.removeItem(id)` (also clears equip map)
- `PlayerState.updateInventory` / `addItemInstance` / `addGoldV2`
- Pattern refs: `FloorItemTakeApply`, `FloorItemDropApply`

---

## 3. Design / recommended approach

**Prefer pure helpers for multi-surface paths; thin handler edits only for single-surface.**

1. **`GiveItemApply`** (`reasoning/.../inventory/`) — same shape as drop:
   - Resolve instance from `inventoryComponent` (id → template name → templateId; templates map)
   - Success: `removeItem` → `updateInventory`; **do not** write V1; do not require NPC to gain item storage (current give only strips player + quest DeliveredItem)
   - Failure: "You don't have that item." / no NPC → handler-level (NPC resolve stays in handler or optional in apply)
   - Thin console/GUI/GameServer `handleGive`

2. **Equip**
   - Port console V2 `handleEquip` into GameServer (templates via repo)
   - **Delete** V1 legacy branches in console/GUI (no Success via `equipWeapon`/`equipArmor`)
   - Optional pure `EquipItemApply` if duplication hurts; OK to share logic inline if <~40 lines ×3 and identical

3. **Use consumable**
   - Resolve from V2 items + template (`isConsumable` / heal); `removeItem` or `removeQuantity`; heal via `player.heal(...)` or copy health; never `useConsumable`
   - Prefer pure `UseConsumableApply` if 3 surfaces share; else thin shared private helper in reasoning

4. **ClientTradeHandlers buy**
   - Replace `addToInventory` with `addItemInstance(instance, templates)` (or `updateInventory(addItem)`); fail closed on overweight

5. **Keep read fallbacks** for display this slice (ticket allows). No field deletes.

6. **Corpse**: production loot already V2 — smoke-grep only; fix only if a V1 write is found.

---

## 4. Files to create/touch

| Action | File |
|--------|------|
| **Create** | `reasoning/.../inventory/GiveItemApply.kt` |
| **Create** | `reasoning/.../inventory/GiveItemContractTest.kt` (thin; ticket scopes tests) |
| Optional create | `UseConsumableApply.kt` (+ contract) if multi-surface shared |
| Edit | `app/.../ItemHandlers.kt` — give/equip-legacy/use |
| Edit | `client/.../ClientItemHandlers.kt` — same |
| Edit | `app/.../GameServer.kt` — give/equip/use V2 |
| Edit | `client/.../ClientTradeHandlers.kt` — buy → V2 add |
| Edit | `docs/V2_REMOVAL_PLAN.md` — Remaining Work: write purge done; field delete residual |
| Edit | `docs/TODO.md` — same residual note |
| Edit | `KNOWN_ISSUES.md` — give/V1 write residual fixed/narrowed |
| If tests | `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` → commit lock in impl |

**No:** field removal on `PlayerState`, skills map purge, mass detekt baseline, git push.

---

## 5. Non-goals

- Delete deprecated `inventory` / `equippedWeapon` / `equippedArmor` fields (follow-up if greps clean)
- Skills V1 map purge (note residual only unless trivial)
- Mass detekt baseline regen
- Trade/pickpocket redesign beyond write purge
- git commit/push (Astra Wave G)
- MUD-025 closeout docs
- Rewriting display/read fallbacks to V2-only (optional polish, not required)

---

## 6. How impl confirms acceptance

**Checklist**
- [ ] Give Success (console/GUI/GameServer): V2 remove only; quest deliver still fires
- [ ] Equip Success all surfaces: `inventoryComponent.equip` only; no `equipWeapon`/`equipArmor` call sites in app/client prod
- [ ] Use Success: no `useConsumable` in app/client prod
- [ ] GUI buy: items in `inventoryComponent.items`
- [ ] Docs + KNOWN_ISSUES residual updated
- [ ] `--core` exit 0; test-lock updated if tests added
- [ ] Closeout cites greps + `tmp/dod-summary.json`

**Exact greps (prod; expect 0 hits in app/client/reasoning main after purge)**
```bash
# V1 mutators in production handlers (main sources)
rg -n 'addToInventory|removeFromInventory|equipWeapon|equipArmor|unequipWeapon|unequipArmor|useConsumable' \
  app/src/main client/src/main reasoning/src/main --glob '*.kt'

# Direct V1 inventory list mutation
rg -n 'copy\(inventory\s*=' app/src/main client/src/main reasoning/src/main --glob '*.kt'

# Equip field writes (should be definitions-only in core PlayerState)
rg -n 'equippedWeapon\s*=' app/src/main client/src/main reasoning/src/main --glob '*.kt'
rg -n 'equippedArmor\s*=' app/src/main client/src/main reasoning/src/main --glob '*.kt'
```
Exceptions to document if any: `core/.../PlayerState.kt` method bodies; tests/testbot.

---

## 7. Ordered impl steps

1. Re-run greps; freeze write-site list in closeout draft.
2. Add `GiveItemApply` + thin contract tests (resolve + remove; missing → Failure).
3. Wire `handleGive` ×3 → apply; strip V1 find/remove.
4. Fix equip: GameServer → V2 path; delete legacy V1 equip branches console/GUI.
5. Fix use ×3 → V2 remove + heal (pure helper optional).
6. Fix `ClientTradeHandlers` buy → `addItemInstance` / component add.
7. Corpse smoke-grep; no change if already V2.
8. Docs: V2_REMOVAL_PLAN Remaining Work, TODO residual, KNOWN_ISSUES give/V1 fixed.
9. Test-lock regen if tests touched.
10. `./tools/verify_mud.sh --core` (N≤3 flaky only); closeout with grep gate + dod-summary path.
11. **STOP for Astra push** — no commit/push in plan session; impl session may commit, Wave G push after done.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Give resolves only V1 names → "don't have" after take | Resolve via templates like drop/take |
| Equip GameServer diverges from console | Copy V2 branch semantics; same failure strings |
| Use heal amounts live only on V1 Entity.Item | Read template consumable props / charges; fail closed if no heal data |
| GUI buy double-gold or weight ignore | Use same `addItemInstance` weight check as take |
| Quest deliver id mismatch (instance vs entity id) | Pass instance id from V2; confirm tracker accepts |
| Test-lock fail | Ticket scopes thin tests → regen with `MUD_ALLOW_TEST_CHANGES=1` |
| Scope creep into field delete / skills | Explicit non-goal; residual note only |
| Read fallbacks still show empty V1 inventory | Known; display polish optional — writes must not dual-write V1 to "fix" display |

**Residual after this ticket:** deprecated fields still on PlayerState; display/read greps may remain; skills map V1; field hard-delete = follow-up (possibly MUD-025 docs only, not delete).
