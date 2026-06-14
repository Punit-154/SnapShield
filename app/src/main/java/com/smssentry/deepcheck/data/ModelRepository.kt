package com.smssentry.deepcheck.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.smssentry.deepcheck.util.Diagnostics
import com.smssentry.deepcheck.DeepCheckConfig
import com.smssentry.deepcheck.model.LiteRtLmEngine
import com.smssentry.deepcheck.model.LlmInferenceEngine
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
        // On startup, check if the model file already exists on disk.
        // If it does, automatically load the engine in the background so
        // the user does not have to re-download after every app restart.
        if (isModelDownloaded()) {
            Diagnostics.i(Diagnostics.MODEL, "init: model found on disk, size=${modelFile.length()} bytes, launching ensureReady")
            applicationScope.launch {
                ensureReady()
            }
        } else {
            Diagnostics.i(Diagnostics.MODEL, "init: model file not found on disk")
        }
    }

    fun isModelDownloaded(): Boolean {
        val exists = modelFile.exists()
        val size = if (exists) modelFile.length() else 0L
        val result = exists && size >= DeepCheckConfig.MIN_FILE_SIZE_BYTES
        Diagnostics.d(Diagnostics.MODEL, "isModelDownloaded: exists=$exists, size=$size, minRequired=${DeepCheckConfig.MIN_FILE_SIZE_BYTES}, result=$result")
        return result
    }

    /**
     * Returns the available disk space in bytes on the internal storage partition.
     */
    private fun availableDiskSpaceBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Diagnostics.w(Diagnostics.MODEL, "availableDiskSpaceBytes: failed to read: ${e.message}")
            Long.MAX_VALUE // Optimistically allow download if we can't check
        }
    }

    /**
     * Clear the current error state so the user can retry.
     */
    fun resetError() {
        _error.value = null
        if (_state.value == State.FAILED) {
            _state.value = State.IDLE
        }
    }

    fun isOnWiFi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun ensureReady(): Boolean {
        if (_state.value == State.READY) {
            Diagnostics.d(Diagnostics.MODEL, "ensureReady: already READY, skipping")
            return true
        }

        if (!isModelDownloaded()) {
            Diagnostics.w(Diagnostics.MODEL, "ensureReady: model not downloaded, reverting to IDLE")
            _state.value = State.IDLE
            return false
        }

        Diagnostics.i(Diagnostics.MODEL, "ensureReady: ${_state.value}→LOADING")
        _state.value = State.LOADING
        return try {
            Diagnostics.timedSuspend(Diagnostics.MODEL, "ensureReady/load") {
                withContext(Dispatchers.IO) {
                    val engine = getEngine()
                    engine.load()
                    // Engine.initialize() succeeded — the model is valid.
                    // No additional validation probe is needed; running a
                    // throwaway generate("Say OK") was causing false FAILED
                    // states when the model produced blank output for that
                    // artificial prompt.
                    _state.value = State.READY
                    Diagnostics.i(Diagnostics.MODEL, "ensureReady: LOADING→READY")
                    Log.i(TAG, "Model loaded successfully")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            Diagnostics.e(Diagnostics.MODEL, "ensureReady: LOADING→FAILED: ${e.message}", e)
            _state.value = State.FAILED
            _error.value = "Failed to load model: ${e.message}"
            false
        }
    }

    fun getEngine(): LlmInferenceEngine {
        cachedEngine?.let {
            Diagnostics.d(Diagnostics.MODEL, "getEngine: cache hit")
            return it
        }
        Diagnostics.i(Diagnostics.MODEL, "getEngine: cache miss, creating LiteRtLmEngine")
        val cacheDir = File(context.cacheDir, "litert_cache").also { it.mkdirs() }
        val engine = LiteRtLmEngine(modelFile.absolutePath, cacheDir.absolutePath)
        cachedEngine = engine
        return engine
    }

    suspend fun downloadModel() {
        if (_state.value == State.DOWNLOADING) {
            Diagnostics.d(Diagnostics.DOWNLOAD, "downloadModel: already DOWNLOADING, skipping")
            return
        }
        _error.value = null

        // ── Disk space pre-check ─────────────────────────────────────
        // The model is ~3.7 GB; require 5 GB free to leave headroom for
        // the OS, temp files and the extracted model cache.
        val requiredBytes = 5_000_000_000L
        val availableBytes = availableDiskSpaceBytes()
        if (availableBytes < requiredBytes) {
            val availMb = availableBytes / (1024 * 1024)
            val reqMb = requiredBytes / (1024 * 1024)
            val msg = "Not enough disk space: ${availMb}MB available, ${reqMb}MB required"
            Diagnostics.e(Diagnostics.DOWNLOAD, "downloadModel: $msg")
            _error.value = msg
            _state.value = State.FAILED
            return
        }
        Diagnostics.d(Diagnostics.DOWNLOAD, "downloadModel: disk space OK (${availableBytes / (1024*1024)}MB available)")

        _state.value = State.DOWNLOADING
        _progress.value = 0f
        Diagnostics.i(Diagnostics.DOWNLOAD, "downloadModel: started, url=${DeepCheckConfig.MODEL_URL}")

        try {
            Diagnostics.timedSuspend(Diagnostics.DOWNLOAD, "downloadModel") {
                withContext(Dispatchers.IO) {
                    val existingBytes = if (modelFile.exists()) modelFile.length() else 0L
                    Diagnostics.i(Diagnostics.DOWNLOAD, "downloadModel: existingBytes=$existingBytes, resuming=${existingBytes > 0}")
                    val request = Request.Builder()
                        .url(DeepCheckConfig.MODEL_URL)
                        .apply {
                            if (existingBytes > 0) header("Range", "bytes=$existingBytes-")
                        }
                        .build()

                    client.newCall(request).execute().use { response ->
                        Diagnostics.d(Diagnostics.DOWNLOAD, "downloadModel: HTTP ${response.code}")
                        if (!response.isSuccessful && response.code != 206) {
                            if (response.code == 416) {
                                Diagnostics.w(Diagnostics.DOWNLOAD, "downloadModel: 416 Range Not Satisfiable, deleting corrupt file")
                                modelFile.delete()
                                throw IOException("Download corrupted, restarting.")
                            }
                            throw IOException("Server error: ${response.code}")
                        }

                        val body = response.body ?: throw IOException("Empty body")
                        val totalSize = if (response.code == 206) existingBytes + body.contentLength() else body.contentLength()
                        Diagnostics.i(Diagnostics.DOWNLOAD, "downloadModel: totalSize=$totalSize bytes, contentLength=${body.contentLength()}")

                        val outputStream = FileOutputStream(modelFile, response.code == 206)
                        outputStream.use { out ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var bytesWritten = if (response.code == 206) existingBytes else 0L
                            var lastMilestone = 0

                            body.byteStream().use { input ->
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    out.write(buffer, 0, bytesRead)
                                    bytesWritten += bytesRead
                                    if (totalSize > 0) {
                                        _progress.value = bytesWritten.toFloat() / totalSize
                                        val pct = (bytesWritten * 100 / totalSize).toInt()
                                        val milestone = pct / 25 * 25
                                        if (milestone > lastMilestone && milestone in listOf(25, 50, 75, 100)) {
                                            Diagnostics.i(Diagnostics.DOWNLOAD, "downloadModel: $milestone% complete ($bytesWritten/$totalSize bytes)")
                                            lastMilestone = milestone
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Diagnostics.i(Diagnostics.DOWNLOAD, "downloadModel: download finished, verifying checksum")
                    _state.value = State.VERIFYING
                    if (!verifyChecksum()) {
                        Diagnostics.e(Diagnostics.DOWNLOAD, "downloadModel: checksum verification FAILED, deleting file")
                        modelFile.delete()
                        throw IOException("Checksum verification failed")
                    }
                    Diagnostics.i(Diagnostics.DOWNLOAD, "downloadModel: checksum verified, loading engine")

                    ensureReady()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Diagnostics.e(Diagnostics.DOWNLOAD, "downloadModel: FAILED: ${e.message}", e)
            _state.value = State.FAILED
            _error.value = e.message ?: "Unknown error"
        }
    }

    private fun verifyChecksum(): Boolean {
        return try {
            Diagnostics.timed(Diagnostics.DOWNLOAD, "verifyChecksum") {
                val digest = MessageDigest.getInstance("SHA-256")
                modelFile.inputStream().buffered().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                val result = actual.equals(DeepCheckConfig.MODEL_SHA256, ignoreCase = true)
                Diagnostics.i(Diagnostics.DOWNLOAD, "verifyChecksum: match=$result, actual=${actual.take(12)}…")
                result
            }
        } catch (e: Exception) {
            Diagnostics.e(Diagnostics.DOWNLOAD, "verifyChecksum: exception: ${e.message}", e)
            false
        }
    }

    fun unload() {
        Diagnostics.i(Diagnostics.MODEL, "unload: disposing engine, hasEngine=${cachedEngine != null}")
        cachedEngine?.close()
        cachedEngine = null
        _state.value = State.IDLE
        Diagnostics.i(Diagnostics.MODEL, "unload: state→IDLE")
    }
}
