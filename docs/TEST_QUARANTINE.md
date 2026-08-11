# Test Quarantine

**Ticket:** MUD-008 · **Repair wave:** MUD-017 (slice 1) · **MUD-020 (slice 2 cleared 2026-08-11)**  
**Baseline date:** 2026-08-10 · **Post-slice-1 count:** 20 · **Post-slice-2 count:** 12

Known failing tests are tagged `@Tag("quarantine")` and **excluded by default** from green verify lanes. They remain runnable and hard-fail on the quarantine lane. Do **not** weaken asserts to force green.

## How tags work

| Mode | How | Behavior |
|------|-----|----------|
| Default (green) | `./gradlew :reasoning:test` or verify `--core` / `--full` | `excludeTags("quarantine")` |
| Quarantine only | `./gradlew :reasoning:test -Pmud.quarantineOnly=true` or `./tools/verify_mud.sh --quarantine` | `includeTags("quarantine")` only; **non-zero exit OK** |
| Include all | `./gradlew :reasoning:test -Pmud.includeQuarantine=true` | No tag filter (debug) |

Convention plugin: `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.

Tag form (method-level preferred):

```kotlin
// quarantine: one-line reason
@Tag("quarantine")
@Test
fun `…`() { … }
```

## Baseline metadata (2026-08-10)

| Artifact | Command | Result |
|----------|---------|--------|
| `tmp/workers/MUD-008/baseline-reasoning-20260810.log` | `./gradlew :reasoning:test --continue` | **644** tests · **621** pass · **23** fail · **0** skip |
| `tmp/workers/MUD-008/baseline-core-20260810.log` | `./tools/verify_mud.sh --core` (pre-tag; no reasoning) | **PASS** · core **462** + perception **56** + memory **321** = **839** pass |

Original post-tag green reasoning (default exclude): **621** tests (644 − 23).  
**MUD-017 slice 1 (2026-08-11):** cleared **3** tags → quarantine count **20**. Green reasoning under exclude: **624** expected (644 − 20).  
**MUD-020 slice 2 (2026-08-11):** cleared **8** tags → quarantine count **12**. Green reasoning under exclude: **632** expected (644 − 12).

Core lane includes green `:reasoning:test` under excludeTags.

### Cleared in MUD-017 slice 1

| Class#method | Fix |
|--------------|-----|
| `CapacityCalculatorTest#calculateCapacity - base capacity scales linearly` | Test aligned STR-0 expected with `MINIMUM_CAPACITY` (10.0); linear asserts at 5/20 kept |
| `ThemeRegistryTest#getAllThemeNames returns all themes` | Count 9 + membership includes `training grounds` |
| `ThemeRegistryTest#getProfileSemantic matches magma keywords` | Prod: magma keyword `"volcanic"` so volcanic/lava/fire map to magma cave |

### Cleared in MUD-020 slice 2

| Class#method | Fix |
|--------------|-----|
| `SkillDefinitionsTest#category getters return correct skills` | Test: combat size 11 + membership of all 11 names; KDoc 6→11 |
| `SkillClassifierTest#fallback classification returns only available skills` | Prod: `fallbackClassification` filters candidates by `hasSkill`, then renormalize |
| `SkillClassifierTest#fallback classification returns empty list when no applicable skills` | Prod: same filter → empty when entity has no matching skills |
| `SkillClassifierTest#LLM classification filters out skills entity doesn't have` | Prod: `parseSkillWeights` filters catalog ∩ entity `hasSkill` |
| `SkillClassifierTest#classification with empty skill list returns empty result` | Prod: empty component → empty after filter |
| `DungeonInitializerSimpleTest#initializeDeepDungeon creates complete hierarchy` | Test: 4 REGIONs (Training Grounds + Upper/Mid/Lower) |
| `DungeonInitializerSimpleTest#initializeDeepDungeon creates regions with correct difficulty` | Test: sorted difficulties [1, 5, 12, 18] |
| `DungeonInitializerSimpleTest#initializeDeepDungeon creates parent-child relationships` | Test: world.children size 4; each REGION parent = WORLD |

## Quarantined tests (12)

| Class#method | Reason |
|--------------|--------|
| `LoreInheritanceEngineTest#varyLore generates lore variation with parent keywords` | fallback lore no longer embeds parent keywords |
| `DeathHandlerTest#NPC death drops loot into space and corpse` | V3 death/loot path assert mismatch (space/corpse) |
| `TreasureRoomPlacerTest#selectTreasureRoomNode excludes Boss and Frontier nodes` | placer selected Frontier node as treasure room |
| `WorldGeneratorTest#generateChunk creates REGION level with parent lore variation` | region lore no longer references parent keywords |
| `SkillManagerTest#defensive skills progress independently for different entities` | defensive skill isolation assert failed post progression rewrite |
| `SkillManagerTest#attemptSkillProgress with lucky success unlocks skill at level 1` | lucky unlock starts at level 2 not 1 (progression formula drift) |
| `SkillManagerTest#grantXp grants full XP on success` | XP/level expectations drift after dual-path progression |
| `SkillManagerTest#grantXp fails for unlocked skill` | grantXp no longer fails for unlocked skill as expected |
| `SkillManagerTest#defender can unlock Dodge through lucky progression` | Dodge lucky unlock level drift (expected 1, got 2) |
| `SkillManagerTest#grantXp grants 20 percent XP on failure` | failure XP scale drift (expected 20, got 200) |
| `SkillManagerTest#grantXp triggers level-up when threshold crossed` | level-up threshold/level count drift |
| `SkillManagerTest#grantXp handles multiple level-ups` | multi level-up count drift (expected 4, got 9) |

## Residual risk

- Counts may drift if new tests are added or failures change; re-baseline before expanding the list.
- Only **consistent** fails from the recorded baseline were tagged — flakes (if any later) should be noted, not silently `@Disabled`.
- **Deferred clusters (12 residual):** SkillManager ×8 (L1/L2 Jason) + MUD-021 cluster (Lore/WorldGenerator ×2, DeathHandler ×1, TreasureRoomPlacer ×1).

## Related

- Verify lanes: `tools/verify_mud.sh`
- Testing guide status: `docs/TESTING.md` (Current Test Status)
- Ticket: `issues/MUD-008-test-baseline-quarantine.md` · repair: `issues/MUD-017-clear-reasoning-quarantine.md`
