package com.smssentry.deepcheck.model

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
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
        // Pass the system prompt via ConversationConfig.systemInstruction,
        // NOT as a user message. Sending it as Message.user() corrupts the
        // conversation history and makes the model treat instructions as a
        // user turn, degrading output quality.
        val convConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            systemInstruction = Contents.of(systemPrompt)
        )
        val conv = eng.createConversation(convConfig)
        return LlmConversationSession(conv)
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: throw IllegalStateException("Model not loaded")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7, topP = 0.9)
        val conversationConfig = ConversationConfig(samplerConfig = samplerConfig)
        val conversation = currentEngine.createConversation(conversationConfig)

        try {
            val userMessage = Message.user(prompt)
            val responseMessage = conversation.sendMessage(userMessage)
            extractTextFromMessage(responseMessage)
        } finally {
            conversation.close()
        }
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        /**
         * Safely extract text content from a LiteRT-LM Message response.
         * Avoids the unsafe `as List<Any>` cast that could throw
         * ClassCastException at runtime.
         */
        fun extractTextFromMessage(message: Message): String {
            return try {
                val contents = message.contents.contents
                contents.filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            } catch (e: Exception) {
                // Last resort — return the whole message as string
                message.toString()
            }
        }
    }
}
