package com.smssentry.deepcheck.model

import com.google.ai.edge.litertlm.*
import com.smssentry.deepcheck.DeepCheckConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtLmEngine(private val modelPath: String) : LlmInferenceEngine {

    private var engine: Engine? = null

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (engine == null) {
            val modelFile = File(modelPath)
            if (!modelFile.exists() || modelFile.length() < DeepCheckConfig.MIN_FILE_SIZE_BYTES) {
                throw IllegalStateException("Model file incomplete or missing")
            }
            try {
                val engineConfig = EngineConfig(modelPath = modelPath)
                val eng = Engine(engineConfig)
                eng.initialize()
                engine = eng
            } catch (e: UnsatisfiedLinkError) {
                throw IllegalStateException("LiteRT-LM native library not available")
            }
        }
    }

    override fun createSession(systemPrompt: String): LlmConversationSession {
        val eng = engine ?: throw IllegalStateException("Model not loaded")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7, topP = 0.9)
        val convConfig = ConversationConfig(samplerConfig = samplerConfig)
        val conv = eng.createConversation(convConfig)
        conv.sendMessage(Message.of(systemPrompt))
        return LlmConversationSession(conv)
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: throw IllegalStateException("Model not loaded")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7, topP = 0.9)
        val conversationConfig = ConversationConfig(samplerConfig = samplerConfig)
        val conversation = currentEngine.createConversation(conversationConfig)

        try {
            val userMessage = Message.of(prompt)
            val responseMessage = conversation.sendMessage(userMessage)
            responseMessage.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        } finally {
            conversation.close()
        }
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
