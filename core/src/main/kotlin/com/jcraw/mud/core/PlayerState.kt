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
    @Deprecated("Use inventoryComponent.items instead", ReplaceWith("inventoryComponent.items"))
    val inventory: List<Entity.Item> = emptyList(),
    @Deprecated("Use inventoryComponent.getEquipped(EquipSlot.HANDS_MAIN) instead")
    val equippedWeapon: Entity.Item? = null,
    @Deprecated("Use inventoryComponent.getEquipped(EquipSlot.CHEST) instead")
    val equippedArmor: Entity.Item? = null,
    @Deprecated("Use SkillManager.getSkillComponent() instead")
    val skills: Map<String, Int> = emptyMap(),
    val properties: Map<String, String> = emptyMap(),
    val revealedExits: Set<String> = emptySet(), // V3: Hidden exits revealed by Perception checks
    val activeQuests: List<Quest> = emptyList(),
    val completedQuests: List<QuestId> = emptyList(),
    val experiencePoints: Int = 0,
    val inventoryComponent: InventoryComponent = InventoryComponent() // V2 inventory system (non-nullable)
) {
    @Deprecated("Use inventoryComponent.addItem() instead")
    fun addToInventory(item: Entity.Item): PlayerState = copy(inventory = inventory + item)

    @Deprecated("Use inventoryComponent.removeItem() instead")
    fun removeFromInventory(itemId: String): PlayerState = copy(inventory = inventory.filter { it.id != itemId })

    @Deprecated("Use inventoryComponent.getItem() instead")
    fun getInventoryItem(itemId: String): Entity.Item? = inventory.find { it.id == itemId }

    @Deprecated("Use inventoryComponent.items.any() instead")
    fun hasItem(itemId: String): Boolean = inventory.any { it.id == itemId }

    fun moveToRoom(roomId: RoomId): PlayerState = copy(currentRoomId = roomId)

    fun takeDamage(damage: Int): PlayerState = copy(health = (health - damage).coerceAtLeast(0))

    fun heal(amount: Int): PlayerState = copy(health = (health + amount).coerceAtMost(maxHealth))

    fun isDead(): Boolean = health <= 0

    /**
     * Calculate max HP from skills using the same formula as CombatComponent.
     * Formula: 10 + (Vitality*5) + (Endurance*3) + (Constitution*2)
     *
     * @param skillComponent The player's skill component
     * @return Calculated maximum HP (minimum 10)
     */
    fun calculateMaxHp(skillComponent: SkillComponent): Int {
        val vitality = skillComponent.getEffectiveLevel("Vitality")
        val endurance = skillComponent.getEffectiveLevel("Endurance")
        val constitution = skillComponent.getEffectiveLevel("Constitution")

        val baseHp = 10
        val skillHp = (vitality * 5) + (endurance * 3) + (constitution * 2)

        return (baseHp + skillHp).coerceAtLeast(10)
    }

    /**
     * Update max HP while preserving current HP percentage.
     * If current HP is 60/100 (60%) and max HP becomes 120, current HP becomes 72/120 (60%).
     *
     * @param newMaxHp The new maximum HP value
     * @return Updated player state with new max HP and proportional current HP
     */
    fun updateMaxHp(newMaxHp: Int): PlayerState {
        val currentPercent = if (maxHealth > 0) health.toFloat() / maxHealth else 1f
        return copy(
            maxHealth = newMaxHp,
            health = (newMaxHp * currentPercent).toInt().coerceIn(0, newMaxHp)
        )
    }

    @Deprecated("Use inventoryComponent.equip() instead")
    fun equipWeapon(weapon: Entity.Item): PlayerState {
        val updatedInventory = if (equippedWeapon != null) {
            inventory + equippedWeapon
        } else {
            inventory
        }
        return copy(
            equippedWeapon = weapon,
            inventory = updatedInventory.filter { it.id != weapon.id }
        )
    }

    @Deprecated("Use inventoryComponent.unequip() instead")
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

    @Deprecated("Use inventoryComponent.equip() instead")
    fun equipArmor(armor: Entity.Item): PlayerState {
        val updatedInventory = if (equippedArmor != null) {
            inventory + equippedArmor
        } else {
            inventory
        }
        return copy(
            equippedArmor = armor,
            inventory = updatedInventory.filter { it.id != armor.id }
        )
    }

    @Deprecated("Use inventoryComponent.unequip() instead")
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

    @Deprecated("Use inventoryComponent for consumable handling")
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

    @Deprecated("Use inventoryComponent for quest rewards", ReplaceWith("updateInventory(inventoryComponent.addGold(quest.reward.goldAmount))"))
    fun claimQuestReward(questId: QuestId): PlayerState {
        val quest = getQuest(questId) ?: return this
        if (!quest.isComplete()) return this

        return copy(
            experiencePoints = experiencePoints + quest.reward.experiencePoints,
            inventory = inventory + quest.reward.items,
            inventoryComponent = inventoryComponent.addGold(quest.reward.goldAmount)
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

    // Inventory Component Methods

    /**
     * Update inventory component
     */
    fun updateInventory(component: InventoryComponent): PlayerState {
        return copy(inventoryComponent = component)
    }

    /**
     * Add item instance to inventory
     * Returns null if item cannot be added (weight limit)
     */
    fun addItemInstance(instance: ItemInstance, templates: Map<String, ItemTemplate>): PlayerState? {
        val template = templates[instance.templateId] ?: return null

        if (!inventoryComponent.canAdd(template, instance.quantity, templates)) {
            return null // Over weight limit
        }

        return updateInventory(inventoryComponent.addItem(instance))
    }

    /**
     * Add gold to V2 inventory
     */
    fun addGoldV2(amount: Int): PlayerState {
        return updateInventory(inventoryComponent.addGold(amount))
    }

    /**
     * Remove gold from V2 inventory
     * Returns null if insufficient gold
     */
    fun removeGoldV2(amount: Int): PlayerState? {
        val updated = inventoryComponent.removeGold(amount) ?: return null
        return updateInventory(updated)
    }

    /**
     * Check if player has sufficient gold in V2 inventory
     */
    fun hasGoldV2(amount: Int): Boolean {
        return inventoryComponent.gold >= amount
    }

    /**
     * Get current gold amount from V2 inventory
     */
    val gold: Int get() = inventoryComponent.gold
}
