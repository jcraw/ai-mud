# MUD-039 Plan — Zero handler twin duplication + hard gate

**Ticket:** MUD-039 (missing — impl files `issues/MUD-039-zero-handler-twin-duplication.md`)  
**Phase:** plan_review · **Plan:** `plans/2026-08-19-ai-mud-MUD-039-zero-handler-twin-duplication.md`  
**Verify:** `./tools/verify_mud.sh --core` · depends MUD-036 + MUD-037 (done)  
Not impl. No commit/push/deploy.

---

## 1. Goal / acceptance

One impl: delete 19 live app↔client clones, then fail closed on new ones.

| # | Acceptance | Delivers |
|---|------------|----------|
| 1 | Zero twins | Checker `pairs=0`. **No allowlist** of the 19 |
| 2 | Hard gate | Emit `DUP_BLOCK_E` (retire live W). Fail default/fast/core/full if E>0 |
| 3 | Soft opt-out | `MUD_DUP_SOFT=1` / `--dup-soft`. No R1 / no `MUD_DUP_HARD` |
| 4 | `--core` green | After extracts + flip. Missing checker **fail-closed** (hard) |
| 5 | Docs | `DUPLICATION_KT` R2; DESIGN C3; AGENTS; DOD_SUMMARY E live |

**Astra:** DESIGN C3 is Tier C (`--full`). 036: “core stays warn until Jason/Astra.” This plan **asks** `--core` hard (agent drain). Alt if rejected: `--full` hard only.

---

## 2. Current inventory

Measured: `files=104` **`pairs=19`**. Allowlist `[]`.

| n | app twin (client = `Client*` unless noted) |
|---|--------------------------------------------|
| 67 | `TreasurePedestalSupport` |
| 56 | `ItemInventoryFormat` |
| 39 | `SkillQuestSkillInfer` |
| 28 | `ItemFloorTemplates` |
| 27 | `TreasureExamineHandlers` |
| 23 | `CombatAttackPrep` |
| 17 | `CombatSkillProgressHandlers` · `ItemTakeHandlers` · `SkillQuestInteractHandlers` |
| 17 | `SkillQuestCheckHandlers` ↔ **`ClientSkillQuestInteractHandlers`** (cross-stem) |
| 16 | `TreasureReturnHandlers` · `TreasureTakeHandlers` |
| 14 | `CombatAttackHit` |
| 13 | `MovementFleeHandlers` · `SkillQuestInteractHarvest` |
| 12 | `ItemDropGiveHandlers` |
| 11 | `CombatAttackHandlers` · `SkillQuestCraftResults` |
| 10 | `CombatAttackMiss` |

**Keep:** 037 applies + `TreasureRoomHandler` / `TreasureRoomStateApply` / `FleeResolver`.  
**Leave:** GameServer (not under `handlers/`); intra-app-only; AST/rename clones.  
**Gate now:** checker exit 0; verify always pass + `DUP_BLOCK_W`; missing checker → skip.

---

## 3. Design

**Extract pures → thin IO → remeasure → flip hard.** Do not merge handler files. Do not rename to dodge hashes.

**Homes (Konsist-legal):**
- `:action` — `ItemInventoryFormat` twins → `ItemInfoFormatter` (beside `SkillFormatter`).
- `:reasoning` — skill infer, floor templates (`ItemRepository` is core), pedestal name/barrier/stats, feature match, combat weapon/health-band/successFlags.
- **IO stays in handlers.** `println` vs `emitEvent` must not share a 10-line window. Client `emitStatusUpdate` + extra `getItemTemplate` fallback stay on the client wrapper.

**Clusters** (remeasure after each; stop at `pairs=0`):
1. **A** `ItemInfoFormatter` · `SkillActionInfer` · `FloorItemTemplates`
2. **B** pedestal pures; then examine/take/return text if still ≥10
3. **C** weaponName / template-ids / NPC match / successFlags / healthBand. Hit `resolveWeapon`: **include `HANDS_BOTH`** (prep already does; hit drifted)
4. **D** leftovers only: take/drop find; Check↔Interact feature match; harvest/craft/flee/miss

**Hard flip (after `pairs=0` only):** checker stays exit 0 / `report_only`.
- Emit `DUP_BLOCK_E` (`limit` 10). Verify fail if E>0 (`gates.duplication_kt=fail`, note `hard E=n pairs=m`).
- Soft: pass + merge findings.
- Skip: quarantine / pitest / smoke / preflight.
- Missing python/checker: **fail-closed** when hard; skip when soft.
- Allowlist stays **empty**. Schema required tuple unchanged.

**Tests/token:** no new `src/test` unless a success-path state delta changes (then one `:reasoning` contract + lock regen). New files under global E; **no Added overrides**. No Compose redesign.

---

## 4. Files

| Action | Path |
|--------|------|
| Create | `action/.../ItemInfoFormatter.kt` · `reasoning/.../SkillActionInfer.kt` · `inventory/FloorItemTemplates.kt` · `treasureroom/TreasurePedestalSupport.kt` |
| Create | 2–5 more reasoning helpers **only if** remeasure still `pairs>0` |
| Edit | Matching app+client twins (thin wrappers) |
| Edit | `check_duplication_kt.py` emit E · `verify_mud.sh` hard/soft/`--dup-soft`/fail-closed/help |
| Edit | `DUPLICATION_KT.md` · DOD_SUMMARY · DESIGN C3 · `AGENTS.md` one-liner |
| Impl book | ticket + BOARD Q4 + `tmp/workers/MUD-039/CLOSEOUT.md` |

No GameServer, allowlist rows, CPD, schema required, override raise, push.

---

## 5. Non-goals

Merge twins into one file; GUI redesign; GameServer combat/emote; intra-app / non-`handlers/` clones; rename “fixes”; R1-only; hard **before** `pairs=0`; new 037-style contract pack; smoke/PIT/LLM; token/detekt/Konsist policy.

---

## 6. Confirm acceptance

- [ ] Standalone `pairs=0` `findings=[]` (no allowlist)
- [ ] `--core` 0; `duplication_kt` pass `hard E=0 pairs=0`; cite `tmp/dod-summary.json`
- [ ] Scratch 10-line twin → `--core` fail + `DUP_BLOCK_E`; `MUD_DUP_SOFT=1` pass; revert
- [ ] Hide checker → hard `--core` fail (not skip)
- [ ] quarantine/pitest/smoke skipped; `--dry-run --core` lists gate, no invoke
- [ ] Docs R2 + `--dup-soft`; no `src/test` (or lock if required); no Added override

---

## 7. Impl steps

1. File ticket/`plans/` if missing. **Do not flip yet.**
2. A → remeasure. B → remeasure. C (`HANDS_BOTH`) → remeasure. D only if needed → `pairs=0`.
3. Flip checker + verify; docs.
4. §6 smokes (clone fail + revert).
5. `--core` (N≤3). Closeout: paths, pairs, dod-summary, GameServer residual. Ticket done. No push.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Fat 19-pair impl | Clusters; stop at zero; no IO merge |
| Hard while pairs>0 | Zero first |
| Hash dodge | Forbidden |
| Token E on new files | Small extracts; no overrides |
| `--core` hard vs C3 | Astra stamp; alt = `--full` only |
| Client template fallback | Keep in wrapper |
| `HANDS_BOTH` narrate | Same as prep; closeout note |
| Missing checker skip | Fail-closed under hard |

---

## Impl handoff

Fresh session after **APPROVED by Astra** (or Jason). Brief → `tmp/workers/MUD-039/IMPL_BRIEF.md`. Serial one builder.

## Learn

bite: none
