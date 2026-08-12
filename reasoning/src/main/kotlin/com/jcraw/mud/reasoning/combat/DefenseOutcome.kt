package com.jcraw.mud.reasoning.combat

/**
 * Defense outcome - how the defender's skills contributed to the result
 */
enum class DefenseOutcome {
    DODGED,      // Dodge skill was primary contributor
    PARRIED,     // Parry skill was primary contributor
    BLOCKED,     // Both contributed equally
    OVERWHELMED  // Defense attempted but attacker won
}
