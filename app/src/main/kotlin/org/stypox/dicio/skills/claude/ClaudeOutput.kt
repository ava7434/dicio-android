package org.stypox.dicio.skills.claude

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString

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

    data class NoMatch(val askToRepeat: Boolean) : ClaudeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
            if (askToRepeat) R.string.eval_no_match_repeat
            else R.string.eval_no_match
        )

        override fun getInteractionPlan(ctx: SkillContext) =
            InteractionPlan.Continue(reopenMicrophone = askToRepeat)
    }
}
