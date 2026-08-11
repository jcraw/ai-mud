# Test Quarantine

**Ticket:** MUD-008 · **Repair wave:** MUD-017 (slice 1) · **MUD-020 (slice 2)** · **MUD-021 (slice 3)** · **MUD-022 (SkillManager clear 2026-08-11)**  
**Baseline date:** 2026-08-10 · **Post-slice-1 count:** 20 · **Post-slice-2 count:** 12 · **Post-slice-3 count:** 8 · **Post-MUD-022 count:** **0**

Known failing tests are tagged `@Tag("quarantine")` and **excluded by default** from green verify lanes. They remain runnable and hard-fail on the quarantine lane. Do **not** weaken asserts to force green.

## How tags work

| Mode | How | Behavior |
|------|-----|----------|
| Default (green) | `./gradlew :reasoning:test` or verify `--core` / `--full` | `excludeTags("quarantine")` |
| Quarantine only | `./gradlew :reasoning:test -Pmud.quarantineOnly=true` or `./tools/verify_mud.sh --quarantine` | `includeTags("quarantine")` only; empty set → exit 0 |
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
**MUD-021 slice 3 (2026-08-11):** cleared **4** tags → quarantine count **8**. Green reasoning under exclude: **636** expected (644 − 8).  
**MUD-022 (2026-08-11):** cleared **8** SkillManager tags → quarantine count **0**. Green reasoning under exclude: **644** expected (full suite).

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

### Cleared in MUD-021 slice 3

| Class#method | Fix |
|--------------|-----|
| `LoreInheritanceEngineTest#varyLore generates lore variation with parent keywords` | Harness: MockLLMClient embeds full parent first line + direction adj (Valdor / kingdom of Valdor + northern) |
| `WorldGeneratorTest#generateChunk creates REGION level with parent lore variation` | Harness: `createMockLoreEngine` MockLLMClient embeds parent keywords on lore variation |
| `DeathHandlerTest#NPC death drops loot into space and corpse` | Re-contract: V3 dual-write (corpse/itemsDropped/Entity.Item); gold floor `>= 1` (variance 0.8–1.2) |
| `TreasureRoomPlacerTest#selectTreasureRoomNode excludes Boss and Frontier nodes` | Prod: shared `isTreasureEligible` (not Hub/Boss/Frontier) in candidates + empty fallback |

### Cleared in MUD-022 (SkillManager ×8)

| Class#method | Fix |
|--------------|-----|
| `SkillManagerTest#grantXp grants full XP on success` | Prod: `GameConfig.skillXpMultiplier` default **1.0f** (was 10× test leak) |
| `SkillManagerTest#grantXp grants 20 percent XP on failure` | Prod: mult 1.0 → failure XP 20 (20% of base) |
| `SkillManagerTest#grantXp triggers level-up when threshold crossed` | Prod: mult 1.0 → 300+150 crosses 400 → L2 |
| `SkillManagerTest#grantXp handles multiple level-ups` | Prod: mult 1.0 → 3000 XP → L4 (not L9) |
| `SkillManagerTest#grantXp auto-unlocks locked skill when level reaches 1` | Test re-contract: locked grantXp **succeeds** + auto-unlock (use-based; was wrong Failure expect) |
| `SkillManagerTest#attemptSkillProgress with lucky success unlocks skill at level 1` | Hard assert unlock + L1 (lucky or XP path with mult 1) |
| `SkillManagerTest#defender can unlock Dodge through lucky progression` | Hard assert Dodge unlock + L1 |
| `SkillManagerTest#defensive skills progress independently for different entities` | Force XP path (`enableLuckyProgression=false`); compare XP across entities |

## Quarantined tests (0)

_None._ Quarantine residual is empty.

## Residual risk

- Counts may drift if new tests are added or failures change; re-baseline before expanding the list.
- Only **consistent** fails from the recorded baseline were tagged — flakes (if any later) should be noted, not silently `@Disabled`.
- **Residual: 0** — SkillManager dual-path drift cleared in MUD-022.

## Related

- Verify lanes: `tools/verify_mud.sh`
- Testing guide status: `docs/TESTING.md` (Current Test Status)
- Ticket: `issues/MUD-008-test-baseline-quarantine.md` · repair: `issues/MUD-017-clear-reasoning-quarantine.md` · clear: `issues/MUD-022-skillmanager-quarantine-clear.md`
