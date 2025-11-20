# Generic Skill Progression System - Implementation Plan

**Status**: 📋 Planned (Ready for Implementation)
**Estimated Effort**: 6-10 hours
**Priority**: High (Core gameplay system)
**Dependencies**: Skill System V2 (✅ Complete)

## Executive Summary

Extend the skill progression system to work consistently across ALL skill types (offensive, defensive, crafting, social, gathering) with dual progression paths: **lucky chance** (instant level-up) OR **XP accumulation** (grind-based), both scaling with difficulty as skills level up.

### Key Features
- ✨ Dual progression paths for all skills (luck OR grind)
- 🛡️ Defensive skills (Dodge, Parry) finally gain XP when used
- 📊 Scaling difficulty - gets grindier at higher levels
- 🔄 Generic application - works for combat, crafting, gathering, social, etc.
- ⚙️ Configurable via GameConfig

## Current State Analysis

### What Works ✅
- **Unlocking (0→1)**: 15% random chance OR 100 XP (use-based progression)
- **Leveling (1→N)**: XP-only with quadratic formula `100 * level^2`
  - Level 1: 100 XP
  - Level 2: 400 XP
  - Level 3: 900 XP
  - Level 10: 10,000 XP
- **XP System**: Implemented in `SkillManager.grantXp()` with success/failure modifiers
- **Offensive Combat**: Attack skills gain XP via `attemptSkillUnlocks()` in CombatHandlers.kt

### What's Broken ❌
- **Defensive Skills**: Dodge/Parry are USED in defense calculation (AttackResolver.kt:97-101) but don't gain XP
- **Chance-based leveling**: After unlock, all progression is XP-only (no lucky level-ups)
- **Manual implementation**: Each skill domain manually implements progression (not generic)

## Proposed Design

### 1. Dual Progression Paths

Every skill use triggers BOTH progression paths:

```kotlin
// Pseudocode for every skill use:
1. Roll for lucky progression (chance-based instant level-up)
2. If lucky roll fails → Grant XP (accumulation-based)
```

**Philosophy**: Players can get lucky and skip the grind, OR they can grind their way to guaranteed progress. Both paths are always active.

### 2. Scaling Chance Formula

**Formula**: `floor(BASE_CHANCE / sqrt(targetLevel + 1))`
**Constant**: `BASE_CHANCE = 15` (configurable in GameConfig)

**Results Table**:

| From Level | To Level | Chance | Approximate Odds | XP Required |
|------------|----------|--------|------------------|-------------|
| 0 | 1 | 15% | 1 in 7 attempts | 100 XP |
| 1 | 2 | 11% | 1 in 9 attempts | 400 XP |
| 2 | 3 | 9% | 1 in 11 attempts | 900 XP |
| 5 | 6 | 6% | 1 in 16 attempts | 3,600 XP |
| 10 | 11 | 5% | 1 in 20 attempts | 12,100 XP |
| 20 | 21 | 3% | 1 in 30 attempts | 44,100 XP |
| 50 | 51 | 2% | 1 in 50 attempts | 260,100 XP |
| 99 | 100 | 1.5% | 1 in 67 attempts | 1,000,000 XP |

**Design Rationale**:
- Square root provides smooth scaling (not too harsh, not too generous)
- High-level players still have SOME chance (never 0%)
- Balances with quadratic XP requirement (XP gets harder faster than chance drops)
- At high levels, XP path becomes dominant (as intended - grindy endgame)

### 3. Defensive Skills

**Problem**: Dodge and Parry are calculated during defense (AttackResolver.kt:97-101) but never gain XP.

**Solution**: Track defender's skills in `AttackResult` and grant XP based on defense outcome.

#### AttackResult Enhancement

**Current**:
```kotlin
sealed class AttackResult {
    abstract val skillsUsed: List<String>  // Only attacker skills!
}
```

