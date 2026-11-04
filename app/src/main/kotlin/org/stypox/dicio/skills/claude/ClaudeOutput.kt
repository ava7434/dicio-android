package org.stypox.dicio.skills.claude

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput

sealed interface ClaudeOutput : HeadlineSpeechSkillOutput {

    data class Success(val answer: String) : ClaudeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = answer
    }

    data class NoApiKey(val providerName: String) : ClaudeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            "Manca la chiave API di $providerName. Aggiungila nelle impostazioni " +
                    "dell'abilità Assistente AI."
    }

    data class Failed(val providerName: String, val reason: String) : ClaudeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            "Non sono riuscito a contattare $providerName: $reason"
    }
}
