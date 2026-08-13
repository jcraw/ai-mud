@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Quest
import com.jcraw.mud.core.QuestObjective
import com.jcraw.mud.core.QuestReward
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Use-skill quest; fallback → explore (MUD-034n).
 */
internal object QuestSkillGen {

    /**
     * Generate a quest to use a skill on a feature
     */
    fun generate(worldState: WorldState, theme: DungeonTheme, random: Random): Quest {
        // V3: Get all features from global entity storage
        val features = worldState.entities.values
            .filterIsInstance<Entity.Feature>()
            .filter { it.skillChallenge != null && !it.isCompleted }

        val targetFeature = features.randomOrNull(random) ?: run {
            // Fallback if no skill challenges
            return QuestExploreGen.generate(worldState, theme, random)
        }

        return build(targetFeature, random)
    }

    private fun build(targetFeature: Entity.Feature, random: Random): Quest {
        val skillChallenge = targetFeature.skillChallenge!!
        val statLabel = skillChallenge.statType.name.lowercase()
        val title = "Test Your ${statLabel.replaceFirstChar { it.uppercase() }}"
        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "Overcome the challenge presented by the ${targetFeature.name}.",
            giver = null,
            objectives = listOf(useSkillObjective(targetFeature, statLabel)),
            reward = QuestReward(experiencePoints = 60, goldAmount = 30, description = "Experience and gold reward"),
            flavorText = "Only skill and courage will see you through."
        )
    }

    private fun useSkillObjective(targetFeature: Entity.Feature, statLabel: String) = QuestObjective.UseSkill(
        id = "obj_skill_${targetFeature.id}",
        description = "Successfully $statLabel check on ${targetFeature.name}",
        skillType = targetFeature.skillChallenge!!.statType,
        targetFeatureId = targetFeature.id,
        targetName = targetFeature.name
    )
}