**Enhanced**:
```kotlin
sealed class AttackResult {
    abstract val attackerSkillsUsed: List<String>
    abstract val defenderSkillsUsed: List<String>  // NEW!

    data class Hit(
        // ... existing fields ...
        override val attackerSkillsUsed: List<String>,
        override val defenderSkillsUsed: List<String>,  // Dodge, Parry used
        val defenseOutcome: DefenseOutcome  // How defense was beaten
    ) : AttackResult()

    data class Miss(
        // ... existing fields ...
        override val attackerSkillsUsed: List<String>,
        override val defenderSkillsUsed: List<String>,  // Dodge, Parry used
        val defenseOutcome: DefenseOutcome  // Dodged/Parried/Blocked
    ) : AttackResult()
}

enum class DefenseOutcome {
    DODGED,      // Dodge skill was primary contributor
    PARRIED,     // Parry skill was primary contributor
    BLOCKED,     // Both contributed equally
    OVERWHELMED  // Defense attempted but attacker won
}
```

#### Defense Outcome Calculation

```kotlin
// In AttackResolver.resolveAttack()
val defenseOutcome = if (!isHit) {
    // Attack missed - which skill saved them?
    val dodgeContribution = defenderSkills?.getEffectiveLevel("Dodge")?.times(0.6) ?: 0.0
    val parryContribution = defenderSkills?.getEffectiveLevel("Parry")?.times(0.4) ?: 0.0
    when {
        dodgeContribution > parryContribution * 1.5 -> DefenseOutcome.DODGED
        parryContribution > dodgeContribution * 1.5 -> DefenseOutcome.PARRIED
        else -> DefenseOutcome.BLOCKED
    }
} else {
    DefenseOutcome.OVERWHELMED
}
```

### 4. Generic Skill Progression Method

**New Method**: `SkillManager.attemptSkillProgress()`

```kotlin
/**
 * Attempt skill progression using dual-path system
 *
 * 1. Roll for lucky progression (chance-based instant level-up)
 * 2. If lucky roll fails, grant XP (accumulation-based)
 *
 * Works for any skill: combat (attack/defend), crafting, gathering, social, etc.
 *
 * @param entityId Entity attempting progression
 * @param skillName Skill being used
 * @param baseXp Base XP to grant if lucky roll fails
 * @param success Whether the skill use was successful
 * @return List of SkillEvents (SkillUnlocked, LevelUp, XpGained)
 */
fun attemptSkillProgress(
    entityId: String,
    skillName: String,
    baseXp: Long,
    success: Boolean
): Result<List<SkillEvent>> {
    return runCatching {
        val component = getSkillComponent(entityId)
        val currentSkill = component.getSkill(skillName) ?: SkillState()

        val events = mutableListOf<SkillEvent>()

        // Path 1: Lucky progression (chance-based)
        val targetLevel = if (!currentSkill.unlocked) 1 else currentSkill.level + 1
        val luckyChance = calculateLuckyChance(targetLevel)
        val roll = rng.nextInt(1, 101)

        if (roll <= luckyChance) {
            // Lucky progression!
            val updatedSkill = if (!currentSkill.unlocked) {
                currentSkill.unlock().copy(level = 1)
            } else {
                currentSkill.copy(level = currentSkill.level + 1)
            }

            // Update component and save
            val newComponent = component.updateSkill(skillName, updatedSkill)
            updateSkillComponent(entityId, newComponent).getOrThrow()
            skillRepo.save(entityId, skillName, updatedSkill).getOrThrow()

            // Generate events
            if (!currentSkill.unlocked) {
                events.add(SkillEvent.SkillUnlocked(
                    entityId = entityId,
                    skillName = skillName,
                    unlockMethod = "lucky progression"
                ))
                skillRepo.logEvent(events.last()).getOrThrow()
            }

            events.add(SkillEvent.LevelUp(
                entityId = entityId,
                skillName = skillName,
                oldLevel = currentSkill.level,
                newLevel = updatedSkill.level,
                isAtPerkMilestone = updatedSkill.isAtPerkMilestone()
            ))
            skillRepo.logEvent(events.last()).getOrThrow()

            return Result.success(events)
        }

        // Path 2: Lucky roll failed, grant XP instead
        val xpEvents = grantXp(entityId, skillName, baseXp, success).getOrThrow()
        events.addAll(xpEvents)

        events
    }
}

/**
 * Calculate lucky progression chance for target level
 * Formula: floor(15 / sqrt(targetLevel + 1))
 */
private fun calculateLuckyChance(targetLevel: Int): Int {
    val baseChance = GameConfig.baseLuckyChance.toDouble()
    return floor(baseChance / sqrt(targetLevel + 1.0)).toInt()
}
```

