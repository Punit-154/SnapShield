package com.smssentry.deepcheck.model

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.smssentry.deepcheck.DeepCheckConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * LiteRT-LM engine wrapper modeled after the official Google AI Edge Gallery
 * implementation (LlmChatModelHelper.kt).
 *
 * Key differences from the previous implementation:
 * 1. GPU backend with automatic CPU fallback (Gallery pattern)
 * 2. cacheDir for faster subsequent loads
 * 3. Thread-safe initialization via Mutex (prevents double-init race condition)
 * 4. Non-blocking timeout via CompletableDeferred (JNI calls can't be
 *    interrupted by coroutine cooperative cancellation)
 * 5. System prompt via ConversationConfig.systemInstruction (not Message.user)
 */
class LiteRtLmEngine(
    private val modelPath: String,
    private val cacheDir: String? = null
) : LlmInferenceEngine {

    private val TAG = "LiteRtLmEngine"

    private var engine: Engine? = null
    private val initMutex = Mutex()

    override suspend fun load() {
        // Mutex ensures only one initialization runs at a time.
        // If init{} in ModelRepository started a load and the session
        // calls load() again, the second caller waits for the first
        // to finish, then sees engine != null and returns immediately.
        initMutex.withLock {
            if (engine != null) return  // Already initialized

            val modelFile = File(modelPath)
            if (!modelFile.exists() || modelFile.length() < DeepCheckConfig.MIN_FILE_SIZE_BYTES) {
                throw IllegalStateException("Model file incomplete or missing")
            }

            // Try GPU first (matches Gallery behavior), fall back to CPU.
            // GPU initialization is dramatically faster on supported devices.
            engine = try {
                Log.i(TAG, "Attempting GPU backend initialization...")
                initializeWithBackend(Backend.GPU())
            } catch (e: Exception) {
                Log.w(TAG, "GPU backend failed (${e.message}), falling back to CPU")
                try {
                    initializeWithBackend(Backend.CPU())
                } catch (cpuError: Exception) {
                    Log.e(TAG, "CPU backend also failed", cpuError)
                    throw IllegalStateException(
                        "Failed to initialize model on both GPU and CPU: ${cpuError.message}",
                        cpuError
                    )
                }
            }
            Log.i(TAG, "Engine initialized successfully")
        }
    }

    /**
     * Initialize the engine on a dedicated thread via CompletableDeferred.
     *
     * Engine.initialize() is a blocking JNI call that cannot be interrupted
     * by coroutine cooperative cancellation. By running it on a plain Thread
     * and awaiting a CompletableDeferred, withTimeout() can cancel at the
     * await() suspension point even if the JNI call hasn't returned yet.
     */
    private suspend fun initializeWithBackend(backend: Backend): Engine {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir ?: ""
        )
        val eng = Engine(config)

        // Run the blocking JNI init on a separate thread so that
        // coroutine timeout can actually cancel if needed.
        val deferred = CompletableDeferred<Unit>()
        val initThread = Thread({
            try {
                eng.initialize()
                deferred.complete(Unit)
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }, "LiteRtLmEngine-init")

        initThread.start()

        try {
            // 120-second timeout for engine initialization.
            // Galaxy A14 takes ~30s with GPU, up to ~90s on CPU.
            withTimeout(120_000L) {
                deferred.await()
            }
        } catch (e: Exception) {
            // If timeout or cancellation, try to close the engine
            try { eng.close() } catch (_: Exception) {}
            throw e
        }

        return eng
    }

    override fun createSession(systemPrompt: String): LlmConversationSession {
        val eng = engine ?: throw IllegalStateException("Model not loaded — call load() first")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7, topP = 0.9)
        // System prompt passed via ConversationConfig.systemInstruction
        // (not as Message.user), matching the Gallery pattern.
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
         */
        fun extractTextFromMessage(message: Message): String {
            return try {
                val contents = message.contents.contents
                contents.filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            } catch (e: Exception) {
                message.toString()
            }
        }
    }
}
