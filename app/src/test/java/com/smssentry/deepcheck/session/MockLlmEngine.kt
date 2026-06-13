package com.smssentry.deepcheck.session

import com.smssentry.deepcheck.model.ChatMessage
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.model.LlmResponse
import com.smssentry.deepcheck.model.Tool

class MockLlmEngine(private val script: List<LlmResponse>) : LlmInferenceEngine {
    private var index = 0

    override suspend fun generateResponseAsync(
        messages: List<ChatMessage>,
        tools: List<Tool>
    ): LlmResponse {
        return script.getOrElse(index++) { script.last() }
    }
}
