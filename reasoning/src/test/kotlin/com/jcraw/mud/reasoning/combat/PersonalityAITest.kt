package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.SocialComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for PersonalityAI - personality-based behavior modulation
 *
 * Tests cover:
 * - Flee threshold variations by personality
 * - Defense preference by personality
 * - Aggressive behavior enforcement
 * - Decision modification based on traits
 * - Action preference weights
 * - Flavor text generation
 */
class PersonalityAITest {

    @Test
    fun `shouldFlee returns true for cowardly NPC at 48% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "cowardly goblin",
            traits = listOf("cowardly")
        )

        val combat = CombatComponent(
            currentHp = 24,
            maxHp = 50, // 48% HP
            actionTimerEnd = 0L
        )

        assertTrue(PersonalityAI.shouldFlee(social, combat),
            "Cowardly NPCs should flee below 50% HP")
    }

    @Test
    fun `shouldFlee returns false for cowardly NPC at 60% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "cowardly goblin",
            traits = listOf("cowardly")
        )

        val combat = CombatComponent(
            currentHp = 30,
            maxHp = 50, // 60% HP
            actionTimerEnd = 0L
        )

        assertFalse(PersonalityAI.shouldFlee(social, combat),
            "Cowardly NPCs should not flee above 50% HP")
    }

    @Test
    fun `shouldFlee returns false for brave NPC at 30% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "brave warrior",
            traits = listOf("brave")
        )

        val combat = CombatComponent(
            currentHp = 15,
            maxHp = 50, // 30% HP
            actionTimerEnd = 0L
        )

        assertFalse(PersonalityAI.shouldFlee(social, combat),
            "Brave NPCs should not flee at 30% HP")
    }

    @Test
    fun `shouldFlee returns true for brave NPC at 5% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "brave warrior",
            traits = listOf("brave")
        )

        val combat = CombatComponent(
            currentHp = 2,
            maxHp = 50, // 4% HP
            actionTimerEnd = 0L
        )

        assertTrue(PersonalityAI.shouldFlee(social, combat),
            "Even brave NPCs should flee when near death (<10% HP)")
    }

    @Test
    fun `shouldFlee returns false for aggressive NPC at 20% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "aggressive orc",
            traits = listOf("aggressive")
        )

        val combat = CombatComponent(
            currentHp = 10,
            maxHp = 50, // 20% HP
            actionTimerEnd = 0L
        )

        assertFalse(PersonalityAI.shouldFlee(social, combat),
            "Aggressive NPCs should not flee at 20% HP")
    }

    @Test
    fun `shouldFlee returns true for default NPC at 25% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "ordinary guard",
            traits = listOf()
        )

        val combat = CombatComponent(
            currentHp = 12,
            maxHp = 50, // 24% HP
            actionTimerEnd = 0L
        )

        assertTrue(PersonalityAI.shouldFlee(social, combat),
            "Default NPCs should flee below 30% HP")
    }

    @Test
    fun `prefersDefense returns true for defensive NPC at 60% HP`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "defensive knight",
            traits = listOf("defensive")
        )

        val combat = CombatComponent(
            currentHp = 30,
            maxHp = 50, // 60% HP
            actionTimerEnd = 0L
        )

        assertTrue(PersonalityAI.prefersDefense(social, combat),
            "Defensive NPCs should prefer defense below 70% HP")
    }

    @Test
    fun `prefersDefense returns false for defensive NPC at 80% HP`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "defensive knight",
            traits = listOf("defensive")
        )

        val combat = CombatComponent(
            currentHp = 40,
            maxHp = 50, // 80% HP
            actionTimerEnd = 0L
        )

        assertFalse(PersonalityAI.prefersDefense(social, combat),
            "Defensive NPCs should not prefer defense above 70% HP")
    }

    @Test
    fun `isAggressiveOnly returns true for aggressive NPC`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "aggressive berserker",
            traits = listOf("aggressive")
        )

        assertTrue(PersonalityAI.isAggressiveOnly(social),
            "Should identify aggressive NPCs")
    }

    @Test
    fun `isAggressiveOnly returns true for reckless NPC`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "reckless fool",
            traits = listOf("reckless")
        )

        assertTrue(PersonalityAI.isAggressiveOnly(social),
            "Should identify reckless NPCs as aggressive-only")
    }

    @Test
    fun `isAggressiveOnly returns false for normal NPC`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "ordinary guard",
            traits = listOf()
        )

        assertFalse(PersonalityAI.isAggressiveOnly(social),
            "Normal NPCs should not be aggressive-only")
    }

    @Test
    fun `modifyDecision converts Flee to Attack for aggressive NPC`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "aggressive orc",
            traits = listOf("aggressive")
        )

        val combat = CombatComponent(
            currentHp = 10,
            maxHp = 50,
            actionTimerEnd = 0L
        )

        val originalDecision = AIDecision.Flee("Low HP")
        val modified = PersonalityAI.modifyDecision(originalDecision, social, combat)

        assertTrue(modified is AIDecision.Attack,
            "Aggressive NPCs should never flee")
        assertTrue(modified.reasoning.contains("Aggressive"),
            "Reasoning should mention aggressive nature")
    }

    @Test
    fun `modifyDecision converts Wait to Attack for aggressive NPC`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "aggressive berserker",
            traits = listOf("aggressive")
        )

        val combat = CombatComponent(
            currentHp = 40,
            maxHp = 50,
            actionTimerEnd = 0L
        )

        val originalDecision = AIDecision.Wait("Waiting for opportunity")
        val modified = PersonalityAI.modifyDecision(originalDecision, social, combat)

        assertTrue(modified is AIDecision.Attack,
            "Aggressive NPCs should never wait")
    }

    @Test
    fun `modifyDecision converts Defend to Attack for aggressive NPC`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "aggressive warrior",
            traits = listOf("aggressive")
        )

        val combat = CombatComponent(
            currentHp = 30,
            maxHp = 50,
            actionTimerEnd = 0L
        )

        val originalDecision = AIDecision.Defend("Protecting self")
        val modified = PersonalityAI.modifyDecision(originalDecision, social, combat)

        assertTrue(modified is AIDecision.Attack,
            "Aggressive NPCs should prefer offense over defense")
    }

    @Test
    fun `modifyDecision forces Flee for cowardly NPC at 45% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "cowardly goblin",
            traits = listOf("cowardly")
        )

        val combat = CombatComponent(
            currentHp = 22,
            maxHp = 50, // 44% HP
            actionTimerEnd = 0L
        )

        val originalDecision = AIDecision.Attack("player", "Attacking")
        val modified = PersonalityAI.modifyDecision(originalDecision, social, combat)

        assertTrue(modified is AIDecision.Flee,
            "Cowardly NPCs should flee below 50% HP")
        assertTrue(modified.reasoning.contains("Cowardly"),
            "Reasoning should mention cowardly nature")
    }

    @Test
    fun `modifyDecision converts Attack to Defend for defensive NPC at 60% HP`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "defensive knight",
            traits = listOf("defensive")
        )

        val combat = CombatComponent(
            currentHp = 30,
            maxHp = 50, // 60% HP
            actionTimerEnd = 0L
        )

        val originalDecision = AIDecision.Attack("player", "Attacking")
        val modified = PersonalityAI.modifyDecision(originalDecision, social, combat)

        assertTrue(modified is AIDecision.Defend,
            "Defensive NPCs should defend when injured")
        assertTrue(modified.reasoning.contains("Defensive"),
            "Reasoning should mention defensive nature")
    }

    @Test
    fun `modifyDecision converts Flee to Attack for greedy NPC at 30% HP`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "greedy bandit",
            traits = listOf("greedy")
        )

        val combat = CombatComponent(
            currentHp = 15,
            maxHp = 50, // 30% HP
            actionTimerEnd = 0L
        )

        val originalDecision = AIDecision.Flee("Low HP")
        val modified = PersonalityAI.modifyDecision(originalDecision, social, combat)

        assertTrue(modified is AIDecision.Attack,
            "Greedy NPCs should not flee when above 20% HP")
        assertTrue(modified.reasoning.contains("Greedy") || modified.reasoning.contains("loot"),
            "Reasoning should mention greed or loot")
    }

    @Test
    fun `modifyDecision does not change decision for normal NPC`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "ordinary guard",
            traits = listOf()
        )

        val combat = CombatComponent(
            currentHp = 30,
            maxHp = 50,
            actionTimerEnd = 0L
        )

        val originalDecision = AIDecision.Attack("player", "Standard attack")
        val modified = PersonalityAI.modifyDecision(originalDecision, social, combat)

        assertEquals(originalDecision, modified,
            "Normal NPCs should not have decisions modified")
    }

    @Test
    fun `getActionPreferences returns high Attack weight for aggressive NPC`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "aggressive orc",
            traits = listOf("aggressive")
        )

        val prefs = PersonalityAI.getActionPreferences(social)

        assertTrue(prefs["Attack"]!! > 1.5,
            "Aggressive NPCs should strongly prefer Attack (got ${prefs["Attack"]})")
        assertTrue(prefs["Flee"]!! < 0.5,
            "Aggressive NPCs should avoid Flee (got ${prefs["Flee"]})")
    }

    @Test
    fun `getActionPreferences returns high Defend weight for defensive NPC`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "defensive knight",
            traits = listOf("defensive")
        )

        val prefs = PersonalityAI.getActionPreferences(social)

        assertTrue(prefs["Defend"]!! > 1.5,
            "Defensive NPCs should strongly prefer Defend (got ${prefs["Defend"]})")
    }

    @Test
    fun `getActionPreferences returns high Flee weight for cowardly NPC`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "cowardly goblin",
            traits = listOf("cowardly")
        )

        val prefs = PersonalityAI.getActionPreferences(social)

        assertTrue(prefs["Flee"]!! > 1.5,
            "Cowardly NPCs should strongly prefer Flee (got ${prefs["Flee"]})")
        assertTrue(prefs["Attack"]!! < 1.0,
            "Cowardly NPCs should avoid Attack (got ${prefs["Attack"]})")
    }

    @Test
    fun `getActionPreferences returns balanced weights for normal NPC`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "ordinary guard",
            traits = listOf()
        )

        val prefs = PersonalityAI.getActionPreferences(social)

        assertEquals(1.0, prefs["Attack"] ?: 1.0, 0.01,
            "Normal NPCs should have balanced Attack weight")
        assertEquals(1.0, prefs["Defend"] ?: 1.0, 0.01,
            "Normal NPCs should have balanced Defend weight")
        assertEquals(1.0, prefs["Flee"] ?: 1.0, 0.01,
            "Normal NPCs should have balanced Flee weight")
    }

    @Test
    fun `getActionFlavor returns aggressive text for aggressive NPC attacking`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "aggressive orc",
            traits = listOf("aggressive")
        )

        val decision = AIDecision.Attack("player", "Charging")
        val flavor = PersonalityAI.getActionFlavor(decision, social)

        assertTrue(flavor.contains("aggressive"),
            "Flavor should mention aggressive behavior")
    }

    @Test
    fun `getActionFlavor returns brave text for brave NPC attacking`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "brave knight",
            traits = listOf("brave")
        )

        val decision = AIDecision.Attack("player", "Fighting")
        val flavor = PersonalityAI.getActionFlavor(decision, social)

        assertTrue(flavor.contains("bravely"),
            "Flavor should mention brave behavior")
    }

    @Test
    fun `getActionFlavor returns fearful text for cowardly NPC fleeing`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "cowardly goblin",
            traits = listOf("cowardly")
        )

        val decision = AIDecision.Flee("Running away")
        val flavor = PersonalityAI.getActionFlavor(decision, social)

        assertTrue(flavor.contains("terror") || flavor.contains("fear"),
            "Flavor should mention fear for cowardly NPCs")
    }

    @Test
    fun `getActionFlavor returns tactical text for defensive NPC defending`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "defensive knight",
            traits = listOf("defensive")
        )

        val decision = AIDecision.Defend("Protecting")
        val flavor = PersonalityAI.getActionFlavor(decision, social)

        assertTrue(flavor.contains("carefully") || flavor.contains("guard"),
            "Flavor should mention careful/guarding for defensive NPCs")
    }

    @Test
    fun `getActionFlavor returns reckless text for reckless NPC attacking`() {
        val social = SocialComponent(
            disposition = -100,
            personality = "reckless barbarian",
            traits = listOf("reckless")
        )

        val decision = AIDecision.Attack("player", "Wild attack")
        val flavor = PersonalityAI.getActionFlavor(decision, social)

        assertTrue(flavor.contains("recklessly"),
            "Flavor should mention reckless behavior")
    }

    @Test
    fun `getActionFlavor returns generic text for normal NPC`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "ordinary guard",
            traits = listOf()
        )

        val decision = AIDecision.Attack("player", "Standard attack")
        val flavor = PersonalityAI.getActionFlavor(decision, social)

        assertEquals("attacks", flavor,
            "Normal NPCs should get generic flavor text")
    }

    @Test
    fun `multiple traits combine correctly - brave and defensive`() {
        val social = SocialComponent(
            disposition = 0,
            personality = "brave defender",
            traits = listOf("brave", "defensive")
        )

        val combat = CombatComponent(
            currentHp = 30,
            maxHp = 50, // 60% HP
            actionTimerEnd = 0L
        )

        // Brave trait should take priority (flee at 10%, not defensive 35%)
        assertFalse(PersonalityAI.shouldFlee(social, combat),
            "Brave trait should prevent fleeing at 60% HP")
    }
}
