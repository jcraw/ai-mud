package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Boss designation metadata for NPC entities
 * Lightweight flag structure vs full component
 * Attached to Entity.NPC as metadata (not in component map)
 */
@Serializable
data class BossDesignation(
    val isBoss: Boolean = false,            // Whether this NPC is a boss
    val bossTitle: String = "",             // Boss title (e.g., "Abyssal Lord", "Dragon King")
    val victoryFlag: String = ""            // Quest flag set on boss death (e.g., "abyssal_lord_defeated")
) {
    /**
     * Check if this is a valid boss (has required fields)
     */
    fun isValid(): Boolean {
        return isBoss && bossTitle.isNotBlank() && victoryFlag.isNotBlank()
    }

    /**
     * Get display name for boss
     */
    fun displayName(): String {
        return if (isBoss && bossTitle.isNotBlank()) {
            bossTitle
        } else {
            ""
        }
    }

    companion object {
        /**
         * Create a new boss designation
         */
        fun create(title: String, victoryFlag: String): BossDesignation {
            return BossDesignation(
                isBoss = true,
                bossTitle = title,
                victoryFlag = victoryFlag
            )
        }

        /**
         * Non-boss designation (default)
         */
        val NONE = BossDesignation(isBoss = false)
    }
}
