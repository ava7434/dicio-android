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
        history: List<Conv
