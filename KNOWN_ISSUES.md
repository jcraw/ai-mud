# Known Issues

## Treasure Room Inventory Bug (HARNESS DONE)

**Status**: MUD-007 **done for harness** (contract tests + verify). Jason 2026-08-11: no playtest gate until product play phase. Optional future human smoke only.

**Symptom (historical)**:
- Treasure room items successfully taken (success message shown)
- Items did NOT appear in inventory / HUD
- `wield`/`equip` failed with "You don't have that in your inventory"
- `i`/`inventory` looked empty

**Fix (MUD-007, automated green; playtest open)**:
- Pure contract: take Success → `InventoryComponent` gains `ItemInstance` with pedestal `templateId`
- Shared `TreasureRoomStateApply.applySuccess` used by console + GUI
- GUI `buildItemTemplatesMap` resolves via repository / `getItemTemplate` (not cache-only)
- After take/return Success: emit `StatusUpdate` so ViewModel refreshes `playerState`
- ViewModel also refreshes `playerState` on any event (belt)
- Inventory/equip: template-null shows `templateId` fallback / distinct error (no silent skip)

**Playtest still required (Jason)**:
- [ ] GUI: examine pedestals → take treasure → inventory lists item → equip works
- [ ] GUI: return treasure clears carrying and unlocks pedestals
- [ ] Console: same take/inventory/equip smoke
- [ ] Leave room with item still in inventory

**Residual risk**:
- ~~Floor-item take V1/V2 mismatch~~ — **fixed MUD-019**: pure `FloorItemTakeApply` writes V2 `InventoryComponent` (console + GUI + GameServer). Legacy floor entities with **no templateId and no name-match** fail closed (no V1-only write).
- ~~Floor-item drop V1/V2 mismatch~~ — **fixed MUD-023**: pure `FloorItemDropApply` removes from V2 `InventoryComponent` + dual-writes floor entity/`itemsDropped` (console + GUI + GameServer).
- ~~V1 inventory/equip **Success writes** (give / equip legacy / use / GUI buy)~~ — **fixed MUD-024**: `GiveItemApply` / `UseConsumableApply` + V2 equip on all surfaces; GUI buy uses `addItemInstance`. Residual: deprecated **fields** still on `PlayerState` (display/read fallbacks OK); field hard-delete optional follow-up.
- Multi-user treasure not retested
- **Modernization program (Wave A–G) closed** — gates on, quarantine **0**; residual human-only only → [`docs/MODERNIZATION_STATUS.md`](docs/MODERNIZATION_STATUS.md)

**Ticket**: `issues/MUD-007-treasure-inventory-gui.md` · plan `plans/2026-08-10-ai-mud-MUD-007-treasure-inventory-gui.md`
