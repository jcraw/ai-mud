package com.jcraw.mud.reasoning.skill

/**
 * Method used to unlock a new skill
 * Each method has different mechanics and benefits
 */
sealed class UnlockMethod {
    /**
     * Attempt unlock through trial and error
     * - 15% success chance (d100 <= 15)
     * - No prerequisites
     * - No initial buffs
     */
    data object Attempt : UnlockMethod()

    /**
     * Unlock by observing someone else use the skill
     * - Grants 1.5x XP buff (temporary)
     * - Unlocks the skill immediately
     * - Buff duration: until next rest/sleep
     */
    data class Observation(val observedEntityId: String) : UnlockMethod()

    /**
     * Unlock through training with an NPC mentor
     * - Grants level 1 immediately
     * - Grants 2x XP buff (temporary)
     * - Requires friendly NPC (disposition check)
     * - Buff duration: until next rest/sleep
     */
    data class Training(val mentorEntityId: String) : UnlockMethod()

    /**
     * Unlock automatically via prerequisite skill level
     * - No chance involved, guaranteed unlock
     * - Example: "Advanced Fire Magic" unlocks at "Fire Magic" level 50
     */
    data class Prerequisite(val prerequisiteSkillName: String, val requiredLevel: Int) : UnlockMethod()
}
