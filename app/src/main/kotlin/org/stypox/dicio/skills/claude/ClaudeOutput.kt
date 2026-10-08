package org.stypox.dicio.skills.claude

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput

sealed interface ClaudeOutput : HeadlineSpeechSkillOutput {

    data class Success(val answer: String) : ClaudeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = answer
    }

    data object NoApiKey : ClaudeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            "No Anthropic API key is set. Add one in the Claude skill settings."
    }

    data class Failed(val reason: String) : ClaudeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            "I could not reach Claude: $reason"
    }
}
