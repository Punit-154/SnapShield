package com.smssentry.deepcheck.session

import com.smssentry.deepcheck.model.LlmInferenceEngine
import kotlinx.coroutines.delay
import java.io.IOException

class MockLlmEngine(private val script: List<String>) : LlmInferenceEngine {
    private var index = 0

    override suspend fun load() { /* no-op */ }

    override suspend fun generate(prompt: String): String {
        val result = script.getOrElse(index++) { script.last() }
        if (result == "__TIMEOUT__") {
            delay(35000) // simulated timeout
            return ""
        }
        if (result == "__ERROR__") {
            throw IOException("Simulated engine error")
        }
        return result
    }

    override fun close() { /* no-op */ }
}
