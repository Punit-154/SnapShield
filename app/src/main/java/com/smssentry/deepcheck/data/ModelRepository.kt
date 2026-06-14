package com.smssentry.deepcheck.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.smssentry.deepcheck.DeepCheckConfig
import com.smssentry.deepcheck.LiteRtLmEngine
import com.smssentry.deepcheck.model.LlmInferenceEngine
import com.smssentry.deepcheck.util.HashUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    @com.smssentry.di.ApplicationScope private val applicationScope: CoroutineScope
) {
    private val TAG = "ModelRepository"

    enum class State { IDLE, DOWNLOADING, VERIFYING, LOADING, READY, FAILED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var cachedEngine: LlmInferenceEngine? = null

    private val modelFile: File by lazy {
        val dir = File(context.filesDir, "models").also { it.mkdirs() }
        File(dir, DeepCheckConfig.MODEL_FILE_NAME)
    }

    init {
        if (isModelDownloaded()) {
            applicationScope.launch {
                ensureReady()
            }
        }
    }

    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && modelFile.length() >= DeepCheckConfig.MIN_FILE_SIZE_BYTES
    }

    fun isOnWiFi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun ensureReady(): Boolean {
        if (_state.value == State.READY) return true
        
        if (!isModelDownloaded()) {
            _state.value = State.IDLE
            return false
        }

        _state.value = State.LOADING
        return try {
            withContext(Dispatchers.IO) {
                val engine = getEngine()
                engine.load()
                _state.value = State.READY
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _state.value = State.FAILED
            _error.value = "Failed to load model: ${e.message}"
            false
        }
    }

    fun getEngine(): LlmInferenceEngine {
        cachedEngine?.let { return it }
        val engine = LiteRtLmEngine(modelFile.absolutePath)
        cachedEngine = engine
        return engine
    }

    suspend fun downloadModel() {
        if (_state.value == State.DOWNLOADING) return
        _error.value = null
        _state.value = State.DOWNLOADING
        _progress.value = 0f

        try {
            withContext(Dispatchers.IO) {
                val existingBytes = if (modelFile.exists()) modelFile.length() else 0L
                val request = Request.Builder()
                    .url(DeepCheckConfig.MODEL_URL)
                    .apply {
                        if (existingBytes > 0) header("Range", "bytes=$existingBytes-")
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        if (response.code == 416) {
                            modelFile.delete()
                            throw IOException("Download corrupted, restarting.")
                        }
                        throw IOException("Server error: ${response.code}")
                    }

                    val body = response.body ?: throw IOException("Empty body")
                    val totalSize = if (response.code == 206) existingBytes + body.contentLength() else body.contentLength()
                    
                    val outputStream = FileOutputStream(modelFile, response.code == 206)
                    outputStream.use { out ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var bytesWritten = if (response.code == 206) existingBytes else 0L
                        
                        body.byteStream().use { input ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                bytesWritten += bytesRead
                                if (totalSize > 0) {
                                    _progress.value = bytesWritten.toFloat() / totalSize
                                }
                            }
                        }
                    }
                }

                _state.value = State.VERIFYING
                if (!verifyChecksum()) {
                    modelFile.delete()
                    throw IOException("Checksum verification failed")
                }

                ensureReady()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _state.value = State.FAILED
            _error.value = e.message ?: "Unknown error"
        }
    }

    private fun verifyChecksum(): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            actual.equals(DeepCheckConfig.MODEL_SHA256, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    fun unload() {
        cachedEngine?.close()
        cachedEngine = null
        _state.value = State.IDLE
    }
}
