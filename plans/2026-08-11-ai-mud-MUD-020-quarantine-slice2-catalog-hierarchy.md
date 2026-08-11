# MUD-020 Plan — Quarantine slice 2 (catalog + hierarchy)

**Ticket:** MUD-020 · **Worker:** grok · **Phase:** plan_review (await Astra)  
**Impl = fresh session** after Astra approve. Do not resume this plan session for product edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-020-quarantine-slice2-catalog-hierarchy.md`  
**Worker mirror:** `tmp/workers/MUD-020/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` **and** `./tools/verify_mud.sh --quarantine`  
**Depends:** MUD-017 done (20 residual). **Target:** clear **8** → quarantine **20 → ≤12**.

---

## 1. Goal / acceptance mapping (8 methods → fix type)

| # | Method | Fix type | Un-tag when |
|---|--------|----------|-------------|
| 1 | `SkillDefinitionsTest#category getters return correct skills` | **Test → intentional contract** (catalog grew) | combat size 11 + other category counts still hold |
| 2 | `SkillClassifierTest#fallback … only available skills` | **Prod fix** (filter by `hasSkill`) | size 1, Strength only, weight 1.0 |
| 3 | `SkillClassifierTest#fallback … empty when no applicable` | **Prod fix** | empty for non-combat skills + generic attack |
| 4 | `SkillClassifierTest#LLM … filters out skills entity doesn't have` | **Prod fix** (`parseSkillWeights` filter) | size 1 Strength, not Sword Fighting |
| 5 | `SkillClassifierTest#classification with empty skill list…` | **Prod fix** | empty component → empty result |
| 6 | `DungeonInitializerSimpleTest#… complete hierarchy` | **Test → intentional contract** (4 REGIONs) | 1 WORLD, **4** REGION, ZONE/SUBZONE non-empty |
| 7 | `… creates regions with correct difficulty` | **Test → intentional contract** | sorted difficulties **[1,5,12,18]** |
| 8 | `… creates parent-child relationships` | **Test → intentional contract** | world.children **4**; each REGION parent = WORLD |

**Acceptance:** no assert-weaken / `@Disabled` / silent delete; `TEST_QUARANTINE.md` updated; test-lock regen; `--core` 0; quarantine residual hard-fail OK with `quarantine_count` match.

---

## 2. Current inventory (test expect vs prod truth)

### C1 — SkillDefinitions combat (1)
- **Test:** `getCombatSkills().size == 6` (also asserts core 6, rogue 5, elemental 7, advanced 3, resource 4, resistance 3 — those still match).
- **Prod:** `combatSkills` list = **11** entries (Sword, Axe, Bow, Light/Heavy Armor, Shield Use, Dodge, Parry, Unarmed Combat, Escape, Pursuit). Stale KDoc still says “6 skills”.
- **Verdict:** intentional catalog growth; **align test (+ KDoc) to 11**. Prefer membership assert of all 11 names (stronger).

### C2 — SkillClassifier fallback/filter/empty (4)
- **Prod path:** `classifySkills` → LLM if present else `fallbackClassification`. Fallback **always** emits catalog skill names (Sword+STR / else Unarmed+STR, …) and **never** filters by `entitySkills`. Dead helper `addIfHasSkill` unused. LLM `parseSkillWeights` filters to `SkillDefinitions.allSkills.keys` only (catalog), **not** entity membership. KDoc still: “Returns empty list if no skills apply.”
- **Fail modes:**
  1. entity only Strength + “swing sword” → test wants **[Strength 1.0]**; prod returns Sword+Strength (2).
  2. Diplomacy+Blacksmithing + “attack enemy” → test wants **[]**; prod else-branch Unarmed+Strength.
  3. LLM suggests Sword+STR, entity Strength+Vitality → test wants **[Strength]**; prod keeps Sword (in catalog).
  4. empty `SkillComponent` + “attack” → test wants **[]**; prod Unarmed+Strength.
- **Verdict:** unfinished filter refactor (level-0 = use skills present on component even if locked; **not** invent skills absent from component). **Restore `hasSkill` filter** in fallback + LLM parse; renormalize. Green sibling tests already give entity the skills they assert.

### C3 — DungeonInitializer region hierarchy (3)
- **Test:** 3 REGIONs; difficulties 5/12/18; world.children size 3.
- **Prod `initializeDeepDungeon`:** **4** regions — Training Grounds (**1**), Upper (**5**), Mid (**12**), Lower (**18**). Class header KDoc still lists only Upper/Mid/Lower.
- **Note:** `initializeAncientAbyss` is **5** regions (+ Abyssal Core); **out of scope** — these tests call `initializeDeepDungeon` only.
- **Verdict:** intentional hierarchy growth; **align tests** to 4 + Training Grounds difficulty 1.

---

## 3. Design / recommended approach per cluster

### C1 SkillDefinitions — test + comment
- Set `assertEquals(11, combatSkills.size)`; keep other category sizes.
- **Stronger:** `assertEquals(setOf(…11 names…), combatSkills.map { it.name }.toSet())`.
- Fix prod KDoc “6 skills” → “11 skills” (docs-only, same file). Un-tag when green.

### C2 SkillClassifier — **prod filter restore** (preferred)
- **Fallback:** build candidate list as today; **filter** with `entitySkills.hasSkill(name)` (or wire `addIfHasSkill`); then `normalizeWeights`. Empty after filter → `emptyList()`.
- **LLM parse:** filter `it.skill in entitySkills` / `hasSkill`, not only catalog (catalog check may remain as sanity). Empty → empty (caller already falls back if LLM empty — OK).
- **Do not** change keyword mapping branches; **do not** touch SkillManager.
- Un-tag each method only when that method is green under quarantine-only **and** default exclude.
- **Alt (if Astra prefers “catalog-always”):** rewrite 4 tests to document always-emit catalog skills — still **stronger** asserts (exact names/weights), not weaken. Prefer prod restore: matches KDoc + dead helper + tests.

