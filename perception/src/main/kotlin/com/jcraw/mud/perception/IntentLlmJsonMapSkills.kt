@file:Suppress(
    "ReturnCount",
    "MaxLineLength",
)

package com.jcraw.mud.perception

/**
 * LLM JSON map: skills / quests / check.
 * Pure extract (MUD-034c) — keys and outcomes unchanged.
 */
internal object IntentLlmJsonMapSkills {

    fun mapSkills(
        intentType: String,
        target: String?,
        npcTarget: String?,
        skillName: String?,
        perkChoice: Int?
    ): Intent? = mapQuests(intentType, target)
        ?: mapSkillOps(intentType, target, npcTarget, skillName, perkChoice)

    private fun mapQuests(intentType: String, target: String?): Intent? =
        when (intentType) {
            "check" -> if (target != null) Intent.Check(target) else Intent.Invalid("Intent.Check what?")
            "quests" -> Intent.Quests
            "accept_quest" -> Intent.AcceptQuest(target)
            "abandon_quest" -> if (target != null) Intent.AbandonQuest(target) else Intent.Invalid("Abandon which quest?")
            "claim_reward" -> if (target != null) Intent.ClaimReward(target) else Intent.Invalid("Claim reward for which quest?")
            else -> null
        }

    private fun mapSkillOps(
        intentType: String,
        target: String?,
        npcTarget: String?,
        skillName: String?,
        perkChoice: Int?
    ): Intent? = when (intentType) {
        "use_skill" -> if (target != null) Intent.UseSkill(skillName, target) else Intent.Invalid("Intent.Use what skill for what action?")
        "train_skill" -> mapTrain(target, npcTarget)
        "choose_perk" -> mapPerk(target, perkChoice)
        "view_skills" -> Intent.ViewSkills
        else -> null
    }

    private fun mapTrain(target: String?, npcTarget: String?): Intent =
        if (target != null && npcTarget != null) {
            Intent.TrainSkill(target, npcTarget)
        } else if (target == null) {
            Intent.Invalid("Train which skill?")
        } else {
            Intent.Invalid("Train with whom or how?")
        }

    private fun mapPerk(target: String?, perkChoice: Int?): Intent =
        if (target != null && perkChoice != null) {
            Intent.ChoosePerk(target, perkChoice)
        } else if (target == null) {
            Intent.Invalid("Choose perk for which skill?")
        } else {
            Intent.Invalid("Choose which perk (1 or 2)?")
        }
}