## Implementation Phases

### Phase 1: Core Enhancement (2-3h)

**Files**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/combat/AttackResolver.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillManager.kt`
- `config/src/main/kotlin/com/jcraw/mud/config/GameConfig.kt`

**Tasks**:
1. Add `DefenseOutcome` enum to AttackResolver.kt (after line 256)
2. Update `AttackResult.Hit` and `AttackResult.Miss` data classes:
   - Rename `skillsUsed` → `attackerSkillsUsed`
   - Add `defenderSkillsUsed: List<String>`
   - Add `defenseOutcome: DefenseOutcome`
3. Update `AttackResult` companion factory methods `hit()` and `miss()`
4. Add `calculateLuckyChance(targetLevel: Int): Int` to SkillManager.kt
5. Add `attemptSkillProgress()` to SkillManager.kt
6. Add configuration to GameConfig.kt:
   ```kotlin
   var baseLuckyChance: Int = 15
   var enableLuckyProgression: Boolean = true
   ```

**Estimated lines**: +170 lines

### Phase 2: Combat Integration (1-2h)

**Files**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/combat/AttackResolver.kt`
- `app/src/main/kotlin/com/jcraw/app/handlers/CombatHandlers.kt`

**Tasks**:
1. Update `AttackResolver.resolveAttack()` (lines 97-119):
   - Track defender skills: `listOf("Dodge", "Parry")`
   - Calculate `defenseOutcome` based on dodge/parry contributions
   - Return defender skills in AttackResult
2. Refactor `CombatHandlers.attemptSkillUnlocks()` → `processSkillProgression()`:
   - Handle BOTH attacker and defender skills
   - Use `attemptSkillProgress()` for both
   - Determine success based on AttackResult type:
     - Attacker: Hit = success, Miss = failure
     - Defender: Miss = success (dodged!), Hit = failure
3. Update `displaySkillEvents()` to show lucky progression messages

**Estimated lines**: ~50 lines changed, net +30 lines

### Phase 3: Generic Application (1-2h)

**Files**:
- `app/src/main/kotlin/com/jcraw/app/handlers/SkillQuestHandlers.kt`

**Tasks**:
1. Update `handleCraft()` to use `attemptSkillProgress()`:
   - Success path (lines 434-441)
   - Failure path (lines 470-476)
2. Update `handleInteract()` gathering to use `attemptSkillProgress()` (if applicable)
3. Update social skill handlers (persuade, intimidate) to use `attemptSkillProgress()`

**Estimated lines**: ~30 lines changed

### Phase 4: Client Sync (1h)

**Files**:
- `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientCombatHandlers.kt`

**Tasks**:
1. Mirror all CombatHandlers.kt changes for GUI client
2. Update `processSkillProgression()` to use `game.emitEvent()` instead of `println()`

**Estimated lines**: +30 lines

### Phase 5: Testing & Polish (1-2h)

**Tasks**:
1. Write unit tests:
   - `SkillManagerTest.test_calculateLuckyChance_scaling()`
   - `SkillManagerTest.test_attemptSkillProgress_luckyUnlock()`
   - `SkillManagerTest.test_attemptSkillProgress_luckyLevelUp()`
   - `AttackResolverTest.test_defenderSkillsTracked()`
