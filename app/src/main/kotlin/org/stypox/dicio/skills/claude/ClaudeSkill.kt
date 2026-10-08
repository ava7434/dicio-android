package org.stypox.dicio.skills.claude

import kotlinx.coroutines.Dispatchers
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
import java.net.URL
import java.nio.charset.StandardCharsets

class ClaudeSkill(correspondingSkillInfo: SkillInfo) :
    RecognizeEverythingSkill(correspondingSkillInfo) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput {
        if (!ClaudeInfo.isEnabled(ctx.android)) {
            val askToRepeat = (ctx.previousOutput as? ClaudeOutput.NoMatch)
                ?.let { !it.askToRepeat } ?: true
            return ClaudeOutput.NoMatch(askToRepeat)
        }

        val apiKey = ClaudeInfo.getApiKey(ctx.android) ?: return ClaudeOutput.NoApiKey
        val rememberConversation = ClaudeInfo.isMemoryEnabled(ctx.android)

        return withContext(Dispatchers.IO) {
            try {
                val history = if (rememberConversation) ConversationMemory.get() else emptyList()
                val answer = askClaude(apiKey, history, inputData)
                if (rememberConversation) {
                    ConversationMemory.add(inputData, answer)
                }
                ClaudeOutput.Success(answer)
            } catch (e: IOException) {
                ClaudeOutput.Failed(e.message ?: "network error")
            } catch (e: Exception) {
                ClaudeOutput.Failed(e.message ?: "unexpected error")
            }
        }
    }

    @Throws(IOException::class)
    private fun askClaude(
        apiKey: String,
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
            put(
                "system",
                "You are a voice assistant fallback. Answer in at most two short " +
                        "sentences, in plain spoken language, no markdown, no lists."
            )
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
    }
