# Known Issues

## Treasure Room Inventory Bug (PENDING JASON PLAYTEST)

**Status**: Impl landed (MUD-007) — **pending Jason playtest** before player-done. Do not claim fixed until playtested.

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

**Residual risk (out of scope MUD-007)**:
- Floor-item take still uses V1 `addToInventory` while inventory/equip read V2 first — empty-inventory bug for floor loot possible
- Multi-user treasure not retested

**Ticket**: `issues/MUD-007-treasure-inventory-gui.md` · plan `plans/2026-08-10-ai-mud-MUD-007-treasure-inventory-gui.md`
