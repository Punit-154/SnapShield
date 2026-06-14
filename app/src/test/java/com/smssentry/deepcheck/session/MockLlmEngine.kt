package com.smssentry.deepcheck.session

import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.model.LlmConversationSession
import kotlinx.coroutines.delay
import java.io.IOException

class MockLlmEngine(private val script: List<String> = emptyList()) : LlmInferenceEngine {
    private var index = 0

    override suspend fun load() { /* no-op */ }

    override suspend fun generate(prompt: String): String = nextResponse()

    override fun createSession(systemPrompt: String): LlmConversationSession =
        MockConversationSession(this)

    suspend fun nextResponse(): String {
        val response = script.getOrNull(index++) ?: "<<<VERDICT:SAFE,0.5,safe>>>\nNo issues found."
        return when (response) {
            "__TIMEOUT__" -> {
                delay(60_000)
                ""
            }
            "__ERROR__"   -> throw IOException("Simulated engine error")
            else          -> response
        }
    }

    override fun close() { /* no-op */ }
}

class MockConversationSession(private val mockEngine: MockLlmEngine) : LlmConversationSession(null) {
    override suspend fun sendTurn(userText: String): String {
        return mockEngine.nextResponse()
    }
    override fun close() {}
}