2. Write integration tests:
   - Combat: Verify defender gains Dodge/Parry XP
   - Crafting: Verify lucky progression can trigger
3. Playtest and tune `baseLuckyChance` constant

**Estimated lines**: +200 test lines

## Example Walkthrough

### Scenario: Dodging an Attack at Dodge Level 5

**Setup**:
- Defender has Dodge level 5, Parry level 2
- Attacker swings: rolls 12, adds modifier +8 = 20
- Defender rolls 18, adds modifier: (5*0.6 + 2*0.4).toInt() = 3
- Defense total: 18 + 3 = 21
- **Result**: Attack MISSES (20 ≤ 21)

**Skill Progression Triggered**:

1. **DefenseOutcome Calculation**:
   - Dodge contribution: 5 × 0.6 = 3.0
   - Parry contribution: 2 × 0.4 = 0.8
   - 3.0 > 0.8 × 1.5? YES (3.0 > 1.2)
   - **Outcome**: `DefenseOutcome.DODGED`

2. **Defender Skill Progression**:

   **Dodge (Level 5 → 6)**:
   - Lucky chance: `floor(15 / sqrt(6 + 1))` = `floor(15 / 2.646)` = **5%**
   - Roll: d100 → 73 (failed)
   - Fallback to XP: Grant 10 XP (defense succeeded)
   - Current XP: 230/3600 → **240/3600**
   - Event: XpGained (silent in combat)

   **Parry (Level 2 → 3)**:
   - Lucky chance: `floor(15 / sqrt(3 + 1))` = `floor(15 / 2.0)` = **7%**
   - Roll: d100 → **6** (SUCCESS!)
   - **Lucky progression triggered!**
   - Parry instantly levels up: 2 → 3
   - Event: LevelUp

3. **Display Output** (console):
```
The goblin misses you - you nimbly dodge aside!

🎉 Parry leveled up! 2 → 3 (lucky progression!)
```

4. **Attacker Skill Progression** (NPC):
   - NPC's attack skills also attempt progression
   - But only if `enableLuckyProgression` is true (maybe disable for NPCs?)

## Configuration

### GameConfig.kt

```kotlin
/**
 * Base chance percentage for lucky skill progression
 * Formula: floor(baseLuckyChance / sqrt(targetLevel + 1))
 *
 * Examples (baseLuckyChance = 15):
 * - Level 0→1: 15%
 * - Level 10→11: 5%
 * - Level 99→100: 1.5%
 *
 * Default: 15
 */
var baseLuckyChance: Int = 15

/**
 * Enable/disable lucky progression (chance-based level-ups)
 * If false, all progression uses XP-only path
 *
 * Default: true
 */
var enableLuckyProgression: Boolean = true

/**
 * Enable lucky progression for NPCs
 * If false, only players get lucky level-ups (NPCs use XP-only)
 *
 * Rationale: NPCs already have preset skill levels, random level-ups
 * during combat could make encounters unpredictable.
 *
 * Default: false
 */
var enableNPCLuckyProgression: Boolean = false
```

## Benefits

### For Players
- ✨ **Exciting moments**: Lucky level-ups feel great, break up the grind
- 🎯 **Player agency**: Can choose to grind OR get lucky
- 🛡️ **Defensive equity**: Dodge/Parry finally progress like offensive skills
- 📈 **Consistent rules**: All skills use same progression system

### For Developers
- 🔄 **Generic system**: Easy to add new skill types (just call `attemptSkillProgress()`)
- ⚙️ **Configurable**: Tune difficulty via `baseLuckyChance`
- 🧪 **Testable**: Clear separation of lucky vs XP paths
- 📊 **Scalable**: Works from level 1 to level 100+

## Trade-offs

