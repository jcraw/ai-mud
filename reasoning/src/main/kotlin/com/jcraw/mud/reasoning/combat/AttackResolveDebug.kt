package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillComponent

/** Debug println helpers for attack resolve (MUD-034k). */
internal object AttackResolveDebug {

    fun logComponents(
        attacker: Entity,
        defender: Entity,
        attackerSkills: SkillComponent?,
        defenderSkills: SkillComponent?,
        defenderCombat: CombatComponent?
    ) {
        logAttacker(attacker, attackerSkills)
        logDefender(defender, defenderSkills, defenderCombat)
    }

    private fun logAttacker(attacker: Entity, attackerSkills: SkillComponent?) {
        println("[COMBAT DEBUG] Attacker: ${attacker.name} (${attacker.javaClass.simpleName})")
        println("[COMBAT DEBUG]   - SkillComponent: ${if (attackerSkills != null) "FOUND" else "MISSING"}")
        if (attacker is Entity.NPC) {
            println("[COMBAT DEBUG]   - NPC components: ${attacker.components.keys}")
        }
    }

    private fun logDefender(
        defender: Entity,
        defenderSkills: SkillComponent?,
        defenderCombat: CombatComponent?
    ) {
        println("[COMBAT DEBUG] Defender: ${defender.name} (${defender.javaClass.simpleName})")
        println("[COMBAT DEBUG]   - CombatComponent: ${if (defenderCombat != null) "FOUND" else "MISSING"}")
        println("[COMBAT DEBUG]   - SkillComponent: ${if (defenderSkills != null) "FOUND" else "MISSING"}")
        if (defender is Entity.NPC) {
            println("[COMBAT DEBUG]   - NPC components: ${defender.components.keys}")
        }
    }
}
