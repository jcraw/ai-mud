package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Combat events for logging and narration
 * Captures all combat-related state changes for event system and narration generation
 *
 * Design principles:
 * - Immutable: All events are data classes
 * - Comprehensive: Covers all combat state transitions
 * - Self-documenting: Event names and fields clearly describe what happened
 */
@Serializable
sealed class CombatEvent {
    abstract val gameTime: Long
    abstract val sourceEntityId: String

    /**
     * Damage was dealt to an entity
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that dealt damage (attacker)
     * @param targetEntityId Entity that received damage (defender)
     * @param damageAmount Raw damage dealt (before reductions)
     * @param damageType Type of damage for resistance calculations
     * @param finalHp Target's HP after damage
     * @param wasKilled True if this damage killed the target
     * @param skillsUsed List of skills that contributed to the attack
     */
    @Serializable
    data class DamageDealt(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val targetEntityId: String,
        val damageAmount: Int,
        val damageType: DamageType,
        val finalHp: Int,
        val wasKilled: Boolean = false,
        val skillsUsed: List<String> = emptyList()
    ) : CombatEvent()

    /**
     * Healing was applied to an entity
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that provided healing (healer, or self for potions)
     * @param targetEntityId Entity that received healing
     * @param healAmount Amount healed
     * @param finalHp Target's HP after healing
     * @param wasFromEffect True if healing came from status effect (regeneration)
     */
    @Serializable
    data class HealingApplied(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val targetEntityId: String,
        val healAmount: Int,
        val finalHp: Int,
        val wasFromEffect: Boolean = false
    ) : CombatEvent()

    /**
     * Status effect was applied to an entity
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that applied the effect
     * @param targetEntityId Entity that received the effect
     * @param effect The status effect that was applied
     * @param wasStacked True if effect was stacked with existing effect
     * @param wasReplaced True if effect replaced an existing effect
     */
    @Serializable
    data class StatusEffectApplied(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val targetEntityId: String,
        val effect: StatusEffect,
        val wasStacked: Boolean = false,
        val wasReplaced: Boolean = false
    ) : CombatEvent()

    /**
     * Status effect was removed or expired from an entity
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that had the effect (owner)
     * @param effectType Type of effect that was removed
     * @param wasExpired True if effect expired naturally, false if removed by dispel/cleanse
     */
    @Serializable
    data class StatusEffectRemoved(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val effectType: StatusEffectType,
        val wasExpired: Boolean = true
    ) : CombatEvent()

    /**
     * Status effects ticked (one game time tick passed)
     * Includes all effects that applied damage/healing this tick
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity whose effects ticked
     * @param applications List of effect applications that occurred
     */
    @Serializable
    data class StatusEffectsTicked(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val applications: List<EffectApplication>
    ) : CombatEvent()

    /**
     * Entity's action timer was advanced
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity whose timer was advanced
     * @param actionCost Cost of the action in ticks
     * @param nextActionTime Game time when entity can act next
     */
    @Serializable
    data class ActionTimerAdvanced(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val actionCost: Long,
        val nextActionTime: Long
    ) : CombatEvent()

    /**
     * Entity died
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that died
     * @param killerEntityId Entity that dealt the killing blow (null for environmental)
     * @param wasPlayer True if the dead entity was a player
     */
    @Serializable
    data class EntityDied(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val killerEntityId: String? = null,
        val wasPlayer: Boolean = false
    ) : CombatEvent()

    /**
     * Entity was revived/respawned
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that was revived
     * @param reviverEntityId Entity that performed the revival (null for respawn)
     * @param newHp HP after revival
     */
    @Serializable
    data class EntityRevived(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val reviverEntityId: String? = null,
        val newHp: Int
    ) : CombatEvent()

    /**
     * Combat engagement started
     * Triggered when disposition drops below threshold or player attacks
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that initiated combat
     * @param targetEntityId Entity being engaged
     * @param roomId Room where combat started
     * @param wasPlayerInitiated True if player started the fight
     */
    @Serializable
    data class CombatStarted(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val targetEntityId: String,
        val roomId: String,
        val wasPlayerInitiated: Boolean = false
    ) : CombatEvent()

    /**
     * Combat engagement ended
     * Can end due to death, flee, or peaceful resolution
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId One of the entities involved
     * @param targetEntityId The other entity involved
     * @param roomId Room where combat ended
     * @param reason Reason combat ended
     */
    @Serializable
    data class CombatEnded(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val targetEntityId: String,
        val roomId: String,
        val reason: CombatEndReason
    ) : CombatEvent()

    /**
     * Entity fled from combat
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that fled
     * @param fromRoomId Room they fled from
     * @param toRoomId Room they fled to (null if flee failed)
     * @param wasSuccessful True if flee succeeded
     */
    @Serializable
    data class EntityFled(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val fromRoomId: String,
        val toRoomId: String? = null,
        val wasSuccessful: Boolean
    ) : CombatEvent()

    /**
     * Attack missed or was dodged
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that attacked
     * @param targetEntityId Entity that was targeted
     * @param wasDodged True if target dodged, false if attacker missed
     * @param skillsUsed List of skills involved in attack
     */
    @Serializable
    data class AttackMissed(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val targetEntityId: String,
        val wasDodged: Boolean = false,
        val skillsUsed: List<String> = emptyList()
    ) : CombatEvent()

    /**
     * Critical hit occurred
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that landed the critical
     * @param targetEntityId Entity that was critically hit
     * @param damageMultiplier Critical damage multiplier (e.g., 2.0 for double damage)
     */
    @Serializable
    data class CriticalHit(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val targetEntityId: String,
        val damageMultiplier: Double
    ) : CombatEvent()

    /**
     * Environmental damage was applied
     * E.g., lava, spikes, falling rocks
     *
     * @param gameTime Game time tick when event occurred
     * @param sourceEntityId Entity that took environmental damage
     * @param damageAmount Damage dealt
     * @param damageType Type of environmental damage
     * @param environmentSource Description of the source (e.g., "lava pool", "spike trap")
     * @param finalHp Entity's HP after damage
     */
    @Serializable
    data class EnvironmentalDamage(
        override val gameTime: Long,
        override val sourceEntityId: String,
        val damageAmount: Int,
        val damageType: DamageType,
        val environmentSource: String,
        val finalHp: Int
    ) : CombatEvent()
}

/**
 * Reasons why combat can end
 */
@Serializable
enum class CombatEndReason {
    /** One combatant died */
    DEATH,

    /** One combatant fled successfully */
    FLEE,

    /** Combat was resolved peacefully (successful intimidation/persuasion) */
    PEACEFUL_RESOLUTION,

    /** One combatant left the area (disconnected, teleported, etc.) */
    SEPARATED,

    /** Combat timed out or was interrupted by game system */
    INTERRUPTED
}
