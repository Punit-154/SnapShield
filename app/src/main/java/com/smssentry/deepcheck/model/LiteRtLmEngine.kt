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
import com.smssentry.deepcheck.util.Diagnostics
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

    companion object {
        /** Cache GPU support result across instances to avoid retrying on devices where it always fails. */
        @Volatile private var gpuKnownToFail = false

        /**
         * Safely extract text content from a LiteRT-LM Message response.
         */
        fun extractTextFromMessage(message: Message): String {
            return try {
                val contents = message.contents.contents
                val text = contents.filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                Diagnostics.d(Diagnostics.ENGINE, "extractTextFromMessage: content length=${text.length}")
                text
            } catch (e: Exception) {
                Diagnostics.w(Diagnostics.ENGINE, "extractTextFromMessage: fallback to toString(), error=${e.message}")
                message.toString()
            }
        }
    }

    override suspend fun load() {
        Diagnostics.i(Diagnostics.ENGINE, "load() called, modelPath=$modelPath")
        // Mutex ensures only one initialization runs at a time.
        // If init{} in ModelRepository started a load and the session
        // calls load() again, the second caller waits for the first
        // to finish, then sees engine != null and returns immediately.
        initMutex.withLock {
            if (engine != null) {
                Diagnostics.d(Diagnostics.ENGINE, "load() skipped — engine already initialized")
                return  // Already initialized
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists() || modelFile.length() < DeepCheckConfig.MIN_FILE_SIZE_BYTES) {
                Diagnostics.e(Diagnostics.ENGINE, "Model file invalid: exists=${modelFile.exists()}, size=${if (modelFile.exists()) modelFile.length() else 0}")
                throw IllegalStateException("Model file incomplete or missing")
            }
            Diagnostics.d(Diagnostics.ENGINE, "Model file validated: size=${modelFile.length()} bytes")

            // Try GPU first (matches Gallery behavior), fall back to CPU.
            // Skip GPU if it previously failed on this device (cached across instances).
            engine = Diagnostics.timedSuspend(Diagnostics.ENGINE, "load(backend init)") {
                if (!gpuKnownToFail) {
                    try {
                        Diagnostics.i(Diagnostics.ENGINE, "Attempting GPU backend initialization")
                        initializeWithBackend(Backend.GPU())
                    } catch (e: Exception) {
                        gpuKnownToFail = true
                        Diagnostics.w(Diagnostics.ENGINE, "GPU failed (cached for future): ${e.message}, falling back to CPU")
                        initCpu()
                    }
                } else {
                    Diagnostics.i(Diagnostics.ENGINE, "Skipping GPU (known to fail on this device) → CPU directly")
                    initCpu()
                }
            }
            Diagnostics.i(Diagnostics.ENGINE, "Engine initialized successfully")
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
    private suspend fun initCpu(): Engine {
        try {
            Diagnostics.i(Diagnostics.ENGINE, "Attempting CPU backend (4 threads)")
            return initializeWithBackend(Backend.CPU(numOfThreads = 4))
        } catch (cpuError: Exception) {
            Diagnostics.e(Diagnostics.ENGINE, "CPU backend also failed: ${cpuError.message}", cpuError)
            throw IllegalStateException(
                "Failed to initialize model on CPU: ${cpuError.message}",
                cpuError
            )
        }
    }

    private suspend fun initializeWithBackend(backend: Backend): Engine {
        val backendName = if (backend is Backend.GPU) "GPU" else "CPU"
        Diagnostics.d(Diagnostics.ENGINE, "initializeWithBackend: backend=$backendName, cacheDir=${cacheDir ?: "(none)"}")
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir ?: "",
            maxNumTokens = 1024   // Limit KV cache: SMS analysis needs at most ~512 input + ~512 output
        )
        val eng = Engine(config)

        // Run the blocking JNI init on a separate thread so that
        // coroutine timeout can actually cancel if needed.
        val deferred = CompletableDeferred<Unit>()
        val initThread = Thread({
            try {
                Diagnostics.d(Diagnostics.ENGINE, "JNI init thread started for $backendName")
                eng.initialize()
                Diagnostics.d(Diagnostics.ENGINE, "JNI init completed for $backendName")
                deferred.complete(Unit)
            } catch (e: Exception) {
                Diagnostics.e(Diagnostics.ENGINE, "JNI init failed for $backendName: ${e.message}")
                deferred.completeExceptionally(e)
            }
        }, "LiteRtLmEngine-init")

        initThread.start()
        Diagnostics.d(Diagnostics.ENGINE, "Init thread started, awaiting deferred with 120s timeout")

        try {
            // 120-second timeout for engine initialization.
            // Galaxy A14 takes ~30s with GPU, up to ~90s on CPU.
            withTimeout(120_000L) {
                deferred.await()
            }
            Diagnostics.i(Diagnostics.ENGINE, "initializeWithBackend($backendName) succeeded")
        } catch (e: Exception) {
            Diagnostics.e(Diagnostics.ENGINE, "initializeWithBackend($backendName) failed/timed-out: ${e.message}")
            // If timeout or cancellation, try to close the engine
            try { eng.close() } catch (_: Exception) {}
            throw e
        }

        return eng
    }

    override fun createSession(systemPrompt: String): LlmConversationSession {
        Diagnostics.i(Diagnostics.ENGINE, "createSession: systemPrompt length=${systemPrompt.length}")
        val eng = engine ?: throw IllegalStateException("Model not loaded — call load() first")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7, topP = 0.9)
        // System prompt passed via ConversationConfig.systemInstruction
        // (not as Message.user), matching the Gallery pattern.
        val convConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            systemInstruction = Contents.of(systemPrompt)
        )
        val conv = Diagnostics.timed(Diagnostics.ENGINE, "createConversation") {
            eng.createConversation(convConfig)
        }
        Diagnostics.i(Diagnostics.ENGINE, "Session created successfully")
        return LlmConversationSession(conv)
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        Diagnostics.i(Diagnostics.ENGINE, "generate() called: prompt length=${prompt.length}")
        val currentEngine = engine ?: throw IllegalStateException("Model not loaded")
        val samplerConfig = SamplerConfig(topK = 40, temperature = 0.7, topP = 0.9)
        val conversationConfig = ConversationConfig(samplerConfig = samplerConfig)
        val conversation = currentEngine.createConversation(conversationConfig)

        try {
            val userMessage = Message.user(prompt)
            val responseMessage = Diagnostics.timedSuspend(Diagnostics.ENGINE, "generate/sendMessage") {
                conversation.sendMessage(userMessage)
            }
            val text = extractTextFromMessage(responseMessage)
            Diagnostics.i(Diagnostics.ENGINE, "generate() completed: response length=${text.length}")
            text
        } finally {
            conversation.close()
        }
    }

    override fun close() {
        Diagnostics.i(Diagnostics.ENGINE, "close() called, engine=${if (engine != null) "active" else "null"}")
        engine?.close()
        engine = null
        Diagnostics.d(Diagnostics.ENGINE, "Engine disposed")
    }

}
