package com.smssentry.deepcheck.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Content

sealed class LlmResponse {
    data class Text(val text: String) : LlmResponse()
    data class ToolCall(val name: String, val arguments: String) : LlmResponse()
    data class Error(val error: String) : LlmResponse()
}

data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCall: LlmResponse.ToolCall? = null
)

data class Tool(
    val name: String,
    val description: String,
    val parameters: String
)

open class LlmConversationSession(
    private val conversation: com.google.ai.edge.litertlm.Conversation? = null
) {
    open suspend fun sendTurn(userText: String): String = withContext(Dispatchers.IO) {
        val conv = conversation ?: throw IllegalStateException("No active conversation")
        val msg = Message.of(userText)
        val response = conv.sendMessage(msg)
        val parts: List<Any> = response.contents.contents as List<Any>
        parts.filterIsInstance<Content.Text>().joinToString("") { it.text }
    }
    open fun close() {
        conversation?.close()
    }
}

interface LlmInferenceEngine {
    suspend fun load()
    suspend fun generate(prompt: String): String
    fun createSession(systemPrompt: String): LlmConversationSession
    fun close()
}

@Serializable
data class VerdictJson(
    val verdict: String,
    val confidence: Float,
    val reasoning: String,
    val evidence: List<String>
)

object VerdictParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractJson(text: String): String? {
        var depth = 0
        var startIndex = -1
        var inString = false
        var escape = false

        for (i in text.indices) {
            val c = text[i]
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> {
                    if (depth == 0) startIndex = i
                    depth++
                }
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0 && startIndex >= 0) {
                        return text.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    fun parseVerdict(jsonStr: String): VerdictJson? {
        return try {
            val verdict = json.decodeFromString<VerdictJson>(jsonStr)
            if (verdict.verdict in listOf("SAFE", "SCAM", "SUSPICIOUS") &&
                verdict.confidence in 0.0f..1.0f
            ) {
                verdict
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class ParsedEducationalVerdict(
    val verdictLabel: String,
    val confidence: Float,
    val scamType: String,
    val explanation: String
)

object EducationalVerdictParser {
    private val TAG_PATTERN = Regex("""<<<VERDICT\s*:\s*(\w+)\s*,\s*([\d.]+)\s*,\s*([^>]+?)\s*>>>""", RegexOption.IGNORE_CASE)

    fun parse(rawOutput: String): ParsedEducationalVerdict? {
        val match = TAG_PATTERN.find(rawOutput) ?: return null
        val (verdictStr, confidenceStr, scamType) = match.destructured
        var explanation = rawOutput.substringAfter(match.value).trim()
        if (explanation.isBlank()) {
            explanation = rawOutput.substringBefore(match.value).trim()
        }
        if (explanation.isBlank()) return null
        return ParsedEducationalVerdict(
            verdictLabel = when (verdictStr.uppercase()) { "SCAM" -> "SCAM"; "SAFE" -> "SAFE"; else -> "SUSPICIOUS" },
            confidence = confidenceStr.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f,
            scamType = scamType.trim(),
            explanation = explanation
        )
    }
}
