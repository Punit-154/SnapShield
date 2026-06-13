package com.smssentry.deepcheck

import android.content.Context
import com.smssentry.deepcheck.model.LlmInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Content

class ModelManager(private val context: Context) {

    enum class State { NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, FAILED }

    private val _state = MutableStateFlow(State.NOT_DOWNLOADED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private var engine: LlmInferenceEngine? = null

    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, "models/${ModelDownloadManager.MODEL_FILE_NAME}")
        return modelFile.exists() && modelFile.length() >= ModelDownloadManager.MIN_FILE_SIZE_BYTES
    }

    fun getDownloadUrl(): String = ModelDownloadManager.MODEL_URL

    suspend fun ensureReady(): Boolean {
        if (_state.value == State.READY) return true

        val modelFile = File(context.filesDir, "models/${ModelDownloadManager.MODEL_FILE_NAME}")
        if (!modelFile.exists() || modelFile.length() < ModelDownloadManager.MIN_FILE_SIZE_BYTES) {
            _state.value = State.NOT_DOWNLOADED
            return false
        }

        _state.value = State.LOADING
        return try {
            withContext(Dispatchers.IO) {
                engine = LiteRtLmEngine(modelFile.absolutePath)
                engine?.load()
                _state.value = State.READY
                true
            }
        } catch (e: Exception) {
            _state.value = State.FAILED
            false
        }
    }

    fun getLlmEngine(): LlmInferenceEngine? {
        val modelFile = File(context.filesDir, "models/${ModelDownloadManager.MODEL_FILE_NAME}")
        return if (modelFile.exists() && modelFile.length() >= ModelDownloadManager.MIN_FILE_SIZE_BYTES) {
            LiteRtLmEngine(modelFile.absolutePath)
        } else {
            null
        }
    }

    fun unload() {
        engine?.close()
        engine = null
        _state.value = State.NOT_DOWNLOADED
    }
}


class LiteRtLmEngine(private val modelPath: String) : LlmInferenceEngine {

    private var engine: Engine? = null

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (engine == null) {
            val modelFile = java.io.File(modelPath)
            if (!modelFile.exists() || modelFile.length() < ModelDownloadManager.MIN_FILE_SIZE_BYTES) {
                throw IllegalStateException("Model file incomplete: ${modelFile.length()} bytes")
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

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val currentEngine = engine ?: throw IllegalStateException("Model not loaded")
        val samplerConfig = SamplerConfig(1, 1.0, 0.6, 0)
        val conversationConfig = ConversationConfig(samplerConfig = samplerConfig)
        val conversation = currentEngine.createConversation(conversationConfig)

        try {
            val userMessage = Message.Companion.of(prompt)
            val responseMessage = conversation.sendMessage(userMessage)
            responseMessage.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        } finally {
            conversation.close()
        }
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
