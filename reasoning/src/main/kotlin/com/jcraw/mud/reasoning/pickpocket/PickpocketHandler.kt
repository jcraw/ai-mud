package com.jcraw.mud.reasoning.pickpocket

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.random.Random

/**
 * Handles pickpocketing logic with stealth/agility skill checks vs perception
 * Includes disposition consequences and wariness status application
 */
class PickpocketHandler(
    private val itemRepository: ItemRepository,
    private val random: Random = Random.Default
) {

    /**
     * Result of a pickpocket attempt
     */
    sealed class PickpocketResult {
        /**
         * Pickpocket succeeded
         * @param playerInventory Updated player inventory
         * @param targetNpc Updated target NPC (with item removed/added, or wariness status)
         * @param targetSocial Updated social component for target
         * @param targetInventory Updated inventory for target NPC
         * @param itemName Name of item stolen/placed
         * @param action "stole" or "placed"
         * @param roll The d20 roll value
         * @param total Total skill check (roll + modifier)
         * @param dc Difficulty class (target's perception)
         */
        data class Success(
            val playerInventory: InventoryComponent,
            val targetNpc: Entity.NPC,
            val targetSocial: SocialComponent?,
            val targetInventory: InventoryComponent?,
            val itemName: String,
            val action: String,
            val roll: Int,
            val total: Int,
            val dc: Int
        ) : PickpocketResult()

        /**
         * Pickpocket failed - caught by target
         * @param targetNpc Updated NPC with wariness status
         * @param targetSocial Updated social component with disposition penalty
         * @param targetCombat Updated combat component with wariness status
         * @param dispositionDelta How much disposition decreased
         * @param roll The d20 roll value
         * @param total Total skill check (roll + modifier)
         * @param dc Difficulty class (target's perception)
         * @param margin How badly they failed (negative number)
         */
        data class Caught(
            val targetNpc: Entity.NPC,
            val targetSocial: SocialComponent,
            val targetCombat: CombatComponent?,
            val dispositionDelta: Int,
            val roll: Int,
            val total: Int,
            val dc: Int,
            val margin: Int
        ) : PickpocketResult()

        /**
         * Pickpocket attempt invalid
         * @param reason Human-readable error message
         */
        data class Failure(val reason: String) : PickpocketResult()
    }

    /**
     * Attempt to steal an item or gold from an NPC
     *
     * @param playerInventory Current player inventory
     * @param playerSkills Player's skill component
     * @param targetNpc Target NPC to pickpocket
     * @param itemTarget Optional item name to steal (null = steal gold)
     * @param templates All item templates for lookups
     * @return PickpocketResult with updated states or failure reason
     */
    fun stealFromNPC(
        playerInventory: InventoryComponent,
        playerSkills: SkillComponent,
        targetNpc: Entity.NPC,
        itemTarget: String? = null,
        templates: Map<String, ItemTemplate>
    ): PickpocketResult {
        // Get target's inventory component
        val targetInventory = targetNpc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
            ?: return PickpocketResult.Failure("Target has no inventory")

        // Get target's social component (for disposition tracking)
        val targetSocial = targetNpc.getSocialComponent()

        // Determine what to steal
        val stealGold = itemTarget == null || itemTarget.equals("gold", ignoreCase = true)

        if (stealGold) {
            // Stealing gold
            if (targetInventory.gold <= 0) {
                return PickpocketResult.Failure("Target has no gold")
            }
        } else {
            // Stealing specific item - find it
            val item = targetInventory.items.firstOrNull { instance ->
                val templateResult = itemRepository.findTemplateById(instance.templateId)
                templateResult.getOrNull()?.name?.equals(itemTarget, ignoreCase = true) == true
            }
            if (item == null) {
                return PickpocketResult.Failure("Target doesn't have that item")
            }
        }

        // Perform skill check: Stealth or Agility (higher) vs target's Perception passive DC
        val skillCheckResult = performPickpocketCheck(playerSkills, targetNpc)

        if (skillCheckResult.success) {
            // Success - steal the item or gold
            return if (stealGold) {
                val stolenAmount = (targetInventory.gold * 0.3).toInt().coerceAtLeast(1)
                val updatedPlayerInventory = playerInventory.addGold(stolenAmount)
                val updatedTargetInventory = targetInventory.removeGold(stolenAmount)!!
                val updatedTargetNpc = targetNpc.withComponent(updatedTargetInventory)

                PickpocketResult.Success(
                    playerInventory = updatedPlayerInventory,
                    targetNpc = updatedTargetNpc,
                    targetSocial = targetSocial,
                    targetInventory = updatedTargetInventory,
                    itemName = "$stolenAmount gold",
                    action = "stole",
                    roll = skillCheckResult.roll,
                    total = skillCheckResult.total,
                    dc = skillCheckResult.dc
                )
            } else {
                // Steal specific item
                val item = targetInventory.items.first { instance ->
                    val templateResult = itemRepository.findTemplateById(instance.templateId)
                    templateResult.getOrNull()?.name?.equals(itemTarget, ignoreCase = true) == true
                }
                val template = itemRepository.findTemplateById(item.templateId).getOrNull()!!

                // Check if player can carry it
                if (!playerInventory.canAdd(template, 1, templates)) {
                    return PickpocketResult.Failure("You can't carry that much (weight limit exceeded)")
                }

                val updatedPlayerInventory = playerInventory.addItem(item)
                val updatedTargetInventory = targetInventory.removeItem(item.id)!!
                val updatedTargetNpc = targetNpc.withComponent(updatedTargetInventory)

                PickpocketResult.Success(
                    playerInventory = updatedPlayerInventory,
                    targetNpc = updatedTargetNpc,
                    targetSocial = targetSocial,
                    targetInventory = updatedTargetInventory,
                    itemName = template.name,
                    action = "stole",
                    roll = skillCheckResult.roll,
                    total = skillCheckResult.total,
                    dc = skillCheckResult.dc
                )
            }
        } else {
            // Failure - caught! Apply disposition penalty and wariness status
            return handleCaughtPickpocketing(targetNpc, skillCheckResult, targetSocial)
        }
    }

    /**
     * Attempt to place an item in an NPC's inventory (sneaky tactics)
     *
     * @param playerInventory Current player inventory
     * @param playerSkills Player's skill component
     * @param targetNpc Target NPC to place item on
     * @param instanceId Item instance ID to place
     * @param templates All item templates for lookups
     * @return PickpocketResult with updated states or failure reason
     */
    fun placeItemOnNPC(
        playerInventory: InventoryComponent,
        playerSkills: SkillComponent,
        targetNpc: Entity.NPC,
        instanceId: String,
        templates: Map<String, ItemTemplate>
    ): PickpocketResult {
        // Get player's item
        val item = playerInventory.getItem(instanceId)
            ?: return PickpocketResult.Failure("You don't have that item")

        // Get item template
        val templateResult = itemRepository.findTemplateById(item.templateId)
        if (templateResult.isFailure || templateResult.getOrNull() == null) {
            return PickpocketResult.Failure("Item template not found")
        }
        val template = templateResult.getOrNull()!!

        // Get target's inventory component
        val targetInventory = targetNpc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
            ?: return PickpocketResult.Failure("Target has no inventory")

        // Get target's social component
        val targetSocial = targetNpc.getSocialComponent()

        // Check if target can carry it
        if (!targetInventory.canAdd(template, 1, templates)) {
            return PickpocketResult.Failure("Target can't carry that much")
        }

        // Perform skill check
        val skillCheckResult = performPickpocketCheck(playerSkills, targetNpc)

        if (skillCheckResult.success) {
            // Success - place the item
            val updatedPlayerInventory = playerInventory.removeItem(instanceId)!!
            val updatedTargetInventory = targetInventory.addItem(item)
            val updatedTargetNpc = targetNpc.withComponent(updatedTargetInventory)

            return PickpocketResult.Success(
                playerInventory = updatedPlayerInventory,
                targetNpc = updatedTargetNpc,
                targetSocial = targetSocial,
                targetInventory = updatedTargetInventory,
                itemName = template.name,
                action = "placed",
                roll = skillCheckResult.roll,
                total = skillCheckResult.total,
                dc = skillCheckResult.dc
            )
        } else {
            // Failure - caught!
            return handleCaughtPickpocketing(targetNpc, skillCheckResult, targetSocial)
        }
    }

    /**
     * Perform pickpocket skill check: max(Stealth, Agility) vs Perception passive DC
     *
     * @param playerSkills Player's skill component
     * @param targetNpc Target NPC
     * @return Skill check result with success/failure and values
     */
    private fun performPickpocketCheck(
        playerSkills: SkillComponent,
        targetNpc: Entity.NPC
    ): SkillCheckResult {
        // Get player's stealth and agility levels
        val stealthLevel = playerSkills.getEffectiveLevel("Stealth")
        val agilityLevel = playerSkills.getEffectiveLevel("Agility")
        val pickpocketSkill = maxOf(stealthLevel, agilityLevel)

        // Calculate target's passive Perception DC (10 + Wisdom modifier + Perception skill bonus)
        val targetWisModifier = targetNpc.stats.wisModifier()
        val targetSkills = targetNpc.getComponent<SkillComponent>(ComponentType.SKILL)
        val targetPerceptionSkill = targetSkills?.getEffectiveLevel("Perception") ?: 0

        // Check if target has wariness status (adds +20 to perception)
        val targetCombat = targetNpc.getComponent<CombatComponent>(ComponentType.COMBAT)
        val warinessBonus = if (targetCombat?.statusEffects?.any { it.type == StatusEffectType.WARINESS } == true) {
            20
        } else {
            0
        }

        val dc = 10 + targetWisModifier + targetPerceptionSkill + warinessBonus

        // Roll d20 + pickpocket skill
        val roll = random.nextInt(1, 21)
        val total = roll + pickpocketSkill
        val success = total >= dc
        val margin = total - dc

        return SkillCheckResult(
            success = success,
            roll = roll,
            modifier = pickpocketSkill,
            total = total,
            dc = dc,
            margin = margin,
            isCriticalSuccess = roll == 20,
            isCriticalFailure = roll == 1
        )
    }

    /**
     * Handle consequences of being caught pickpocketing
     *
     * @param targetNpc Target NPC who caught the player
     * @param skillCheckResult Skill check result
     * @param targetSocial Target's social component
     * @return PickpocketResult.Caught with disposition penalty and wariness status
     */
    private fun handleCaughtPickpocketing(
        targetNpc: Entity.NPC,
        skillCheckResult: SkillCheckResult,
        targetSocial: SocialComponent?
    ): PickpocketResult.Caught {
        // Calculate disposition penalty based on how badly they failed
        // Margin is negative, so we negate it and scale: -20 to -50
        val dispositionDelta = (-20 - (kotlin.math.abs(skillCheckResult.margin) * 3)).coerceAtMost(-20).coerceAtLeast(-50)

        // Apply disposition change
        val updatedSocial = (targetSocial ?: SocialComponent(personality = "ordinary", traits = emptyList()))
            .applyDispositionChange(dispositionDelta)

        // Apply wariness status effect (+20 Perception for 10 turns)
        val targetCombat = targetNpc.getComponent<CombatComponent>(ComponentType.COMBAT)
        val warinessEffect = StatusEffect(
            type = StatusEffectType.WARINESS,
            magnitude = 20,
            duration = 10,
            source = "pickpocket_failure"
        )

        val updatedCombat = if (targetCombat != null) {
            // Add wariness to existing status effects
            val newEffects = targetCombat.statusEffects + warinessEffect
            targetCombat.copy(statusEffects = newEffects)
        } else {
            // Create new combat component with wariness
            CombatComponent(
                maxHp = targetNpc.maxHealth,
                currentHp = targetNpc.health,
                statusEffects = listOf(warinessEffect)
            )
        }

        // Update NPC with new components
        val updatedNpc = targetNpc
            .withComponent(updatedSocial)
            .withComponent(updatedCombat)

        return PickpocketResult.Caught(
            targetNpc = updatedNpc,
            targetSocial = updatedSocial,
            targetCombat = updatedCombat,
            dispositionDelta = dispositionDelta,
            roll = skillCheckResult.roll,
            total = skillCheckResult.total,
            dc = skillCheckResult.dc,
            margin = skillCheckResult.margin
        )
    }
}
