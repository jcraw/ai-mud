# Known Issues

## Treasure Room Inventory Bug (UNRESOLVED)

**Status**: In progress, needs further investigation

**Symptom**:
- Treasure room items successfully taken (success message shown)
- Items do NOT appear in inventory
- `wield`/`equip` commands fail with "You don't have that in your inventory"
- `i`/`inventory` shows empty inventory

**Affected Clients**:
- ✅ Console app - FIXED (not tested)
- ❌ GUI client - Still broken after attempted fix

**Changes Made**:

1. **IntentRecognizer.kt** (perception module):
   - Line 180: Added "i", "inv", "eq" to inventory intent triggers
   - Line 188: Added "wield", "wear" variations to equip intent
   - Line 244-246: Added parsing rules #24-26 for equip/inventory/pedestal variations

2. **App.kt** (console app):
   - Lines 122-127: Initialize player with empty `InventoryComponent` (V2 system)
   - Line 4: Added import for `InventoryComponent`

3. **ItemHandlers.kt** (console app):
   - Lines 335-412: Rewrote `handleEquip` to check V2 `inventoryComponent` first, then fallback to legacy
   - Lines 67-71: Added debug logging to `handleInventory`

4. **TreasureRoomHandlers.kt** (console app):
   - Lines 56-71: Added debug logging to `handleTakeTreasure`

5. **ClientItemHandlers.kt** (GUI client):
   - Lines 199-281: Rewrote `handleEquip` to check V2 `inventoryComponent` first, then fallback to legacy

**Root Cause**: Unknown
- Player IS initialized with `inventoryComponent` (verified in EngineGameClient.kt:247)
- Treasure room handler should add items to V2 inventory
- Something is preventing the inventory update from persisting

**Next Steps**:
1. Add comprehensive debug logging to trace inventory state through entire flow
2. Check if treasure room state update is working correctly
3. Verify `game.worldState` updates are persisting between commands
4. Check GUI client event handling - may be an issue with state propagation
