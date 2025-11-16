package com.jcraw.mud.core

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val id: PlayerId,
    val name: String,
    val currentRoomId: RoomId,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val stats: Stats = Stats(),
    val inventory: List<Entity.Item> = emptyList(), // Legacy - use inventoryComponent instead
    val equippedWeapon: Entity.Item? = null, // Legacy - use inventoryComponent.equipped instead
    val equippedArmor: Entity.Item? = null, // Legacy - use inventoryComponent.equipped instead
    val skills: Map<String, Int> = emptyMap(),
    val properties: Map<String, String> = emptyMap(),
    val revealedExits: Set<String> = emptySet(), // V3: Hidden exits revealed by Perception checks
    val activeQuests: List<Quest> = emptyList(),
    val completedQuests: List<QuestId> = emptyList(),
    val experiencePoints: Int = 0,
    val gold: Int = 0, // Legacy - use inventoryComponent.gold instead
    val inventoryComponent: InventoryComponent? = null // V2 inventory system
) {
    fun addToInventory(item: Entity.Item): PlayerState = copy(inventory = inventory + item)

    fun removeFromInventory(itemId: String): PlayerState = copy(inventory = inventory.filter { it.id != itemId })

    fun getInventoryItem(itemId: String): Entity.Item? = inventory.find { it.id == itemId }

    fun hasItem(itemId: String): Boolean = inventory.any { it.id == itemId }

    fun moveToRoom(roomId: RoomId): PlayerState = copy(currentRoomId = roomId)

    fun takeDamage(damage: Int): PlayerState = copy(health = (health - damage).coerceAtLeast(0))

    fun heal(amount: Int): PlayerState = copy(health = (health + amount).coerceAtMost(maxHealth))

    fun isDead(): Boolean = health <= 0

    fun getSkillLevel(skillName: String): Int = skills[skillName] ?: 0

    fun setSkillLevel(skillName: String, level: Int): PlayerState = copy(skills = skills + (skillName to level))

    fun equipWeapon(weapon: Entity.Item): PlayerState {
        // Add old weapon back to inventory if present
        val updatedInventory = if (equippedWeapon != null) {
            inventory + equippedWeapon
        } else {
            inventory
        }
        // Remove new weapon from inventory and equip it
        return copy(
            equippedWeapon = weapon,
            inventory = updatedInventory.filter { it.id != weapon.id }
        )
    }

    fun unequipWeapon(): PlayerState {
        return if (equippedWeapon != null) {
            copy(
                equippedWeapon = null,
                inventory = inventory + equippedWeapon
            )
        } else {
            this
        }
    }

    /**
     * Get weapon damage bonus from equipped weapon.
     *
     * @deprecated This method uses legacy inventory system. For V2 combat, use
     * `SkillModifierCalculator.getWeaponDamage()` with V2 inventory (inventoryComponent.equipped)
     * and ItemRepository templates. AttackResolver and DamageCalculator handle this automatically.
     */
    @Deprecated(
        message = "Use SkillModifierCalculator.getWeaponDamage() with V2 inventory instead",
        replaceWith = ReplaceWith(
            "skillModifierCalculator.getWeaponDamage(inventoryComponent?.equipped ?: emptyMap(), templates)",
            "com.jcraw.mud.reasoning.skills.SkillModifierCalculator"
        )
    )
    fun getWeaponDamageBonus(): Int = equippedWeapon?.damageBonus ?: 0

    fun equipArmor(armor: Entity.Item): PlayerState {
        // Add old armor back to inventory if present
        val updatedInventory = if (equippedArmor != null) {
            inventory + equippedArmor
        } else {
            inventory
        }
        // Remove new armor from inventory and equip it
        return copy(
            equippedArmor = armor,
            inventory = updatedInventory.filter { it.id != armor.id }
        )
    }

    fun unequipArmor(): PlayerState {
        return if (equippedArmor != null) {
            copy(
                equippedArmor = null,
                inventory = inventory + equippedArmor
            )
        } else {
            this
        }
    }

    /**
     * Get armor defense bonus from equipped armor.
     *
     * @deprecated This method uses legacy inventory system. For V2 combat, use
     * `SkillModifierCalculator.getTotalArmorDefense()` with V2 inventory (inventoryComponent.equipped)
     * and ItemRepository templates. AttackResolver and DamageCalculator handle this automatically.
     */
    @Deprecated(
        message = "Use SkillModifierCalculator.getTotalArmorDefense() with V2 inventory instead",
        replaceWith = ReplaceWith(
            "skillModifierCalculator.getTotalArmorDefense(inventoryComponent?.equipped ?: emptyMap(), templates)",
            "com.jcraw.mud.reasoning.skills.SkillModifierCalculator"
        )
    )
    fun getArmorDefenseBonus(): Int = equippedArmor?.defenseBonus ?: 0

    fun useConsumable(item: Entity.Item): PlayerState {
        return if (item.isConsumable) {
            val newHealth = (health + item.healAmount).coerceAtMost(maxHealth)
            copy(
                health = newHealth,
                inventory = inventory.filter { it.id != item.id }
            )
        } else {
            this
        }
    }

    // Quest management
    fun addQuest(quest: Quest): PlayerState = copy(activeQuests = activeQuests + quest)

    fun removeQuest(questId: QuestId): PlayerState = copy(activeQuests = activeQuests.filter { it.id != questId })

    fun getQuest(questId: QuestId): Quest? = activeQuests.find { it.id == questId }

    fun updateQuest(updatedQuest: Quest): PlayerState {
        val updatedQuests = activeQuests.map { if (it.id == updatedQuest.id) updatedQuest else it }
        return copy(activeQuests = updatedQuests)
    }

    fun completeQuest(questId: QuestId): PlayerState {
        val quest = getQuest(questId) ?: return this
        return copy(
            completedQuests = completedQuests + questId,
            activeQuests = activeQuests.filter { it.id != questId }
        )
    }

    fun claimQuestReward(questId: QuestId): PlayerState {
        val quest = getQuest(questId) ?: return this
        if (!quest.isComplete()) return this

        return copy(
            experiencePoints = experiencePoints + quest.reward.experiencePoints,
            gold = gold + quest.reward.goldAmount,
            inventory = inventory + quest.reward.items
        ).updateQuest(quest.claim())
    }

    fun hasQuest(questId: QuestId): Boolean = activeQuests.any { it.id == questId }

    fun hasCompletedQuest(questId: QuestId): Boolean = completedQuests.contains(questId)

    // V3 Hidden Exit Methods

    /**
     * Reveal a hidden exit by its edge ID (fromSpaceId:targetSpaceId)
     */
    fun revealExit(edgeId: String): PlayerState = copy(revealedExits = revealedExits + edgeId)

    /**
     * Check if an exit has been revealed
     */
    fun hasRevealedExit(edgeId: String): Boolean = revealedExits.contains(edgeId)

    // V2 Inventory Component Methods

    /**
     * Get or initialize the inventory component
     * Creates a new one if it doesn't exist, with capacity based on Strength
     */
    fun getOrInitInventory(): InventoryComponent {
        return inventoryComponent ?: InventoryComponent(
            capacityWeight = stats.strength * 5.0
        )
    }

    /**
     * Update inventory component
     */
    fun updateInventory(component: InventoryComponent): PlayerState {
        return copy(inventoryComponent = component)
    }

    /**
     * Add item instance to V2 inventory
     * Returns null if item cannot be added (weight limit)
     */
    fun addItemInstance(instance: ItemInstance, templates: Map<String, ItemTemplate>): PlayerState? {
        val inv = getOrInitInventory()
        val template = templates[instance.templateId] ?: return null

        if (!inv.canAdd(template, instance.quantity, templates)) {
            return null // Over weight limit
        }

        return updateInventory(inv.addItem(instance))
    }

    /**
     * Add gold to V2 inventory
     */
    fun addGoldV2(amount: Int): PlayerState {
        val inv = getOrInitInventory()
        return updateInventory(inv.addGold(amount))
    }

    /**
     * Remove gold from V2 inventory
     * Returns null if insufficient gold
     */
    fun removeGoldV2(amount: Int): PlayerState? {
        val inv = getOrInitInventory()
        val updated = inv.removeGold(amount) ?: return null
        return updateInventory(updated)
    }

    /**
     * Check if player has sufficient gold in V2 inventory
     */
    fun hasGoldV2(amount: Int): Boolean {
        return getOrInitInventory().gold >= amount
    }
}