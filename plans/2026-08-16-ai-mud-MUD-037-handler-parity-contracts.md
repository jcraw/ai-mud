# MUD-037 Plan — Handler parity contract pack (inv/combat/social)

**Ticket:** MUD-037 · **Phase:** plan_review (await Astra approve)  
**Plan:** `plans/2026-08-16-ai-mud-MUD-037-handler-parity-contracts.md`  
**Mirror:** `tmp/workers/MUD-037/PLAN.md`  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · depends MUD-031 (done)  
Not impl this turn.

---

## 1. Goal / acceptance

| # | Acceptance | Impl |
|---|------------|------|
| 1 | Shared apply **or** contracts for take/drop/equip/use + 1 combat + 1 social, across surfaces | Keep take/drop applies; add `EquipItemApply` + `UseConsumableContractTest`; add `CombatHitApply` + `EmoteApply`; thin-wire live surfaces |
| 2 | Assertion-strong (state deltas), not “msg contains ok” | Inv/equip/HP/qty/disposition/combat HP; Failure leaves world/player unchanged |
| 3 | Test-lock if new tests (ticket-authorized) | `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` |
| 4 | `--core` exit 0 | Hard gate |
| 5 | No GUI redesign | IO only: Narrative/System; no Compose/layout |

---

## 2. Current inventory

**Already shared + contracts (keep; do not rewrite)**

| Intent | Apply | Tests | Surfaces |
|--------|-------|-------|----------|
| take | `FloorItemTakeApply` | `FloorItemTakeContractTest` (templateId, space clear, overweight unchanged) | console + GUI + GameServer |
| drop | `FloorItemDropApply` | `FloorItemDropContractTest` (V2 remove, floor props, equip-clear) | same ×3 |
| give (not AC) | `GiveItemApply` | `GiveItemContractTest` | same ×3 — leave |

**Gaps this ticket fills**

| Intent | Today | Gap |
|--------|-------|-----|
| **use** | `UseConsumableApply` wired ×3 | **No contract test** |
| **equip** | V2 `inv.equip` **copied** in `ItemEquipHandlers` / `ClientItemEquipHandlers` / `GameServerItemHandlers.handleEquip` (find + slot + copy player) | **No `EquipItemApply`**, no contract; resolve rules already drift (GameServer extra exact-name) |
| **combat Attack** | Console+GUI call `AttackResolver.resolveAttack`; hit writes `replaceEntityInSpace` in both `CombatAttackHit` + `ClientCombatAttackHit` | **No contract**; GameServer `handleAttack` = stub *“not yet supported in multi-user”* |
| **social Emote** | Console+GUI call `EmoteHandler.processEmote` | Console writes **world**; GUI **repo-save only** (no `worldState`). GameServer `Intent.Emote` = stub |

**Do not pick** GUI-stub persuade/intimidate or LLM talk as the social intent.

**Pattern:** MUD-019/023/024 sealed `Success`/`Failure` + `:reasoning` unit, no live LLM.

---

## 3. Design

**KISS: extract missing pures; contract the apply; keep GameServer stubs.**

1. **`EquipItemApply`** (`reasoning/.../inventory/`): `(player, target, templates) → Success(player, itemName, slot, instanceId) / Failure`. Resolve like use: instance id → name contains → templateId. Fail: missing / no template / `equipSlot==null` / `equip()` null. Success: `updateInventory(inv.equip)`. **No V1.** Wire ×3; strip local find/apply.

2. **`UseConsumableContractTest`** only (apply exists): Success → item gone or qty−1 **and** `health` += min(heal, missing HP); missing / non-consumable → Failure, player unchanged (same object or equal health+items).

3. **Combat = `CombatHitApply`** (not GameServer combat, not RNG grind): `(world, spaceId, npc, AttackResult.Hit) → WorldState` via `npc.withComponent(updatedDefenderCombat)` + `replaceEntityInSpace`. Console+GUI hit branches call it; narration/skill-progress/death stay in handlers. Contracts: (a) Hit `currentHp=N` → space NPC combat HP **== N**; (b) `AttackResolver` missing defender → `Failure`; world unchanged.

4. **Social = `EmoteApply`**: `(world, spaceId, npc, keyword, EmoteHandler) → Success(world, narrative, delta, npcId) / Failure`. Parse keyword; `processEmote`; `replaceEntityInSpace`. Contracts: `bow` → NPC `SocialComponent` disposition **== old + EmoteType.BOW.dispositionDelta (5)**; unknown keyword → Failure, world unchanged. Wire console + GUI; GUI **must** assign `worldState` from apply (repo-save may remain after).