### C3 DungeonInitializer — test + stale KDoc
- Hierarchy: `assertEquals(4, regionChunks.size)`.
- Difficulty: sorted **1, 5, 12, 18** (name optional: Training Grounds / Upper / Mid / Lower).
- Parent-child: `worldChunk.children.size == 4`; each REGION `parentId == world`.
- Update class KDoc REGIONS line to include Training Grounds. Un-tag when green.

### Docs / lock
- `docs/TEST_QUARANTINE.md`: move 8 to cleared table; residual **12**; drop C1/C2/C3 from residual defer; keep SkillManager×8 + MUD-021 cluster.
- Test-lock: `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`.
- Optional AGENTS one-line “20 tagged” → “12” if still points at count (prefer quarantine doc as SoT).

---

## 4. Files to create/touch (impl)

| Path | Action |
|------|--------|
| `reasoning/src/main/.../combat/SkillClassifier.kt` | **Prod:** filter fallback + LLM parse by `hasSkill` |
| `reasoning/src/main/.../skill/SkillDefinitions.kt` | KDoc combat count 6→11 only |
| `reasoning/src/main/.../world/DungeonInitializer.kt` | Optional KDoc hierarchy 3→4 (header) |
| `reasoning/src/test/.../skill/SkillDefinitionsTest.kt` | combat 11 (+ membership); un-tag |
| `reasoning/src/test/.../combat/SkillClassifierTest.kt` | un-tag 4 when green (asserts stay) |
| `reasoning/src/test/.../world/DungeonInitializerSimpleTest.kt` | 4 regions / difficulties / children; un-tag 3 |
| `docs/TEST_QUARANTINE.md` | 20→12; cleared + residual |
| `tools/test-lock/manifest.sha256` | regen with env gate |
| Optional `AGENTS.md` | quarantine count pointer only if still “20” |

**Do not touch:** SkillManager*, Lore*, DeathHandler*, TreasureRoomPlacer*, CI, PIT, testbot live.

---

## 5. Non-goals

- SkillManager ×8 (L1/L2 / XP — Jason)
- Lore/WG ×2, DeathHandler ×1, TreasureRoomPlacer ×1 (**MUD-021**)
- Mass detekt baseline regen; assert-weaken / `@Disabled` / silent delete
- git commit/push; force-push; secrets in plans/logs
- `initializeAncientAbyss` 5-region redesign; SkillDefinitions content redesign beyond count/KDoc

---

## 6. How impl confirms acceptance

- [ ] `rg -c '@Tag\("quarantine"\)' reasoning/src/test` → **12** (was 20)
- [ ] None of the 8 method names still have adjacent `@Tag("quarantine")`
- [ ] Grep residual still lists SkillManager×8 + Lore/Death/Treasure/WG (MUD-021 set)
- [ ] `./tools/verify_mud.sh --core` → **exit 0**
- [ ] `./tools/verify_mud.sh --quarantine` → hard-fail OK; fails **12**; `tmp/dod-summary.json` `quarantine_count` **12**
- [ ] `docs/TEST_QUARANTINE.md` cleared table has 8 rows; residual 12
- [ ] Test-lock green on `--core` after regen
- [ ] No weakened asserts; no `@Disabled`; no deleted tests

---

## 7. Ordered impl steps

1. Re-count tags (expect 20); open only C1–C3 files.
2. **C1:** combat size 11 + membership; KDoc; run method green; un-tag.
3. **C2:** restore `hasSkill` filter in fallback + `parseSkillWeights`; run 4 methods green under quarantine-only; un-tag each; re-run nearby green classifier tests (sword/axe/… siblings).
4. **C3:** update 3 tests to 4 REGIONs + difficulties [1,5,12,18] + children 4; optional KDoc; un-tag.
5. `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`.
6. Update `docs/TEST_QUARANTINE.md` (12 residual; cleared MUD-020 section).
7. Verify: `--core` 0; `--quarantine` residual; cite `quarantine_count`.
8. Closeout note + ticket/BOARD done. **No push unless Jason allowlist.**

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Scope creep into **SkillManager** | Hard stop; only classifier/definitions/dungeon files |
| “Level-0 always catalog” product opinion vs filter restore | Prefer filter (KDoc + dead `addIfHasSkill`); if Astra chooses catalog-always, switch to stronger test rewrites only |
| LLM path still returns unowned skills after partial fix | Filter in **both** fallback and parse |
| Green classifier tests break | They already put asserted skills on entity; re-run full class |
| Dungeon confuse 4 vs 5 regions | Only `initializeDeepDungeon`; do not assert Abyssal Core here |
| Assert-gaming | Keep exact size/weight/membership; expand counts to match prod, do not loosen |
| Test-lock fail-closed | Always regen with `MUD_ALLOW_TEST_CHANGES=1` after test edits |
| Residual ≠ 12 if extra tags drift | Re-`rg` before closeout; fix doc to actual |

---

## Astra approve lock

Impl only after Astra/Jason approve this plan. Fresh impl session; this session = plan only.

---
Status: APPROVED by Astra 2026-08-11 13:02 MST
Common-sense: C1 test→11 combat (+membership/KDoc); C2 prod restore hasSkill filter in fallback+LLM parse; C3 test→4 REGIONs difficulties [1,5,12,18] children 4; target quarantine 20→12; SkillManager×8 + MUD-021 deferred; no assert-weaken/@Disabled/silent delete.
Impl = fresh session (do not resume plan session 019ff25a-5e3c-7121-8017-0c4949ea2a7b).
