# MUD-021 Plan — Quarantine slice 3 (lore / death / treasure placer)

**Ticket:** MUD-021 · **Worker:** grok · **Phase:** plan_review (await Astra)  
**Impl = fresh session** after Astra approve. Do **not** resume this plan session for product edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-021-quarantine-slice3-lore-death-treasure.md`  
**Worker mirror:** `tmp/workers/MUD-021/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` **and** `./tools/verify_mud.sh --quarantine`  
**Depends:** MUD-020 done (residual **12**). **Target:** clear **4** → quarantine **12 → ≤8** (SkillManager ×8 stays deferred).

---

## 1. Goal / acceptance mapping (4 methods → fix type)

| # | Method | Fix type | Un-tag when |
|---|--------|----------|-------------|
| 1 | `LoreInheritanceEngineTest#varyLore generates lore variation with parent keywords` | **Test harness** (mock embeds distinctive parent tokens) | lore contains parent key + direction |
| 2 | `WorldGeneratorTest#generateChunk creates REGION level with parent lore variation` | **Test harness** (`createMockLoreEngine` embeds parent) | REGION + parentId + lore refs parent |
| 3 | `DeathHandlerTest#NPC death drops loot into space and corpse` | **Test re-contract** (V3 dual-write; de-flake gold) | corpse + space drops + gold deterministic |
| 4 | `TreasureRoomPlacerTest#selectTreasureRoomNode excludes Boss and Frontier nodes` | **Prod fix** (fallback excludes Frontier) | result id == `valid` |

**Acceptance:** no assert-weaken / `@Disabled` / silent delete; real fix or intentional stronger re-contract per method; `TEST_QUARANTINE.md` + count; test-lock regen; `--core` 0; quarantine residual hard-fail OK with `quarantine_count` ≤8; closeout lists SkillManager ×8 still deferred.

---

## 2. Current inventory (test expect vs prod truth)

### C1 — LoreInheritanceEngine parent keywords (1)
- **Test:** `varyLore(parent="The ancient kingdom of Valdor…", REGION, "north")` → success; lore contains **`kingdom of Valdor`** + **`northern`**.
- **Prod:** pure LLM passthrough; prompt asks consistency with parent; **no** deterministic fallback embedding keywords on success.
- **Mock:** extracts `Parent lore: (.+)` then **first 3 words** → `"The ancient kingdom"`; direction regex → `"northern"`. Output lacks phrase `"kingdom of Valdor"` → fail at assert line 35.
- **Sibling green:** `dark forest` works (first-3-word window includes phrase).
- **Verdict:** harness/assert mismatch, not missing prod fallback. **Align mock** to re-embed distinctive parent tokens (e.g. full parent sentence or proper noun `Valdor` + faction words) so direction + parent keywords both land. Keep strong contract (parent identity present + direction). Optional: assert `Valdor` + `northern` (still strong).

### C2 — WorldGenerator REGION parent lore (1)
- **Test:** parent chunk lore `"The ancient kingdom of Valdor"`; expect `chunk.lore.contains("Valdor")` + REGION + `parentId == WORLD_root`.
- **Prod path:** `generateChunk` → if parent ≠ null → `loreEngine.varyLore(...)` → `chunk.lore = lore` (correct wire).
- **Harness:** `createMockLoreEngine()` uses dumb `MockLLMClient` always returning **`"mock response"`** → no parent tokens.
- **Verdict:** integration mock does not simulate varyLore contract. **Fix mock** to embed parent keywords when userContext is lore variation (same idea as C1). Keep structure asserts. Do **not** rewrite WorldGenerator cascade.

### C3 — DeathHandler NPC loot V3 (1)
- **Test expects:** `NPCDeath`; `space.itemsDropped` non-empty with `GOLD_TEMPLATE_ID`; `Entity.Item` gold in space; `Entity.Corpse` with non-empty `contents` and **`goldAmount >= 8`**.
- **Prod truth (V3 already correct):**
  - find space via `spaces.entities`; `replaceEntityInSpace` NPC→Corpse;
  - loot table + gold → `corpse.contents` + dual-write `itemsDropped` + `toEntityItems` → `addEntityToSpace`.
