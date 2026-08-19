package com.jcraw.app.handlers

import com.jcraw.mud.reasoning.skill.SkillActionInfer

/** Thin console wrapper around [SkillActionInfer] (MUD-039). */
object SkillQuestSkillInfer {
    fun inferSkillFromAction(action: String): String? = SkillActionInfer.infer(action)
}
