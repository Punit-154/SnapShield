package com.smssentry.deepcheck

import android.content.Context
import com.smssentry.deepcheck.model.LlmInferenceEngine
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

    private var cachedEngine: LlmInferenceEngine? = null

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
                val engineInstance = getLlmEngine() ?: throw IllegalStateException("Failed to create engine")
                engineInstance.load()
                _state.value = State.READY
                true
            }
        } catch (e: Exception) {
            _state.value = State.FAILED
            false
        }
    }

    fun getLlmEngine(): LlmInferenceEngine? {
        if (cachedEngine != null) return cachedEngine

        val modelFile = File(context.filesDir, "models/${ModelDownloadManager.MODEL_FILE_NAME}")
        return if (modelFile.exists() && modelFile.length() >= ModelDownloadManager.MIN_FILE_SIZE_BYTES) {
            cachedEngine = com.smssentry.deepcheck.model.LiteRtLmEngine(modelFile.absolutePath)
            cachedEngine
        } else {
            null
        }
    }

    fun unload() {
        cachedEngine?.close()
        cachedEngine = null
        _state.value = State.NOT_DOWNLOADED
    }
}