5. **GameServer Attack/Emote stay stubs.** Ticket non-goal = full multiplayer. Proof = shared apply + unit contracts + live surfaces already on the helper. Residual note in closeout / `KNOWN_ISSUES` one line.

6. Tests: `:reasoning` only; mock/no LLM; JUnit like existing `*ContractTest`. No `OpenAIClient(`. New prod files under global token E (2500/250 fn); **no Added override**.

---

## 4. Files

| Action | Path |
|--------|------|
| Create | `reasoning/src/main/.../inventory/EquipItemApply.kt` |
| Create | `reasoning/src/main/.../combat/CombatHitApply.kt` |
| Create | `reasoning/src/main/.../EmoteApply.kt` (beside `EmoteHandler`) |
| Create | `reasoning/src/test/.../inventory/EquipItemContractTest.kt` |
| Create | `reasoning/src/test/.../inventory/UseConsumableContractTest.kt` |
| Create | `reasoning/src/test/.../combat/CombatHitContractTest.kt` |
| Create | `reasoning/src/test/.../EmoteApplyContractTest.kt` |
| Edit | `app/.../ItemEquipHandlers.kt` · `client/.../ClientItemEquipHandlers.kt` · `app/.../GameServerItemHandlers.kt` |
| Edit | `app/.../CombatAttackHit.kt` · `client/.../ClientCombatAttackHit.kt` (damage write only) |
| Edit | `app/.../SocialDialogueHandlers.kt` · `client/.../ClientSocialDialogueHandlers.kt` (`applyEmote`) |
| Edit | `KNOWN_ISSUES.md` — one residual: MU attack/emote still stub |
| Regen | `tools/test-lock/manifest.sha256` |

No new modules. No Compose. No 036/038.

---

## 5. Non-goals

- GameServer combat / emote / net layer
- GUI persuade/intimidate/check stubs; GUI redesign
- Headless playthrough (038); dup-gate (036); PIT (035)
- Give/trade/loot/treasure rewrite; V1 field delete
- Weakening take/drop tests; live LLM; override raise

---

## 6. Confirm acceptance

- [ ] take/drop existing contracts still green (no rewrite)
- [ ] equip Success → `equipped[slot].id == instanceId`; missing/non-equip → Failure, `equipped` unchanged
- [ ] use Success → items/qty + `health` deltas; Failure unchanged
- [ ] combat Hit apply → defender combat `currentHp` exact; missing target → Failure, world `===` or structurally equal
- [ ] emote `bow` → disposition +5 on space NPC; unknown → Failure, unchanged
- [ ] grep: no `inventoryComponent.equip(` Success in the 3 handler files (only apply)
- [ ] GUI emote assigns `game.worldState = result.world`
- [ ] GameServer Attack/Emote still stub strings (no MU feature)
- [ ] no live LLM in new tests; `./tools/verify_mud.sh --core` 0; test-lock written; cite `tmp/dod-summary.json`

---

## 7. Impl steps

1. Failing `EquipItemContractTest` + `UseConsumableContractTest`.
2. `EquipItemApply`; wire ×3; delete local equip apply.
3. Failing `CombatHitContractTest`; `CombatHitApply`; wire both hit writers.
4. Failing `EmoteApplyContractTest`; `EmoteApply`; wire console+GUI (world write).
5. `KNOWN_ISSUES` stub residual.
6. `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`.
7. `./tools/verify_mud.sh --core` (N≤3 flake only). Closeout: paths, greps, dod-summary, residual stubs.
8. Ticket → done (agent OK; no Jason playtest).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Combat RNG flakes | Contract world-apply + Failure path; don’t assert exact damage off `Random.Default` |
| GUI emote still repo-only | Apply returns `WorldState`; handler must assign it |
| Equip resolve drift | One resolve order (same as use) |
| Scope into MU combat | Explicit stub; 038 later |
| Token E on new apply | Keep each apply ~40–80 lines |
| Test-lock fail-closed | Ticket-scoped regen |
| Konsist | Apply stays in `reasoning`; tests in `:reasoning` |

---

## Impl handoff

Fresh session after **APPROVED by Astra**. Brief: `issues/_templates/implement-brief.md` → `tmp/workers/MUD-037/IMPL_BRIEF.md`. Serial one builder. No commit/push this plan turn.

## Learn

bite: none