### Pros
- More engaging progression (lucky moments feel great)
- Reduces high-level grind without removing it entirely
- Defensive skills finally get rewarded
- Generic system works everywhere (combat, crafting, social, etc.)

### Cons
- RNG can feel unfair (one player gets lucky at L50, another doesn't)
- Slightly more complex than XP-only system
- Lucky progression might devalue achievement at high levels

### Mitigation
- XP path is ALWAYS available (luck just accelerates, never required)
- Probabilities are tuned to be rare but possible at high levels (1.5% at L99)
- Configuration flags allow disabling lucky progression if desired
- Can disable for NPCs to keep encounters predictable

## Testing Strategy

### Unit Tests

**SkillManagerTest.kt**:
```kotlin
fun test_calculateLuckyChance_level0() {
    assertEquals(15, skillManager.calculateLuckyChance(0))
}

fun test_calculateLuckyChance_level1() {
    assertEquals(11, skillManager.calculateLuckyChance(1))
}

fun test_calculateLuckyChance_level20() {
    assertEquals(3, skillManager.calculateLuckyChance(20))
}

fun test_attemptSkillProgress_luckyUnlock() {
    // Mock RNG to return 1 (guaranteed lucky roll)
    // Verify skill unlocks at level 1
}

fun test_attemptSkillProgress_fallbackToXp() {
    // Mock RNG to return 100 (guaranteed fail)
    // Verify XP is granted instead
}
```

**AttackResolverTest.kt**:
```kotlin
fun test_defenderSkillsTracked() {
    // Resolve attack
    // Verify AttackResult.defenderSkillsUsed contains ["Dodge", "Parry"]
}

fun test_defenseOutcome_dodgePriority() {
    // High dodge, low parry
    // Verify defenseOutcome == DefenseOutcome.DODGED
}
```

### Integration Tests

**Combat Integration**:
- Attack an NPC 20 times, verify defensive skills gain XP
- Mock RNG for lucky progression, verify level-ups trigger
- Verify both attacker and defender skills progress

**Crafting Integration**:
- Craft 10 items, verify lucky progression can trigger
- Verify XP grants on both success and failure

## Success Criteria

### Phase 1-2 Complete When:
- ✅ Defensive skills (Dodge, Parry) gain XP when used in combat
- ✅ `attemptSkillProgress()` method works for any skill
- ✅ Lucky progression triggers at correct percentages
- ✅ Both attacker and defender skills progress in combat

### Phase 3-4 Complete When:
- ✅ Crafting uses `attemptSkillProgress()`
- ✅ Gathering uses `attemptSkillProgress()`
- ✅ Social skills use `attemptSkillProgress()`
- ✅ GUI client mirrors console behavior

### Phase 5 Complete When:
- ✅ Unit tests pass for chance calculation
- ✅ Integration tests pass for defensive skills
- ✅ Playtesting confirms system feels balanced
- ✅ `baseLuckyChance` tuned to desired difficulty

## Future Enhancements (Optional)

1. **Skill XP Multipliers by Skill Type**:
   - Combat skills: 1.0x multiplier
   - Crafting skills: 0.8x multiplier (slower)
   - Social skills: 1.2x multiplier (faster)

2. **Luck Streak Mechanic**:
   - After N failed lucky rolls, boost next chance temporarily
   - Prevents long unlucky streaks

3. **Prestige System**:
   - At level 100, option to "prestige" (reset to level 1, keep perks)
   - Unlocks prestige-only perks

4. **Skill Decay**:
   - Unused skills slowly lose XP (not levels) over time
   - Encourages varied playstyles

## Summary

This design provides a complete, generic skill progression system that works identically across all skill types. The dual-path approach (lucky chance OR XP grind) gives players exciting moments while maintaining long-term progression goals. Defensive skills finally get proper rewards, and the system scales smoothly from beginner to endgame content.

**Implementation Order**: Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
**Total Estimate**: 6-10 hours
**Risk Level**: Low (well-defined, clear implementation path)
