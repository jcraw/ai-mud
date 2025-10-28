package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Equipment slots for equippable items
 * Some items occupy specific slots, while two-handed items occupy both hand slots
 */
@Serializable
enum class EquipSlot {
    /** Main hand slot for one-handed weapons or shields */
    HANDS_MAIN,

    /** Off-hand slot for secondary weapons or shields */
    HANDS_OFF,

    /** Head slot for helmets and headgear */
    HEAD,

    /** Chest slot for armor and robes */
    CHEST,

    /** Legs slot for pants and leg armor */
    LEGS,

    /** Feet slot for boots and shoes */
    FEET,

    /** Back slot for cloaks and backpacks */
    BACK,

    /** Both hands slot for two-handed weapons (clears HANDS_OFF when equipped) */
    HANDS_BOTH,

    /** First accessory slot for rings, amulets */
    ACCESSORY_1,

    /** Second accessory slot for rings, amulets */
    ACCESSORY_2,

    /** Third accessory slot for rings, amulets */
    ACCESSORY_3,

    /** Fourth accessory slot for rings, amulets */
    ACCESSORY_4
}