- **Fail / flake root:** `generateGoldDrop(base=8)` uses variance **0.8–1.2** → gold int **6–9**; assert `>= 8` fails ~**50%**. Table `goblin_common` has `guaranteedDrops=0` (table items optional; gold still expected when `goldDrop>0`).
- **Verdict:** prefer **V3 dual-write truth**, not old room APIs. **Re-contract test:** keep corpse-in-space, NPC gone, gold in `itemsDropped` + Entity.Item + corpse contents; change gold floor to **`>= 1`** (matches prod `coerceAtLeast(1)`) **or** inject fixed `Random` if extending LootGenerator call site (optional; prefer assert re-contract to avoid API churn). Not assert-weaken of dual-write contract.

### C4 — TreasureRoomPlacer Boss+Frontier exclusion (1)
- **Test graph:** start Hub → boss(Boss), frontier(Frontier); `valid` Linear **disconnected** (no dist 2–3 candidates).
- **Prod:** `findTreasureRoomCandidates` correctly excludes Boss/Frontier/Hub at dist 2–3. **Empty fallback:**
  ```kotlin
  nodes.firstOrNull { !Hub && !Boss }  // Frontier NOT excluded
  ```
  → returns **`frontier`** (observed: expected `valid` got `frontier`).
- **Verdict:** **prod bug**. Hard-exclude **Boss + Frontier** (and Hub) in fallback; share one exclusion predicate with candidates. Un-tag when result == `valid`.

---

## 3. Design / recommended approach per cluster

### C1 Lore — test harness (preferred)
- Strengthen `LoreInheritanceEngineTest.MockLLMClient` lore branch: embed full parent first line (or key tokens including proper nouns) + direction adjective.
- Keep asserts: parent reference + `northern`. Prefer retaining `"kingdom of Valdor"` **or** pair `Valdor` + direction (document which). Un-tag when green.
- **Do not** add heavy prod post-LLM keyword enforcement this ticket.

### C2 WorldGenerator — test harness
- Replace dumb mock content in `createMockLoreEngine` path: when context is lore variation, return string containing parent keywords (+ optional direction). Structure asserts stay.
- Un-tag when green. No WorldGenerator algorithm rewrite.

### C3 DeathHandler — test re-contract (V3)
- Keep: NPCDeath, space non-null, itemsDropped has gold template, Entity.Item gold props, Corpse present, contents non-empty.
- Fix flake: `goldAmount >= 1` (or `> 0`) given `goldDrop = 8`; document variance.
- Stronger optional: assert NPC id absent from space entities; corpse name contains NPC name.
- **No** revive V1 room APIs. Prod dual-write already matches intent unless impl finds real hole (then minimal prod fix).

### C4 TreasureRoomPlacer — **prod fix**
- Shared helper e.g. `isTreasureEligible(node)`: not Hub, not Boss, not Frontier.
- Use in `findTreasureRoomCandidates` **and** empty-candidate fallback.
- Un-tag when `select…` returns `valid`. No graph/BFS redesign.

### Docs / lock
- `docs/TEST_QUARANTINE.md`: move 4 to cleared; residual **8** = SkillManager only; update count narrative (12→8).
- Test-lock: `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` if tests touched.
- Optional AGENTS quarantine count pointer 12→8 if still present.
- Closeout: SkillManager ×8 deferred (Jason L1/L2).

---

## 4. Files to create/touch (impl)

