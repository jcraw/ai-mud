# MUD-007 Plan — Fix treasure-room inventory in GUI (KNOWN_ISSUES)

**APPROVED by Astra** · 2026-08-10 ~23:15 AZ · common-sense OK (RC-A ViewModel StatusUpdate gap + RC-B client cache-only templates; pure contract test first; dual-handler parity; keep needs_jason playtest; no V1 mass rewrite / no floor-take scope creep).

**Ticket:** MUD-007 · **Worker:** grok · **Phase:** implementing  
**Impl = fresh session** after Astra approve. Do not resume plan session.  
**Plan path:** `plans/2026-08-10-ai-mud-MUD-007-treasure-inventory-gui.md`  
**Worker mirror:** `tmp/workers/MUD-007/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh`  
**Gate:** `needs_jason: playtest` — do **not** claim player-done

Status: **APPROVED by Astra** (2026-08-10 ~23:15 AZ) — fresh IMPL authorized

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Take treasure updates GUI inventory | After take, `inventory`/`i` lists item from V2 `inventoryComponent`; equip finds it |
| 2 | Console parity | Same take→inventory contract on console path (no regression) |
| 3 | Tests, no live LLM | Unit/integration on Success → `inventoryComponent` + world apply; mock `ItemRepository` |
| 4 | Verify green | `./tools/verify_mud.sh` exit 0 (+ test-lock regen if tests touch) |
| 5 | Residual + playtest | Closeout risk notes; Jason GUI + console playtest before player-done |

---

## 2. Current inventory (evidence)

### Files / split
| Layer | Path | Role |
|-------|------|------|
| Core pure | `reasoning/.../TreasureRoomHandler.kt` | `takeItemFromPedestal` → `Success(playerInventory=addItem(...))` |
| Console | `app/.../TreasureRoomHandlers.kt` | Success → `updatePlayer(copy(inventoryComponent=…))` + `updateTreasureRoom` + assign `worldState` |
| GUI | `client/.../ClientTreasureRoomHandlers.kt` | Same write shape as console |
| Display/equip GUI | `client/.../ClientItemHandlers.kt` | `handleInventory` / `handleEquip` prefer V2 `inventoryComponent` (non-null default on `PlayerState`) |
| Display/equip console | `app/.../ItemHandlers.kt` | Same V2-first pattern + debug prints |
| Engine GUI | `client/EngineGameClient.kt` | Player init with empty `InventoryComponent`; `getCurrentState()=worldState.player`; routes `Take`/`TakeTreasure` |
| UI bind | `client/GameViewModel.kt` | `playerState` refresh **only** on `GameEvent.StatusUpdate` |
| Model | `PlayerState`: V1 `inventory` deprecated; V2 `inventoryComponent` non-null default |

### Suspected root causes (ranked, with evidence)

**RC-A — ViewModel / event refresh gap (GUI-specific, high confidence for HUD staleness)**  
- Treasure take emits **Narrative only** (`ClientTreasureRoomHandlers` L55–60).  
- `GameViewModel.handleGameEvent` refreshes `playerState` **only** for `StatusUpdate` (L177–180).  
- Movement/look emit `StatusUpdate`; take does not → any UI bound to `UiState.playerState` stays pre-take.  
- Alone does **not** explain equip failure (equip reads `game.worldState`, not UiState).

**RC-B — Client template map uses cache only (parity delta vs console)**  
- Client `buildItemTemplatesMap`: `itemTemplateCache[id]` only (`ClientTreasureRoomHandlers` L200–205).  
- Console: `itemRepository.findTemplateById` (`TreasureRoomHandlers` L242–248).  
- Cache miss → name match fails / empty available list; if take still succeeds via other route, weight/`canAdd` templates may be incomplete.  
- Inventory display resolves templates via **repository** (ClientItemHandlers L27–29); equip also repository. If instance present but template missing → equip: “You don't have that…” (false negative L312–314); inventory loops skip null templates (silent empty-looking list).

**RC-C — Dual V1/V2 write vs read (adjacent debt; treasure path already V2)**  
- Treasure take writes **V2 only** (correct).  
- Regular floor `handleTake` still uses V1 `addToInventory` (ClientItemHandlers L183) while inventory/equip read V2 first → classic empty-inventory bug for **floor** items.  
- Out of scope unless take is mis-routed to floor path (routing exists both in `EngineGameClient` Take branch and `ClientItemHandlers.handleTake`).

**RC-D — Console “FIXED” is unverified**  
- KNOWN_ISSUES: “Console FIXED (not tested)”.  
- Console take apply is isomorphic to client; “fix” was init + equip V2 check + debug logs, not a divergent pure core.  
- Treat console as **must re-verify**, not ground truth.

**RC-E — State overwrite (lower likelihood from static read)**  
- Post-intent: `advanceTime` (time only), `processNPCTurns` (damage via `copy` health), `syncPlayerMaxHp` (copy max HP). All should preserve `inventoryComponent`.  
- `updatePlayer` keys by player id; `player` = `players.values.first()` — single `player_ui` in GUI init. No evidence of multi-player clobber in single-player GUI.

**Core path appears correct on paper:**  
`TreasureRoomHandler` Success always `addItem`s; both handlers assign `game.worldState = updatePlayer(…).updateTreasureRoom(…)`. Missing: **contract test** proving this end-to-end for inventoryComponent items.

---

## 3. Design / recommended approach

**Strategy: prove contract first → minimal fix at true break → parity → UI refresh.**

1. **Pure contract (prefer shared helper if both handlers stay duplicated)**  
   - Add thin pure apply (optional but preferred): e.g. `TreasureRoomStateApply.applySuccess(world, spaceId, player, Success) → WorldState` in `reasoning/.../treasureroom/` (or private shared if too small).  
   - Both console + client call it (one write path).  
   - If helper is overkill for KISS: keep dual assign lines but **one shared unit test** on handler Success + `WorldState.updatePlayer` semantics.

