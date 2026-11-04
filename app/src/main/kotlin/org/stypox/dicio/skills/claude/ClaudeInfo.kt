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
import org.stypox.dicio.settings.ui.ListSetting
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
    private const val KEY_API_KEY = "api_key" // Anthropic key, name kept for compatibility
    private const val KEY_PROVIDER = "provider"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"
    // alias that Google keeps pointing to its current Flash model, which has a free tier
    const val DEFAULT_GEMINI_MODEL = "gemini-flash-latest"
    private const val KEY_MEMORY_ENABLED = "memory_enabled"
    private const val KEY_JARVIS_ENABLED = "jarvis_enabled"
    private const val KEY_USER_TITLE = "user_title"
    private const val DEFAULT_USER_TITLE = "signore"

    /** The AI service that answers the questions not handled by the other skills. */
    enum class Provider(val id: String, val displayName: String) {
        CLAUDE("claude", "Claude"),
        GEMINI("gemini", "Gemini"),
    }

    override fun name(context: Context) = "Assistente AI"

    override fun sentenceExample(context: Context) =
        "Fai qualsiasi domanda che le altre abilità non capiscono"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.SmartToy)

    override fun build(ctx: SkillContext): Skill<*> {
        return ClaudeSkill(ClaudeInfo)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProvider(context: Context): Provider {
        val id = prefs(context).getString(KEY_PROVIDER, null)
        return Provider.entries.firstOrNull { it.id == id } ?: Provider.CLAUDE
    }

    private fun setProvider(context: Context, value: Provider) {
        prefs(context).edit().putString(KEY_PROVIDER, value.id).apply()
    }

    private fun apiKeyPref(provider: Provider) = when (provider) {
        Provider.CLAUDE -> KEY_API_KEY
        Provider.GEMINI -> KEY_GEMINI_API_KEY
    }

    /** The API key of [provider], or null if it was not set. */
    fun getApiKey(context: Context, provider: Provider): String? {
        val key = prefs(context).getString(apiKeyPref(provider), null)
        return if (key.isNullOrBlank()) null else key.trim()
    }

    private fun setApiKey(context: Context, provider: Provider, value: String) {
        prefs(context).edit().putString(apiKeyPref(provider), value).apply()
    }

    fun getGeminiModel(context: Context): String {
        val model = prefs(context).getString(KEY_GEMINI_MODEL, null)
        return if (model.isNullOrBlank()) DEFAULT_GEMINI_MODEL else model.trim()
    }

    private fun setGeminiModel(context: Context, value: String) {
        prefs(context).edit().putString(KEY_GEMINI_MODEL, value).apply()
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
        var provider by remember { mutableStateOf(getProvider(context)) }
        var claudeKey by remember { mutableStateOf(getApiKey(context, Provider.CLAUDE) ?: "") }
        var geminiKey by remember { mutableStateOf(getApiKey(context, Provider.GEMINI) ?: "") }
        var geminiModel by remember {
            mutableStateOf(prefs(context).getString(KEY_GEMINI_MODEL, null) ?: "")
        }
        var memoryEnabled by remember { mutableStateOf(isMemoryEnabled(context)) }
        var jarvisEnabled by remember { mutableStateOf(isJarvisEnabled(context)) }
        var userTitle by remember {
            mutableStateOf(prefs(context).getString(KEY_USER_TITLE, null) ?: "")
        }

        Column {
            ListSetting(
                title = "Servizio AI",
                description = "Chi risponde alle domande libere",
                possibleValues = listOf(
                    ListSetting.Value(
                        value = Provider.GEMINI,
                        name = "Gemini (Google)",
                        description = "Ha un piano gratuito, chiave su aistudio.google.com",
                    ),
                    ListSetting.Value(
                        value = Provider.CLAUDE,
                        name = "Claude (Anthropic)",
                        description = "A pagamento, chiave su console.anthropic.com",
                    ),
                ),
            ).Render(
                value = provider,
                onValueChange = {
                    provider = it
                    setProvider(context, it)
                },
            )

            when (provider) {
                Provider.GEMINI -> {
                    StringSetting(
                        title = "Gemini API key",
                        description = "Chiave gratuita da aistudio.google.com",
                        descriptionWhenEmpty = "Non impostata: crea una chiave su aistudio.google.com",
                    ).Render(
                        value = geminiKey,
                        onValueChange = { newValue ->
                            geminiKey = newValue
                            setApiKey(context, Provider.GEMINI, newValue)
                        },
                    )
                    StringSetting(
                        title = "Modello Gemini",
                        description = "Lascia vuoto per usare il predefinito",
                        descriptionWhenEmpty = "Predefinito: $DEFAULT_GEMINI_MODEL",
                    ).Render(
                        value = geminiModel,
                        onValueChange = { newValue ->
                            geminiModel = newValue
                            setGeminiModel(context, newValue)
                        },
                    )
                }
                Provider.CLAUDE -> {
                    StringSetting(
                        title = "Anthropic API key",
                        description = "Usata per rispondere quando nessun altro skill capisce",
                        descriptionWhenEmpty = "Non impostata: il fallback resterà silenzioso",
                    ).Render(
                        value = claudeKey,
                        onValueChange = { newValue ->
                            claudeKey = newValue
                            setApiKey(context, Provider.CLAUDE, newValue)
                        },
                    )
                }
            }

            BooleanSetting(
                title = "Ricorda la conversazione",
                descriptionOn = "L'assistente vede le ultime domande/risposte per capire il contesto",
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
