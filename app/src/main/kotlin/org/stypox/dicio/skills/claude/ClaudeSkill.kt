package org.stypox.dicio.skills.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.json.JSONArray
import org.json.JSONObject
import org.stypox.dicio.util.RecognizeEverythingSkill
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ClaudeSkill(correspondingSkillInfo: SkillInfo) :
    RecognizeEverythingSkill(correspondingSkillInfo) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput {
        val provider = ClaudeInfo.getProvider(ctx.android)
        val apiKey = ClaudeInfo.getApiKey(ctx.android, provider)
            ?: return ClaudeOutput.NoApiKey(provider.displayName)
        val rememberConversation = ClaudeInfo.isMemoryEnabled(ctx.android)
        val systemPrompt = if (ClaudeInfo.isJarvisEnabled(ctx.android)) {
            jarvisSystemPrompt(ClaudeInfo.getUserTitle(ctx.android))
        } else {
            NEUTRAL_SYSTEM_PROMPT
        }

        return withContext(Dispatchers.IO) {
            try {
                val history = if (rememberConversation) ConversationMemory.get() else emptyList()
                val answer = when (provider) {
                    ClaudeInfo.Provider.CLAUDE ->
                        askClaude(apiKey, systemPrompt, history, inputData)
                    ClaudeInfo.Provider.GEMINI -> askGeminiWithRetry(
                        apiKey, ClaudeInfo.getGeminiModel(ctx.android),
                        systemPrompt, history, inputData,
                    )
                }
                if (rememberConversation) {
                    ConversationMemory.add(inputData, answer)
                }
                ClaudeOutput.Success(answer)
            } catch (e: IOException) {
                ClaudeOutput.Failed(provider.displayName, e.message ?: "errore di rete")
            } catch (e: Exception) {
                ClaudeOutput.Failed(provider.displayName, e.message ?: "errore imprevisto")
            }
        }
    }

    @Throws(IOException::class)
    private fun askClaude(
        apiKey: String,
        systemPrompt: String,
        history: List<ConversationMemory.Turn>,
        question: String,
    ): String {
        val url = URL("https://api.anthropic.com/v1/messages")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-api-key", apiKey)
        connection.setRequestProperty("anthropic-version", "2023-06-01")
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        val messages = JSONArray()
        for (turn in history) {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", turn.question)
            })
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", turn.answer)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", question)
        })

        val requestBody = JSONObject().apply {
            put("model", "claude-haiku-5-5")
            put("max_tokens", 300)
            put("system", systemPrompt)
            put("messages", messages)
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(requestBody.toString())
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val responseText = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
            .use { it.readText() }

        if (responseCode !in 200..299) {
            val errorMessage = try {
                JSONObject(responseText).getJSONObject("error").getString("message")
            } catch (_: Exception) {
                "HTTP $responseCode"
            }
            throw IOException(errorMessage)
        }

        val responseJson = JSONObject(responseText)
        val contentArray = responseJson.getJSONArray("content")
        val textBuilder = StringBuilder()
        for (i in 0 until contentArray.length()) {
            val block = contentArray.getJSONObject(i)
            if (block.optString("type") == "text") {
                textBuilder.append(block.getString("text"))
            }
        }

        val answer = textBuilder.toString().trim()
        if (answer.isEmpty()) {
            throw IOException("empty response")
        }
        return answer
    }

    /**
     * Free-tier Gemini models are often temporarily overloaded (HTTP 503), hit transient server
     * errors, or answer too slowly. On an overload retry once after a short pause; on a timeout
     * or a repeated failure switch to the other Flash model, so that the user rarely has to ask
     * again and never waits too long.
     */
    @Throws(IOException::class)
    private suspend fun askGeminiWithRetry(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ConversationMemory.Turn>,
        question: String,
    ): String {
        val otherModel = if (model == ClaudeInfo.DEFAULT_GEMINI_MODEL) {
            ClaudeInfo.FALLBACK_GEMINI_MODEL
        } else {
            ClaudeInfo.DEFAULT_GEMINI_MODEL
        }
        var lastError: IOException? = null
        for (currentModel in listOf(model, otherModel)) {
            for (attempt in 0 until 2) {
                try {
                    return askGemini(apiKey, currentModel, systemPrompt, history, question)
                } catch (e: GeminiHttpException) {
                    if (!e.isTemporary) throw e
                    lastError = e
                    if (attempt == 0) delay(1_500)
                } catch (e: SocketTimeoutException) {
                    // a slow model will likely be slow again: move on to the other one
                    lastError = e
                    break
                }
            }
        }
        throw IOException(
            "i server di Google sono sovraccarichi in questo momento, riprova tra poco",
            lastError,
        )
    }

    /** An error answer from the Gemini API, with its HTTP status code. */
    private class GeminiHttpException(val code: Int, message: String) : IOException(message) {
        // 503 = model overloaded, 500 = internal error; both usually go away quickly
        val isTemporary get() = code == 503 || code == 500
    }

    /**
     * Uses the Gemini API (generateContent), which has a free tier. The key is sent in the
     * x-goog-api-key header rather than in the URL, so it does not end up in logs.
     */
    @Throws(IOException::class)
    private fun askGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<ConversationMemory.Turn>,
        question: String,
    ): String {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    URLEncoder.encode(model, "UTF-8") + ":generateContent"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000

        fun content(role: String, text: String) = JSONObject().apply {
            put("role", role)
            put("parts", JSONArray().put(JSONObject().put("text", text)))
        }

        val contents = JSONArray()
        for (turn in history) {
            contents.put(content("user", turn.question))
            contents.put(content("model", turn.answer))
        }
        contents.put(content("user", question))

        val requestBody = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            put("contents", contents)
            // generous limit: some Gemini models spend part of it on internal reasoning,
            // while the system prompt already keeps the spoken answer short
            put("generationConfig", JSONObject().put("maxOutputTokens", 1024))
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(requestBody.toString())
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val responseText = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
            .use { it.readText() }

        if (responseCode !in 200..299) {
            val errorMessage = try {
                JSONObject(responseText).getJSONObject("error").getString("message")
            } catch (_: Exception) {
                "HTTP $responseCode"
            }
            throw GeminiHttpException(responseCode, errorMessage)
        }

        val candidates = JSONObject(responseText).optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val textBuilder = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                // skip reasoning summaries, if a model returns them
                if (!part.optBoolean("thought", false)) {
                    textBuilder.append(part.optString("text"))
                }
            }
        }

        val answer = textBuilder.toString().trim()
        if (answer.isEmpty()) {
            throw IOException("risposta vuota")
        }
        return answer
    }

    companion object {
        private const val VOICE_RULES =
            "Your answers are read aloud by a text-to-speech engine: answer in at most two " +
                    "short sentences, in plain spoken language, with no markdown, no lists, " +
                    "no emoji and no symbols that cannot be pronounced. Always reply in the " +
                    "same language the user speaks."

        private const val NEUTRAL_SYSTEM_PROMPT =
            "You are a voice assistant fallback. $VOICE_RULES"

        private fun jarvisSystemPrompt(userTitle: String) =
            "You are Jarvis, the personal AI assistant living in the user's smartphone. " +
                    "Your personality is that of a refined, loyal digital butler: calm, " +
                    "precise, unfailingly polite and quietly confident, with a dry, subtle " +
                    "sense of humour used sparingly. Address the user as \"$userTitle\" now " +
                    "and then, but not in every sentence. Get straight to the point, never " +
                    "grovel, and if something is impossible or unknown, say so elegantly. " +
                    VOICE_RULES
    }
}
