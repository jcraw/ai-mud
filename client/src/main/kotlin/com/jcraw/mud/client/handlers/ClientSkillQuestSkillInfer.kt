package com.jcraw.mud.client.handlers

import com.jcraw.mud.reasoning.skill.SkillActionInfer

/** Thin GUI wrapper around [SkillActionInfer] (MUD-039). */
object ClientSkillQuestSkillInfer {
    fun inferSkillFromAction(action: String): String? = SkillActionInfer.infer(action)
}
