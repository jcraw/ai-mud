package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*

/**
 * Handles combat behavior triggers and automatic counter-attacks
 *
 * Integrates with the social disposition system to create emergent combat:
 * - Attacking a neutral/friendly NPC makes them hostile
 * - Hostile NPCs are automatically added to the turn queue
 * - Combat emerges from dispositions rather than explicit combat modes
 */
object CombatBehavior {

    /**
     * Trigger counter-attack behavior for an NPC that was attacked
     *
     * This method:
     * 1. Sets NPC disposition to -100 (hostile)
     * 2. Creates CombatComponent if not present
     * 3. Adds NPC to turn queue if not already present
     *
     * @param npcId The ID of the NPC that was attacked
     * @param spaceId The space where the NPC is located
     * @param worldState Current world state
     * @param turnQueue Turn queue manager (modified in-place)
     * @return Updated WorldState with hostile NPC
     */
    fun triggerCounterAttack(
        npcId: String,
        spaceId: SpaceId,
        worldState: WorldState,
        turnQueue: TurnQueueManager
    ): WorldState {
        val npc = worldState.getEntity(npcId) as? Entity.NPC ?: return worldState

        // 1. Set disposition to hostile (-100)
        val socialComponent = npc.getSocialComponent() ?: SocialComponent(
            personality = "ordinary",
            traits = emptyList()
        )
        val hostileSocial = socialComponent.copy(disposition = -100)
        val npcWithHostileDisposition = npc.withComponent(hostileSocial) as Entity.NPC

        // 2. Ensure NPC has CombatComponent
        val npcWithCombat = ensureCombatComponent(npcWithHostileDisposition, worldState)

        // 3. Add to turn queue if not already present
        addToTurnQueueIfNeeded(npcWithCombat, worldState, turnQueue)

        // Update entity in global storage
        return worldState.updateEntity(npcWithCombat)
    }

    /**
     * Make multiple NPCs hostile (e.g., guards alerted by combat)
     *
     * @param npcIds List of NPC IDs to make hostile
     * @param spaceId The space where the NPCs are located
     * @param worldState Current world state
     * @param turnQueue Turn queue manager (modified in-place)
     * @return Updated WorldState with all NPCs now hostile
     */
    fun triggerGroupHostility(
        npcIds: List<String>,
        spaceId: SpaceId,
        worldState: WorldState,
        turnQueue: TurnQueueManager
    ): WorldState {
        var updatedState = worldState
        npcIds.forEach { npcId ->
            updatedState = triggerCounterAttack(npcId, spaceId, updatedState, turnQueue)
        }
        return updatedState
    }

    /**
     * Ensure an NPC has a CombatComponent
     * Creates one if not present, based on NPC skills
     *
     * @param npc The NPC to check
     * @param worldState Current world state (for context, unused in V1)
     * @return NPC with CombatComponent guaranteed
     */
    private fun ensureCombatComponent(npc: Entity.NPC, worldState: WorldState): Entity.NPC {
        // Check if already has combat component
        if (npc.hasComponent(ComponentType.COMBAT)) {
            return npc
        }

        // Create new combat component based on skills
        val skillComponent: SkillComponent? = npc.getComponent(ComponentType.SKILL)
        val combatComponent = CombatComponent.create(
            skills = skillComponent,
            itemHpBonus = 0 // V1: No item bonuses for NPCs
        )

        return npc.withComponent(combatComponent) as Entity.NPC
    }

    /**
     * Add NPC to turn queue if not already present
     * Calculates action cost based on Speed skill
     *
     * @param npc The NPC to add to queue
     * @param worldState Current world state (for game time)
     * @param turnQueue Turn queue manager (modified in-place)
     */
    private fun addToTurnQueueIfNeeded(
        npc: Entity.NPC,
        worldState: WorldState,
        turnQueue: TurnQueueManager
    ) {
        // Skip if already in queue
        if (turnQueue.contains(npc.id)) {
            return
        }

        // Get NPC's Speed skill level
        val skillComponent: SkillComponent? = npc.getComponent(ComponentType.SKILL)
        val speedLevel = skillComponent?.getEffectiveLevel("Speed") ?: 0

        // Calculate action cost (time until NPC can act)
        val actionCost = ActionCosts.calculateCost(ActionCosts.MELEE_ATTACK, speedLevel)

        // Add to queue with current game time + action cost
        val timerEnd = worldState.gameTime + actionCost
        println("[TURN QUEUE DEBUG] Adding ${npc.name} to queue: gameTime=${worldState.gameTime}, actionCost=$actionCost, timerEnd=$timerEnd")
        turnQueue.enqueue(npc.id, timerEnd)
    }

    /**
     * Check if an NPC should initiate combat based on disposition
     * This is called during game loop to check for proactive attacks
     *
     * @param npc The NPC to check
     * @return true if NPC should initiate attack
     */
    fun shouldInitiateCombat(npc: Entity.NPC): Boolean {
        return CombatInitiator.isHostile(npc)
    }

    /**
     * De-escalate combat by improving NPC disposition
     * Used for persuasion/intimidation success during combat
     *
     * @param npcId The NPC ID
     * @param spaceId The space where the NPC is located
     * @param newDisposition New disposition value (should be >= -75 to end hostility)
     * @param worldState Current world state
     * @param turnQueue Turn queue manager (modified in-place to remove NPC if no longer hostile)
     * @return Updated WorldState
     */
    fun deEscalateCombat(
        npcId: String,
        spaceId: SpaceId,
        newDisposition: Int,
        worldState: WorldState,
        turnQueue: TurnQueueManager
    ): WorldState {
        val npc = worldState.getEntity(npcId) as? Entity.NPC ?: return worldState

        // Update disposition
        val socialComponent = npc.getSocialComponent() ?: SocialComponent(
            personality = "ordinary",
            traits = emptyList()
        )
        val updatedSocial = socialComponent.copy(disposition = newDisposition)
        val updatedNpc = npc.withComponent(updatedSocial) as Entity.NPC

        // If no longer hostile, remove from turn queue
        if (newDisposition >= CombatInitiator.HOSTILE_THRESHOLD) {
            turnQueue.remove(npcId)
        }

        // Update entity in global storage
        return worldState.updateEntity(updatedNpc)
    }
}
