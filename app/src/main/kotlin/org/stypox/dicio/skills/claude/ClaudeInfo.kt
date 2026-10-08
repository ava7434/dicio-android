package org.stypox.dicio.skills.claude

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.settings.ui.BooleanSetting
import org.stypox.dicio.settings.ui.SettingsItem
import org.stypox.dicio.settings.ui.StringSetting

object ClaudeInfo : SkillInfo("claude") {
    private const val PREFS_NAME = "skill_settings_claude"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MEMORY_ENABLED = "memory_enabled"

    override fun name(context: Context) = "Claude"

    override fun sentenceExample(context: Context) =
        "Ask anything not understood by other skills"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.SmartToy)

    override fun build(ctx: SkillContext): Skill<*> {
        return ClaudeSkill(ClaudeInfo)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String? {
        val key = prefs(context).getString(KEY_API_KEY, null)
        return if (key.isNullOrBlank()) null else key
    }

    private fun setApiKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_API_KEY, value).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    private fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun isMemoryEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEMORY_ENABLED, true)

    private fun setMemoryEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MEMORY_ENABLED, value).apply()
    }

    override val renderSettings: @Composable () -> Unit = @Composable {
        val context = LocalContext.current
        var enabled by remember { mutableStateOf(isEnabled(context)) }
        var apiKey by remember { mutableStateOf(getApiKey(context) ?: "") }
        var memoryEnabled by remember { mutableStateOf(isMemoryEnabled(context)) }

        Column {
            BooleanSetting(
                title = "Chiedi a Claude quando non capisco",
                descriptionOn = "Attivo: le frasi non riconosciute vengono inviate a Claude",
                descriptionOff = "Disattivo: Dicio dirà solo \"non ho capito\" come prima",
            ).Render(
                value = enabled,
                onValueChange = {
                    enabled = it
                    setEnabled(context, it)
                },
            )

            StringSetting(
                title = "Anthropic API key",
                description = "Usata per rispondere quando nessun altro skill capisce",
                descriptionWhenEmpty = "Non impostata: il fallback resterà silenzioso",
            ).Render(
                value = apiKey,
                onValueChange = { newValue ->
                    apiKey = newValue
                    setApiKey(context, newValue)
                },
            )

            BooleanSetting(
                title = "Ricorda la conversazione",
                descriptionOn = "Claude vede le ultime domande/risposte per capire il contesto",
                descriptionOff = "Ogni domanda viene inviata senza contesto precedente",
            ).Render(
                value = memoryEnabled,
                onValueChange = {
                    memoryEnabled = it
                    setMemoryEnabled(context, it)
                },
            )

            SettingsItem(
                title = "Cancella memoria conversazione",
                description = "Dimentica le domande/risposte recenti",
                icon = Icons.Default.DeleteSweep,
                modifier = Modifier.clickable {
                    ConversationMemory.clear()
                    Toast.makeText(context, "Memoria cancellata", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}
