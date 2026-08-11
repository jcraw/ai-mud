# MUD-017 Plan — Clear/repair :reasoning quarantine (slice 1)

**Ticket:** MUD-017 · **Worker:** grok · **Phase:** plan_review (await Astra)  
**Impl = fresh session** after Astra approve. Do not resume this plan session for product edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-017-clear-reasoning-quarantine.md`  
**Worker mirror:** `tmp/workers/MUD-017/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --quarantine` **and** still-green `./tools/verify_mud.sh --core`  
**Slices OK:** this plan = **slice 1 only** (target −3 tags); residual → follow-up ticket notes

---

## 1. Goal / acceptance mapping

| # | Acceptance | Slice-1 delivers |
|---|------------|------------------|
| 1 | Triage MUD-008 list; fix in small slices | Full 23 clustered (§2); **slice 1 = Capacity + ThemeRegistry only** (3 methods); defer rest |
| 2 | Un-quarantine only when genuinely fixed | Prod fix and/or intentional-contract test update with **stronger** asserts; un-tag only methods green under quarantine-only **and** default exclude |
| 3 | No delete/weaken/`@Disabled`/silent delete | Forbidden; keep asserts real (floor, full theme list, magma semantic) |
| 4 | Count drops; dod-summary + `TEST_QUARANTINE.md` | **23 → 20**; update list/counts; `quarantine_count` in `tmp/dod-summary.json` |

---

## 2. Current inventory

| Item | Truth |
|------|--------|
| **Tags** | **23** method-level `@Tag("quarantine")` under `reasoning/src/test/**` (MUD-008 baseline 2026-08-10) |
| **Lanes** | Green (`--core`/`--full`): `excludeTags("quarantine")`. Debt: `./tools/verify_mud.sh --quarantine` → `-Pmud.quarantineOnly=true` (hard-fail OK if residual). SoT: `docs/TEST_QUARANTINE.md` |
| **Test-lock** | Ticket scopes `src/test` edits → `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`; commit tests + `tools/test-lock/manifest.sha256` together |
| **MUD-008 residual** | 644/621/23; DeathHandler flaky once on re-run but still tagged; root repair = this ticket |

### Clusters (23) by root cause

| Cluster | # | Classes | Root-cause sketch |
|---------|--:|---------|-------------------|
| **SkillManager XP/level formula** | 8 | `SkillManagerTest` | Dual-path progression drift (lucky unlock L2 vs L1; XP scales; multi-level counts) — **Jason-ish / design** |
| **SkillClassifier fallback** | 4 | `SkillClassifierTest` | Fallback matches more skills / empty-list contract broken (catalog + classifier) |
| **Dungeon region count** | 3 | `DungeonInitializerSimpleTest` | Expected 3 REGIONs, got 4 (init hierarchy growth) |
| **ThemeRegistry** | 2 | `ThemeRegistryTest` | Count 8→9 (`training grounds`); semantic magma null on `"volcanic"` |
| **SkillDefinitions combat count** | 1 | `SkillDefinitionsTest` | `getCombatSkills()` 6→11 catalog growth |
| **Lore keywords** | 2 | `LoreInheritanceEngineTest`, `WorldGeneratorTest` | Fallback/region lore no longer embeds parent keywords |
| **DeathHandler loot** | 1 | `DeathHandlerTest` | V3 space/corpse assert; **flaky** history |
| **TreasureRoomPlacer** | 1 | `TreasureRoomPlacerTest` | Placer picked Frontier (exclusion bug) |
| **CapacityCalculator floor** | 1 | `CapacityCalculatorTest` | Strength 0: expect 0.0, prod `MINIMUM_CAPACITY=10.0` |

---

## 3. Design / recommended approach

### Slice 1 — **chosen: A (Capacity + ThemeRegistry)** — 3 tags, high leverage, low product risk

Agent-clear; no Jason product opinion needed. Prefer **production contract alignment** (documented intentional behavior), not assert-gaming.

#### Cluster C1 — CapacityCalculator (1 method)
- **Prod truth:** `CapacityCalculator.MINIMUM_CAPACITY = 10.0`; `calculateCapacity` ends with `coerceAtLeast(MINIMUM_CAPACITY)`. Sibling green test already asserts floor at STR 0.
- **Failing test:** `calculateCapacity - base capacity scales linearly` still expects `0.0` at `strengthLevel = 0` (stale pre-floor).
- **Strategy (test update to intentional contract):** keep linear asserts at STR 5→25 and 20→100; change STR 0 expected to **`MINIMUM_CAPACITY` (10.0)** (or assert `>= floor` + exact 10.0). **Do not** remove floor from prod; **do not** weaken 5/20 lines. Un-tag when green.

#### Cluster C2 — ThemeRegistry (2 methods)
1. **`getAllThemeNames returns all 8 themes`**
   - **Prod truth:** 9 profiles incl. intentional `"training grounds"`.
   - **Strategy (test):** assert size **9**; keep membership of original 8; **add** `training grounds`; optionally assert set equality of all keys (stronger). Rename method to “all themes” if desired. Un-tag when green.
2. **`getProfileSemantic matches magma keywords`**
   - **Likely prod bug:** keyword `"volcano"` does **not** substring-match `"volcanic"` → `"volcanic cave"` → null. `"lava chamber"` / `"fire cavern"` should already hit `lava`/`fire` branches — **impl re-confirms** under quarantine-only before un-tag.
   - **Strategy (prod fix preferred):** add `"volcanic"` (and if needed `"volcano"` remains) to magma `when` arm so lava/volcanic/fire inputs map to `magma cave` ambiance `"scorching, smoky, unstable"`. Keep asserts; un-tag only when all three inputs pass.

### Defer (not slice 1) — follow-up ticket note e.g. **MUD-017b / new id**

| Defer | Why out of slice 1 |
|-------|--------------------|
| **SkillManager ×8** | Formula ambiguity (lucky unlock start L1 vs L2, XP 20 vs 200, multi-level counts) — mark **Jason product opinion** if design unclear; mass progression rewrite out of scope |
| **SkillClassifier ×4** | May couple to catalog; fix after SkillDefinitions clarity |
| **SkillDefinitions ×1** | Catalog growth agent-clear but **option B**; do after A if time **only if** still green budget — **default stay deferred** |
| **DungeonInitializer ×3** | Multi-region hierarchy; not one-line unless proven |
| **Lore + WorldGenerator ×2** | Keyword embedding contract |
| **DeathHandler ×1** | Flaky; needs careful V3 loot path |
| **TreasureRoomPlacer ×1** | Frontier exclusion logic |

### Docs / lock
- Update `docs/TEST_QUARANTINE.md`: remove 3 rows; baseline count **20**; note slice-1 date.
- AGENTS: **no** change unless count pointer needs “20” (optional 1-line; prefer quarantine doc only).
- Test-lock regen after authorized test edits.

---

## 4. Files to create/touch (impl)

| Path | Action |
|------|--------|
| `reasoning/src/main/.../world/ThemeRegistry.kt` | **Prod:** magma semantic keywords (`volcanic`) |
| `reasoning/src/test/.../skills/CapacityCalculatorTest.kt` | Floor assert at STR 0; remove `@Tag("quarantine")` + reason when green |
| `reasoning/src/test/.../world/ThemeRegistryTest.kt` | Count 9 + training grounds; un-tag both methods when green |
| `docs/TEST_QUARANTINE.md` | List/counts 23→20; residual note for deferred clusters |
| `tools/test-lock/manifest.sha256` | Regen with `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` |
| Optional AGENTS quarantine count | Only if pointer still says 23 |

**Do not touch:** SkillManager bulk, classifier, dungeon init, death, treasure, lore (slice 1); CI workflows; PIT; live LLM/testbot.

---

## 5. Non-goals

- Clearing all 23 in one impl; assert-weaken / delete / `@Disabled` swap
- Mass skill-progression redesign without evidence; Jason-blocked clusters in slice 1
- MUD-007 / 009 / 018; force-push; secrets; commit/push from plan session
- CI workflow edits; PIT changes; testbot live paths

---

## 6. How impl confirms acceptance

- [ ] Quarantine method count: **rg** `@Tag("quarantine")` in `reasoning/src/test` → **20** (was 23)
- [ ] Slice-1 methods un-tagged **only** after green under `:reasoning:test -Pmud.quarantineOnly=true` (for remaining) **and** those methods pass under default exclude / full `:reasoning:test`
- [ ] `./tools/verify_mud.sh --core` → **exit 0**
- [ ] `./tools/verify_mud.sh --quarantine` → may still hard-fail (residual 20 OK); **count dropped** in summary
- [ ] `tmp/dod-summary.json` `quarantine_count` reflects drop (cite path in closeout)
- [ ] `docs/TEST_QUARANTINE.md` list + counts updated; no 3 cleared methods left listed as quarantined
- [ ] Test-lock: manifest regen after test edits; `--core` includes lock pass
- [ ] No weakened asserts; no `@Disabled`; no silent test deletes

---

## 7. Ordered impl steps

1. Re-inventory tags (expect 23); open only C1/C2 prod + test files.
2. **Capacity:** align linear test STR-0 with `MINIMUM_CAPACITY`; run that test green; un-tag.
3. **ThemeRegistry count:** update to 9 + `training grounds` membership (stronger); un-tag when green.
4. **ThemeRegistry magma:** add `volcanic` (prod); confirm lava/volcanic/fire; un-tag when green. If lava/fire still fail, diagnose before un-tag (do not loosen ambiance asserts).
5. `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`.
6. Update `docs/TEST_QUARANTINE.md` (20 remaining; defer list pointer).
7. Verify: `--core` exit 0; `--quarantine` run (residual hard-fail OK); check `quarantine_count`.
8. Closeout: paths, before/after count, dod-summary path, residual risk (20 deferred). **No push unless Jason allowlist.**

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Assert-gaming temptation | Prefer prod fix (magma); capacity test only matches **documented** floor already green elsewhere |
| Formula ambiguity (SkillManager) | **Out of slice 1**; need Jason if L1 vs L2 unlock intentional |
| Flaky DeathHandler | Deferred; do not “fix” by un-tag without stable green |
| Scope creep to all 23 | Hard stop after C1+C2; optional B only if Astra expands |
| Test-lock fail-closed | Always regen with env gate after authorized test edits |
| Magma mis-diagnosis | Confirm which of 3 inputs fail before un-tag |

---

## Astra approve lock

- **Slice 1 = A:** CapacityCalculator floor (test→intentional 10.0) + ThemeRegistry count (test→9) + magma semantic (prod `volcanic`).
- **Target:** 23→20 tags; `--core` green; quarantine lane may remain red.
- **Defer:** SkillManager, Classifier, SkillDefinitions, Dungeon init, Lore/WG, Death, Treasure.


---

Status: APPROVED by Astra 2026-08-11 02:20 MST
Slice 1 lock: Capacity floor test + ThemeRegistry count(9)+magma volcanic prod keyword; 23→20; defer Skill*/Dungeon/Lore/Death/Treasure. Fresh impl session next (plan file handoff).