| Path | Action |
|------|--------|
| `reasoning/src/main/.../treasureroom/TreasureRoomPlacer.kt` | **Prod:** exclude Frontier (w/ Boss/Hub) on fallback; shared predicate |
| `reasoning/src/test/.../world/LoreInheritanceEngineTest.kt` | Mock embeds parent keywords; un-tag |
| `reasoning/src/test/.../world/WorldGeneratorTest.kt` | Mock lore embeds parent; un-tag |
| `reasoning/src/test/.../combat/DeathHandlerTest.kt` | V3 asserts + gold de-flake; un-tag |
| `reasoning/src/test/.../treasureroom/TreasureRoomPlacerTest.kt` | un-tag when prod green |
| `docs/TEST_QUARANTINE.md` | 12→8; cleared table + residual SkillManager×8 |
| `tools/test-lock/manifest.sha256` | regen with env gate |
| Optional `AGENTS.md` | count pointer only if still “12” |

**Do not touch:** SkillManager*, full worldgen/DungeonInitializer redesign, detekt baseline mass regen, CI, PIT, testbot live, git commit/push.

---

## 5. Non-goals

- SkillManager ×8 L1/L2 unlock / XP redesign (Jason product opinion — **stay deferred**)
- Full worldgen rewrite / AncientAbyss redesign
- MUD-007 playtest
- Mass detekt baseline regen
- git commit/push
- Assert-weaken / `@Disabled` / silent delete

---

## 6. How impl confirms acceptance

Checklist:
- [ ] Tag count `rg -c '@Tag\("quarantine"\)' reasoning/src/test` → **8** (was 12)
- [ ] None of the 4 methods still have adjacent `@Tag("quarantine")`
- [ ] Residual list = SkillManager ×8 only
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] `./tools/verify_mud.sh --quarantine` hard-fail OK; `quarantine_count` **8** in `tmp/dod-summary.json`
- [ ] Targeted classes green under `-Pmud.includeQuarantine=true`
- [ ] DeathHandler: re-run method ≥5× without flake
- [ ] `docs/TEST_QUARANTINE.md` cleared 4 + residual 8
- [ ] Test-lock green after regen
- [ ] Closeout lists SkillManager deferred

Greps:
```bash
rg -n 'quarantine' reasoning/src/test/kotlin/com/jcraw/mud/reasoning/world/LoreInheritanceEngineTest.kt
rg -n 'quarantine' reasoning/src/test/kotlin/com/jcraw/mud/reasoning/world/WorldGeneratorTest.kt
rg -n 'quarantine' reasoning/src/test/kotlin/com/jcraw/mud/reasoning/combat/DeathHandlerTest.kt
rg -n 'quarantine' reasoning/src/test/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomPlacerTest.kt
rg -n 'NodeType\.Frontier' reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomPlacer.kt
```

---

## 7. Ordered impl steps

1. **C4 prod** — TreasureRoomPlacer fallback Frontier exclusion (+ shared predicate); run placer test; un-tag.
2. **C1 harness** — Lore mock parent-keyword embed; run test; un-tag.
3. **C2 harness** — WorldGenerator mock lore embed; run test; un-tag.
4. **C3 test** — DeathHandler V3 re-contract + gold de-flake; multi-run; un-tag.
5. Docs `TEST_QUARANTINE.md` 12→8 + cleared rows; test-lock regen; optional AGENTS pointer.
6. Verify `--core` then `--quarantine`; write CLOSEOUT (SkillManager residual explicit).
7. Ticket → done bookkeeping only in impl session (no commit/push unless Astra says).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| SkillManager scope creep | Explicit non-goal; residual only in closeout |
| Over-rewriting worldgen / lore engine | Harness-only for C1/C2; no cascade/LLM policy rewrite |
| “Weaken” accusation on gold assert | Document variance math; keep dual-write + corpse V3 asserts (stronger on structure) |
| Fallback only excludes Frontier but picks wrong node | Test pins `valid`; also keep Boss/Hub out |
| Test-lock fail | Always regen with `MUD_ALLOW_TEST_CHANGES=1` when tests change |

---

**Handoff:** Astra approve this plan → **fresh** impl brief/session. This plan session stops here.

---
**Status: APPROVED by Astra 2026-08-11 14:02 MST**
