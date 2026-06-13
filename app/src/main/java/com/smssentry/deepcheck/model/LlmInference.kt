package com.smssentry.deepcheck.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

interface LlmInferenceEngine {
    suspend fun generateResponseAsync(
        messages: List<ChatMessage>,
        tools: List<Tool> = emptyList()
    ): LlmResponse?
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
