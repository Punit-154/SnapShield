package com.smssentry.deepcheck

import android.content.Context
import com.smssentry.deepcheck.ModelDownloadManager
import com.smssentry.deepcheck.model.ChatMessage
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.model.LlmResponse
import com.smssentry.deepcheck.model.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class ModelManager(private val context: Context) {

    enum class State { NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, FAILED }

    private val _state = MutableStateFlow(State.NOT_DOWNLOADED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private var engine: LlmInferenceEngine? = null

    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, "models/gemma-4-e4b-it-int4.tflite")
        return modelFile.exists() && modelFile.length() > 1_000_000_000L
    }

    fun getDownloadUrl(): String = ModelDownloadManager.MODEL_URL

    suspend fun ensureReady(): Boolean {
        if (_state.value == State.READY) return true

        val modelFile = File(context.filesDir, "models/gemma-4-e4b-it-int4.tflite")
        if (!modelFile.exists()) {
            _state.value = State.NOT_DOWNLOADED
            _state.value = State.FAILED
            return false
        }

        _state.value = State.LOADING
        return try {
            withContext(Dispatchers.IO) {
                engine = LiteRtLmEngine(modelFile.absolutePath)
                _state.value = State.READY
                true
            }
        } catch (e: Exception) {
            _state.value = State.FAILED
            false
        }
    }

    fun getInference(): LlmInferenceEngine? {
        return if (_state.value == State.READY) engine else null
    }

    fun unload() {
        engine = null
        _state.value = State.NOT_DOWNLOADED
    }
}

class LiteRtLmEngine(modelPath: String) : LlmInferenceEngine {
    override suspend fun generateResponseAsync(
        messages: List<ChatMessage>,
        tools: List<Tool>
    ): LlmResponse? {
        return LlmResponse.Error("LiteRT-LM not available in this build.")
    }
}