2. **Client parity fixes (likely product delta)**  
   - `buildItemTemplatesMap`: resolve via `itemRepository` / `getItemTemplate` (same as console), not cache-only.  
   - After take/return Success: emit `StatusUpdate` (hp/maxHp/location from current player) so ViewModel refreshes `playerState`.  
   - Optional belt: ViewModel refreshes `playerState` from `getCurrentState()` on **any** event (or Narrative+System too) — small, robust; avoid double-fetch storms (single client, fine).

3. **Display/equip hardness (minimal)**  
   - Inventory: if instances exist but template missing, show templateId fallback (not silent skip) — optional debug-friendly.  
   - Equip: if instance matches by templateId substring but template null, distinct error (“template missing”) vs “not in inventory”.  
   - **Do not** reintroduce V1 write on treasure path; **do not** full V1 removal.

4. **Tests (ticket-scoped; no live LLM)**  
   - `TreasureRoomHandler` take Success: `playerInventory.items` contains new instance with pedestal `templateId`.  
   - Apply/`updatePlayer`: world.player.inventoryComponent.items size +1; treasure room locked.  
   - Prefer `:reasoning` or `:core` unit tests with fake `ItemRepository` (in-memory map).  
   - Avoid full EngineGameClient boot if possible (heavy DB); if needed, one thin client test only for StatusUpdate refresh.  
   - **Test-lock:** any `src/test/**` change → `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` and commit updated `tools/test-lock/manifest.sha256` (MUD-012). Note in closeout.

5. **Docs bookkeeping (tiny)**  
   - KNOWN_ISSUES treasure section → resolved or “pending Jason playtest” after impl (impl session). Not this plan turn.

---

## 4. Files to create/touch

| Action | File |
|--------|------|
| Edit | `client/.../ClientTreasureRoomHandlers.kt` — template resolve; StatusUpdate; shared apply if any |
| Edit | `app/.../TreasureRoomHandlers.kt` — call shared apply; strip or keep DEBUG prints (prefer remove after green) |
| Edit | `client/GameViewModel.kt` — broader `playerState` refresh if needed |
| Edit (opt) | `client/.../ClientItemHandlers.kt` — template-fallback display / equip error clarity |
| Create | `reasoning/src/test/.../TreasureRoomHandlerTest.kt` (or `.../TreasureRoomInventoryContractTest.kt`) |
| Create (opt) | `reasoning/.../TreasureRoomStateApply.kt` pure apply |
| Regen | `tools/test-lock/manifest.sha256` if tests added |
| Later impl | `KNOWN_ISSUES.md` status line; ticket closeout — not plan turn |

No new Gradle modules. No GUI Compose layout polish.

---

## 5. Non-goals

- MUD-009/013/014/015/016/017/018  
- Full V1 inventory deletion / mass equip rewrite / multi-user treasure  
- Floor-item V1 take migration (note residual risk only)  
- Live LLM / testbot playthrough as gate  
- Claiming playtest-done; removing `needs_jason: playtest`  
- Git commit/push from plan session  

---

## 6. How impl confirms acceptance

**Automated**
- [ ] Unit: take Success adds ItemInstance with expected `templateId` to `InventoryComponent`  
- [ ] Unit: apply/updatePlayer → `world.player.inventoryComponent.items` non-empty; equip-name match finds instance  
- [ ] `./tools/verify_mud.sh` exit 0  
- [ ] If tests changed: test-lock check green after regen  

**Jason playtest (required before player-done)**
- [ ] GUI: enter treasure room → `examine pedestals` → `take treasure <item>` → success narrative → `inventory` lists item → `equip <item>` works  
- [ ] GUI: `return treasure` clears carrying and unlocks pedestals  
- [ ] Console: same take/inventory/equip smoke (parity)  
- [ ] Leave room with item still in inventory (exit finalize does not strip item)

**Closeout must state:** residual dual V1 floor-take debt; any untested multi-user; playtest still open.

---

## 7. Ordered impl steps

1. Reproduce static contract: write failing unit test(s) for take→inventoryComponent (+ world apply).  
2. Confirm green on pure `TreasureRoomHandler` alone; if fails, fix core first.  
3. If handler green: add shared apply (optional) and wire **both** handlers; align client template map to repository/`getItemTemplate`.  
4. Emit `StatusUpdate` after take/return Success; optionally widen ViewModel refresh.  
5. Harden inventory/equip template-null messaging only if tests/play need it.  
6. Run `./tools/verify_mud.sh`; regen test-lock if needed.  
7. Closeout: paths, verify result, residual risk; leave ticket for Jason playtest (do not mark player-done).  
8. Update KNOWN_ISSUES status line after code lands.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Dual inventory still confuses floor take | Out of scope; document residual |
| False console-fixed | Explicit console parity test + Jason console smoke |
| State races on concurrent `sendInput` | Single-player sequential; no Mutex today — note only if flaky |
| Test-lock fail-closed | Ticket-scoped regen with `MUD_ALLOW_TEST_CHANGES=1` |
| Over-extract shared layer | Prefer 10-line pure apply or dual call + one test; KISS |
| Silent template miss looks like empty inv | Fallback display + equip error split |
| Playtest gate skipped | Keep `needs_jason: playtest`; never claim player-done in impl |

---

## Impl handoff

- Fresh session only after **Astra/Jason approve** this plan.  
- Brief: fill `tmp/workers/MUD-007/IMPL_BRIEF.md` from `issues/_templates/implement-brief.md`.  
- Serial one live builder per tree.
