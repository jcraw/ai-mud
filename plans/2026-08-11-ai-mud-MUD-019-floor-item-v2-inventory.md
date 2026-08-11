# MUD-019 Plan — Floor-item take → V2 InventoryComponent parity

**Ticket:** MUD-019 · **Worker:** grok · **Phase:** plan_review (await Astra approve)  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-019-floor-item-v2-inventory.md`  
**Worker mirror:** `tmp/workers/MUD-019/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core`  
**Depends:** MUD-007 (treasure pure-apply pattern; playtest still Jason-gated)  
**Impl = fresh session** after Astra/Jason approve. Do not resume this plan session.

Status: **PLAN ONLY** — not impl approval

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Floor take Success → V2 `InventoryComponent` | `ItemInstance` with expected `templateId` on player after take |
| 2 | Console + GUI parity | Both call one pure apply helper (not divergent dual writes) |
| 3 | Inventory / equip / wield see item | Display+equip already V2-first; take write must match |
| 4 | Contract tests, mock LLM | Unit: take → `inventoryComponent.items` contains `templateId` |
| 5 | Verify green | `./tools/verify_mud.sh --core` exit 0; test-lock regen if tests touch |
| 6 | KNOWN_ISSUES residual | Floor path marked fixed/narrowed (drop/legacy residual if any) |

---

## 2. Current inventory (V1 vs V2 take paths)

### Bug shape (same class as pre-MUD-007 treasure)
- **Write (floor take):** V1 `PlayerState.addToInventory(Entity.Item)` → deprecated `inventory: List<Entity.Item>`
- **Read (inventory/equip/HUD):** V2 `inventoryComponent` first (non-null default empty)
- Result: Success narrative, empty list / “You don't have that”

### Paths found (ticket-relevant)

| Layer | Path | Today |
|-------|------|--------|
| Console floor take | `app/.../ItemHandlers.kt` `handleTake` / `handleTakeAll` | `removeEntityFromSpace` + `addToInventory` (V1); console also `removeDroppedItem` if `properties["instanceId"]` |
| GUI floor take | `client/.../ClientItemHandlers.kt` same names | Same V1 write; no `removeDroppedItem` |
| Multi-user | `app/.../GameServer.kt` `handleTake` / `handleTakeAll` | Same V1 write (include for parity; not multi-user treasure retest) |
| Routing | `MudGameEngine` / `EngineGameClient` | `Intent.Take` → floor handler; treasure room delegates first when unlooted |
| Treasure (fixed MUD-007) | `TreasureRoomHandler` + `TreasureRoomStateApply` | V2 `addItem` + shared apply — **pattern to mirror** |
| Display/equip | ItemHandlers / ClientItemHandlers | Already V2-first |
| Convert source | `LootGenerator.toEntityItems` | Stamps `properties["templateId"]`, `instanceId`, quality/quantity/charges on floor `Entity.Item` |
| Core API | `PlayerState.addItemInstance` / `InventoryComponent.addItem` | Correct V2 entry points |
| Drop | still V1 entity inventory | **Out of scope** residual |

### Pattern to mirror
`reasoning/.../treasureroom/TreasureRoomStateApply.kt`: pure `applySuccess(world, spaceId, player, Success) → WorldState` writing `inventoryComponent`. Contract tests in `TreasureRoomInventoryContractTest`.

---

## 3. Design / recommended approach

**Pure helper + thin handlers** (KISS; same as MUD-007).

1. **`FloorItemTakeApply`** (name flexible) under `reasoning/.../inventory/` (or `items/`):
   - Input: `world`, `player`, `spaceId`, floor `Entity.Item`, templates map (and/or resolve via `ItemRepository` only in tests with fake repo).
   - Resolve `ItemInstance`:
     - Prefer `properties["templateId"]` + reuse `instanceId` / quality / quantity / charges from properties (LootGenerator reverse).
     - Else name-match against provided templates (legacy procedural drops).
     - If no template → `Failure` with clear message (do **not** V1-only write).
   - Weight: `inventoryComponent.canAdd` / `PlayerState.addItemInstance` (null = overweight Failure).
   - On Success: `removeEntityFromSpace` → `updatePlayer` with `inventoryComponent` (+ optional keep/clear V1 list untouched — do not dual-write V1 unless needed for tests).
   - Also `removeDroppedItem(spaceId, instanceId)` when instance id known (console already does; unify in helper).
   - Return sealed `Success(world, itemName, templateId)` / `Failure(msg)`.

2. **Thin handlers** (`handleTake` / `handleTakeAll`):
   - Keep find/pickupable/scenery checks in handlers.
   - Call pure apply; assign `worldState`; emit narrative / System.
   - GUI: emit Narrative (+ StatusUpdate belt if desired; ViewModel already refreshes on any event post-MUD-007).
   - `handleTakeAll`: loop apply; stop or skip overweight per item with message.

3. **Tests** (mock LLM; prefer `:reasoning` unit):
   - Floor `Entity.Item` with `templateId`/`instanceId` props → apply → `world.player.inventoryComponent.items` has that templateId; entity gone from space; itemsDropped cleared if present.
   - Overweight → Failure, world unchanged.
   - No live LLM / no full Engine boot.

4. **Docs:** KNOWN_ISSUES residual floor line → fixed; note drop still V1 / legacy no-template residual if left.

---

## 4. Files to create/touch

| Action | File |
|--------|------|
| **Create** | `reasoning/src/main/kotlin/.../inventory/FloorItemTakeApply.kt` (pure apply) |
| **Create** | `reasoning/src/test/kotlin/.../inventory/FloorItemTakeContractTest.kt` (or under items/) |
| Edit | `app/.../ItemHandlers.kt` — `handleTake` / `handleTakeAll` call apply |
| Edit | `client/.../ClientItemHandlers.kt` — same |
| Edit | `app/.../GameServer.kt` — same floor take paths (parity; small) |
| Edit | `KNOWN_ISSUES.md` — residual floor note |
| Regen | `tools/test-lock/manifest.sha256` if tests added (`MUD_ALLOW_TEST_CHANGES=1`) |
| Bookkeep | ticket + BOARD on impl closeout (not this turn) |

No new modules. No treasure / drop rewrite. No V1 field deletion.

---

## 5. Non-goals

- MUD-007 treasure Jason playtest closeout
- Full V1 field deletion / mass `V2_REMOVAL_PLAN`
- Multi-user treasure retest
- Drop / give / equip path rewrites (except verify equip still V2-reads taken item)
- SkillManager / quarantine debt (MUD-020/021)
- git commit/push this plan turn; product `*.kt` this turn

---

## 6. How impl confirms acceptance

**Automated**
- [ ] Unit: take Success → `inventoryComponent.items.any { it.templateId == expected }`
- [ ] Unit: entity removed from space; optional itemsDropped cleared
- [ ] Unit: overweight Failure leaves inventory empty / world unchanged
- [ ] Grep post-impl: `handleTake` bodies no longer call `addToInventory` for Success path  
  `rg 'addToInventory' app/src/.../ItemHandlers.kt client/.../ClientItemHandlers.kt app/.../GameServer.kt`
- [ ] `./tools/verify_mud.sh --core` exit 0; test-lock green after regen if tests
- [ ] KNOWN_ISSUES floor residual updated

**Manual smoke (optional; not Jason-gated unless requested)**
- [ ] Console: drop-or-spawn floor item → `take <item>` → `inventory` lists → `equip` if equippable
- [ ] GUI: same

---

## 7. Ordered impl steps

1. Add failing contract test(s) for floor take → V2 inventory (+ world apply).
2. Implement `FloorItemTakeApply` (Entity.Item → ItemInstance → remove entity/dropped → updatePlayer).
3. Wire console `ItemHandlers` take/takeAll; strip redundant dual logic into helper.
4. Wire GUI `ClientItemHandlers` identically; StatusUpdate only if needed.
5. Wire `GameServer` floor take/takeAll to same helper.
6. Run `./tools/verify_mud.sh --core`; regen test-lock if tests added.
7. Update `KNOWN_ISSUES.md` residual; closeout note + dod-summary path.
8. Ticket → done (or human_gated only if playtest requested — default **agent closeout OK** for this bugfix).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Legacy `Entity.Item` without `templateId` (ItemGenerator) | Name-match templates; else Failure + residual note — no silent V1-only |
| Dual V1/V2 still confuses drop | Out of scope; document residual |
| Weight capacity false Failure | Use same `canAdd`/`addItemInstance` as corpse/treasure |
| GameServer skipped → multi-user still broken | Include GameServer in wire step |
| Test-lock fail-closed | Ticket-scoped regen with `MUD_ALLOW_TEST_CHANGES=1` |
| Over-extract | Keep helper ~30–60 lines; no new framework |
| Treasure take regression | Treasure still delegates first; do not change TreasureRoom path |

---

## Impl handoff

- Fresh session only after **Astra/Jason approve** this plan.
- Brief: `issues/_templates/implement-brief.md` → `tmp/workers/MUD-019/IMPL_BRIEF.md`.
- Serial **one live builder per tree**. No commit/push unless ticket post-done policy later.

---
Status: APPROVED by Astra 2026-08-11 12:21 MST
Common-sense: pure FloorItemTakeApply + thin console/GUI/GameServer handlers; mirror MUD-007 treasure pattern; contract tests; no V1 mass delete; no SkillManager.
Impl = fresh session (do not resume plan session 019ff234-15f7-7461-9b1f-af515919382c).
