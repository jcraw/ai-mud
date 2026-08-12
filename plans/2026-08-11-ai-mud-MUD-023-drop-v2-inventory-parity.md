# MUD-023 Plan — Drop path → V2 InventoryComponent parity

**Ticket:** MUD-023 · **Worker:** grok · **Phase:** implementing (APPROVED by Astra 2026-08-11 17:06 MST)  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-023-drop-v2-inventory-parity.md`  
**Worker mirror:** `tmp/workers/MUD-023/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core`  
**Depends:** MUD-019 (floor take V2 — done)  
**Impl = fresh session** from IMPL_BRIEF. Do not resume the plan session.

Status: **APPROVED by Astra** — implement via fresh session + IMPL_BRIEF.md

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Drop Success removes matching `ItemInstance` from V2 `inventoryComponent` | `removeItem(instanceId)` (also clears V2 equip slot if worn) |
| 2 | Floor/space gains entity (+ `itemsDropped`) consistent with take reverse | `Entity.Item` with `templateId`/`instanceId`/quality props + `itemsDropped` append |
| 3 | Console + GUI (+ GameServer) share pure apply helper | One `FloorItemDropApply`; thin handlers only narrative/IO |
| 4 | No Success that only mutates deprecated V1 `PlayerState.inventory` | Drop Success never calls `removeFromInventory` / V1 equip clear as sole write |
| 5 | Contract tests, mock LLM | drop → component loses item; space has entity + itemsDropped; missing → Failure |
| 6 | KNOWN_ISSUES residual | Drop residual marked fixed/narrowed (give/V1 mass purge → MUD-024) |
| 7 | Verify green | `./tools/verify_mud.sh --core` exit 0; test-lock regen if tests added |

---

## 2. Current inventory (drop paths; V1 vs V2)

### Bug shape (reverse of pre-MUD-019 take)
- **Write (drop):** V1 `player.inventory.find` + `removeFromInventory` / clear `equippedWeapon`|`equippedArmor`; `addEntityToSpace` with bare `Entity.Item`
- **Read (inventory/equip/HUD + take Success):** V2 `inventoryComponent` only after MUD-019
- Result: after take→inventory lists item, **`drop <item>` → "You don't have that"** (V1 empty); or legacy V1-only strip leaves V2 stale

### Paths found (ticket-relevant)

| Layer | Path | Today |
|-------|------|--------|
| Console | `app/.../ItemHandlers.kt` `handleDrop` | V1 find + `removeFromInventory` / V1 equip clear; `addEntityToSpace` only — **no** `itemsDropped`, **no** V2 remove |
| GUI | `client/.../ClientItemHandlers.kt` `handleDrop` | Same V1; no V1 equip fallback branch |
| Multi-user | `app/.../GameServer.kt` `handleDrop` | Same V1 + equipWeapon/Armor clear |
| Routing | `MudGameEngine` / `EngineGameClient` | `Intent.Drop` → handlers above |
| Take reverse (fixed) | `reasoning/.../inventory/FloorItemTakeApply.kt` | V2 add + remove entity + `removeDroppedItem` — **pattern to mirror** |
| Core V2 APIs | `InventoryComponent.removeItem(instanceId)` (also strips equip map); `PlayerState.updateInventory` / `addItemInstance` | Correct remove entry points; **no** `removeItemInstance` on PlayerState yet (optional thin wrapper) |
| Floor entity stamp | `LootGenerator.toEntityItems` | Props: `templateId`, `instanceId`, quality/quantity/charges; id `drop_${instance.id}` — drop should emit same shape so take round-trips |
| Death dual-write | `DeathHandler` | entity + `itemsDropped` — drop should dual-write similarly |
| Testbot | `V3TestGameEngine.handleDrop` | Partial V2 already (out of acceptance; leave or light-touch only if needed) |
| Give | same files `handleGive` | Still V1 — **out of scope residual** (MUD-024 class) |

### Match rules today vs target
- **Today:** substring on V1 `Entity.Item` name/id
- **Target (align equip + take reverse):** resolve against `inventoryComponent.items` via template name / `templateId` / instance id (templates map required); equipped instances drop via `removeItem` (already clears `equipped`)

---

## 3. Design / recommended approach

**Pure helper + thin handlers** (KISS; reverse of MUD-019 / same as MUD-007 treasure apply).

1. **`FloorItemDropApply`** under `reasoning/.../inventory/`:
   - Input: `world`, `player`, `spaceId`, `target: String`, `templates: Map<String, ItemTemplate>`
   - Resolve instance:
     - Prefer exact `instance.id` match (case-insensitive)
     - Else template name contains target (lookup via templates)
     - Else `templateId` contains target
     - Missing → `Failure("You don't have that.")` — **no** V1 fallback write
   - On Success:
     - `inv = inventoryComponent.removeItem(instance.id)` (null → Failure; also unequips V2)
     - `updatePlayer(player.updateInventory(inv))`
     - Build floor `Entity.Item` reverse of take/`toEntityItems`: id `drop_${instance.id}`, name from template (+ ` xN` if qty>1), props `templateId`/`instanceId`/quality/quantity/charges (+ weight if useful)
     - `addEntityToSpace(spaceId, entity)`
     - Append `instance` to space `itemsDropped` (dual-write for look/take parity)
   - Return sealed `Success(world, itemName, templateId, instanceId)` / `Failure(msg)`
   - **Do not** write V1 `inventory` / `equippedWeapon` / `equippedArmor` on Success (leave untouched; MUD-024 purges)

2. **Thin handlers** (`handleDrop` only):
   - Load templates from `itemRepository` (or existing map); call pure apply; assign `worldState`; narrative
   - Console: println; GUI: Narrative/System; GameServer: Triple + GenericAction broadcast
   - Strip V1 find / equipWeapon branches from Success path

3. **Tests** (`:reasoning` unit, mock LLM):
   - Seed player with V2 `ItemInstance` → drop Success → `inventoryComponent.items` empty of that id; space entity present with props; `itemsDropped` contains instance
   - Equipped V2 instance drops and `equipped` cleared
   - Missing target → Failure; world unchanged
   - Optional: drop then take round-trip via `FloorItemTakeApply` keeps same instanceId/templateId

4. **Docs:** KNOWN_ISSUES drop residual → fixed; note give/V1 field purge still residual (MUD-024)

---

## 4. Files to create/touch

| Action | File |
|--------|------|
| **Create** | `reasoning/src/main/kotlin/.../inventory/FloorItemDropApply.kt` |
| **Create** | `reasoning/src/test/kotlin/.../inventory/FloorItemDropContractTest.kt` |
| Edit | `app/.../ItemHandlers.kt` — `handleDrop` → apply |
| Edit | `client/.../ClientItemHandlers.kt` — same |
| Edit | `app/.../GameServer.kt` — same |
| Edit | `KNOWN_ISSUES.md` — drop residual fixed/narrowed |
| Regen | `tools/test-lock/manifest.sha256` if tests (`MUD_ALLOW_TEST_CHANGES=1`) |
| Bookkeep | ticket + BOARD on **impl** closeout (not this turn) |

No new modules. No give/trade/pickpocket. No V1 field deletion.

---

## 5. Non-goals

- Full V1 field deletion (MUD-024)
- Trade / pickpocket redesign
- Give / corpse strip rewrites (document residual only)
- MUD-007 treasure playtest
- Quarantine / SkillManager (MUD-022 done)
- git commit/push this plan turn; product `*.kt` this turn

---

## 6. How impl confirms acceptance

**Automated**
- [ ] Unit: drop Success → `!inventoryComponent.items.any { it.id == instanceId }`
- [ ] Unit: space has `Entity.Item` with `properties["templateId"]` / `instanceId`; `itemsDropped` has instance
- [ ] Unit: missing item → Failure; world/inventory unchanged
- [ ] Unit (optional): equipped drop clears V2 equip map entry
- [ ] Grep Success path free of V1-only strip:  
  `rg 'removeFromInventory|equippedWeapon|equippedArmor' app/.../ItemHandlers.kt client/.../ClientItemHandlers.kt app/.../GameServer.kt`  
  — drop bodies must not use these for Success (give may still until MUD-024)
- [ ] `./tools/verify_mud.sh --core` exit 0; test-lock green after regen
- [ ] KNOWN_ISSUES drop residual updated

**Manual smoke (optional; not Jason-gated)**
- [ ] Console/GUI: take floor item → inventory lists → drop → look shows item → take again

---

## 7. Ordered impl steps

1. Add failing `FloorItemDropContractTest` (Success remove+floor; Failure missing; equip clear optional).
2. Implement `FloorItemDropApply` (resolve → V2 remove → entity + itemsDropped → updatePlayer).
3. Wire console `ItemHandlers.handleDrop`; strip V1 Success path.
4. Wire GUI `ClientItemHandlers.handleDrop` identically.
5. Wire `GameServer.handleDrop` to same helper.
6. `./tools/verify_mud.sh --core`; regen test-lock if tests added.
7. Update `KNOWN_ISSUES.md`; closeout note + `tmp/dod-summary.json` path.
8. Ticket → done (agent closeout OK; no Jason playtest gate).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Name match ambiguous (two similar items) | First match like equip; document; exact instanceId preferred when known |
| Drop entity missing props → take Failure | Stamp same props as `toEntityItems` / take expects |
| Only entity write, no `itemsDropped` | Dual-write both; take clears both |
| V1 equip still on PlayerState | Do not dual-clear V1 on Success (MUD-024); V2 `removeItem` is source of truth |
| GameServer skipped → multi-user broken | Include in wire step |
| Give still V1 | Explicit residual; not acceptance |
| Test-lock fail-closed | Ticket-scoped regen with `MUD_ALLOW_TEST_CHANGES=1` |
| Over-extract | Helper ~40–80 lines; no new framework |
