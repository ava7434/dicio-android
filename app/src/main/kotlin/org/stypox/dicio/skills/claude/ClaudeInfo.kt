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

/**
 * Catch-all skill with LOW specificity: it is only ever picked when no other skill (weather,
 * calculator, etc.) matched the input with a high enough score, because of how SkillRanker
 * handles specificity tiers. This means it naturally behaves as a fallback, and the user can
 * enable/disable it from the normal skill list in Settings > Abilit\u00e0, just like any other skill.
 */
object ClaudeInfo : SkillInfo("claude") {
    private const val PREFS_NAME = "skill_settings_claude"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MEMORY_ENABLED = "memory_enabled"
    private const val KEY_JARVIS_ENABLED = "jarvis_enabled"
    private const val KEY_USER_TITLE = "user_title"
    private const val DEFAULT_USER_TITLE = "signore"

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

    /** Whether Claude is given the last few exchanges as context. Defaults to true. */
    fun isMemoryEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEMORY_ENABLED, true)

    private fun setMemoryEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MEMORY_ENABLED, value).apply()
    }

    /** Whether Claude answers with the "Jarvis" butler-like personality. Defaults to true. */
    fun isJarvisEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_JARVIS_ENABLED, true)

    private fun setJarvisEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_JARVIS_ENABLED, value).apply()
    }

    /** How the Jarvis personality addresses the user, e.g. "signore". */
    fun getUserTitle(context: Context): String {
        val title = prefs(context).getString(KEY_USER_TITLE, null)
        return if (title.isNullOrBlank()) DEFAULT_USER_TITLE else title.trim()
    }

    private fun setUserTitle(context: Context, value: String) {
        prefs(context).edit().putString(KEY_USER_TITLE, value).apply()
    }

    override val renderSettings: @Composable () -> Unit = @Composable {
        val context = LocalContext.current
        var apiKey by remember { mutableStateOf(getApiKey(context) ?: "") }
        var memoryEnabled by remember { mutableStateOf(isMemoryEnabled(context)) }
        var jarvisEnabled by remember { mutableStateOf(isJarvisEnabled(context)) }
        var userTitle by remember {
            mutableStateOf(prefs(context).getString(KEY_USER_TITLE, null) ?: "")
        }

        Column {
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

            BooleanSetting(
                title = "Personalità Jarvis",
                descriptionOn = "Risponde come un maggiordomo digitale: cortese, calmo, un po' ironico",
                descriptionOff = "Risposte neutre e sintetiche",
            ).Render(
                value = jarvisEnabled,
                onValueChange = {
                    jarvisEnabled = it
                    setJarvisEnabled(context, it)
                },
            )

            if (jarvisEnabled) {
                StringSetting(
                    title = "Come vuoi essere chiamato",
                    description = "Jarvis si rivolgerà a te così",
                    descriptionWhenEmpty = "Predefinito: \"$DEFAULT_USER_TITLE\"",
                ).Render(
                    value = userTitle,
                    onValueChange = { newValue ->
                        userTitle = newValue
                        setUserTitle(context, newValue)
                    },
                )
            }

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
