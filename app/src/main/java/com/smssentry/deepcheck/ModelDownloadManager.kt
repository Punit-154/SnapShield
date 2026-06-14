package com.smssentry.deepcheck

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ModelDownloadManager(private val context: Context) {

    enum class State { IDLE, DOWNLOADING, VERIFYING, COMPLETE, FAILED }

    companion object {
        private const val TAG = "ModelDownloadManager"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
        const val MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"
        const val MIN_FILE_SIZE_BYTES = 3_659_000_000L
        const val MODEL_SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private val _speedBytesPerSec = MutableStateFlow(0L)
    val speedBytesPerSec: StateFlow<Long> = _speedBytesPerSec.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    private val modelFile: File by lazy {
        File(modelsDir, MODEL_FILE_NAME)
    }

    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && modelFile.length() >= MIN_FILE_SIZE_BYTES
    }

    fun isOnWiFi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun startDownload() {
        if (_state.value == State.DOWNLOADING) return
        _error.value = null
        _state.value = State.DOWNLOADING
        _progress.value = 0f
        _downloadedBytes.value = 0L
        _totalBytes.value = 0L
        _speedBytesPerSec.value = 0L

        try {
            withContext(Dispatchers.IO) {
                val existingBytes = if (modelFile.exists()) modelFile.length() else 0L

                val requestBuilder = Request.Builder().url(MODEL_URL)
                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                val request = requestBuilder.build()
                val call = client.newCall(request)
                activeCall = call

                val response = call.execute()
                if (!response.isSuccessful && response.code != 206) {
                    if (response.code == 416) {
                        response.close()
                        modelFile.delete()
                        _error.value = "Partial download corrupted. Tap retry to start fresh."
                        _state.value = State.FAILED
                        return@withContext
                    }
                    _error.value = "Server returned ${response.code}"
                    _state.value = State.FAILED
                    response.close()
                    return@withContext
                }

                val body = response.body ?: run {
                    _error.value = "Empty response body"
                    _state.value = State.FAILED
                    return@withContext
                }

                val contentLength = body.contentLength()
                val isRangeResponse = response.code == 206

                val totalSize = if (isRangeResponse) {
                    existingBytes + contentLength
                } else {
                    contentLength
                }
                _totalBytes.value = totalSize

                // FIX: Use FileOutputStream(file, true) to append during resume
                val outputStream = if (isRangeResponse && modelFile.exists()) {
                    FileOutputStream(modelFile, true)
                } else {
                    if (modelFile.exists()) modelFile.delete()
                    FileOutputStream(modelFile)
                }

                var bytesWritten = if (isRangeResponse) existingBytes else 0L
                _downloadedBytes.value = bytesWritten

                val buffer = ByteArray(8192)
                var lastTimeNs = System.nanoTime()
                var lastBytes = bytesWritten
                val inputStream = body.byteStream()

                try {
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw IOException("Download cancelled")
                        }
                        val read = inputStream.read(buffer)
                        if (read == -1) break

                        outputStream.write(buffer, 0, read)
                        bytesWritten += read
                        _downloadedBytes.value = bytesWritten

                        if (totalSize > 0) {
                            _progress.value = (bytesWritten.toFloat() / totalSize).coerceIn(0f, 1f)
                        }

                        val nowNs = System.nanoTime()
                        val elapsedNs = nowNs - lastTimeNs
                        if (elapsedNs >= 500_000_000L) {
                            val bytesDelta = bytesWritten - lastBytes
                            val elapsedSec = elapsedNs / 1_000_000_000.0
                            _speedBytesPerSec.value = (bytesDelta / elapsedSec).toLong()
                            lastTimeNs = nowNs
                            lastBytes = bytesWritten
                        }
                    }
                } finally {
                    inputStream.close()
                    outputStream.close()
                    body.close()
                }

                activeCall = null

                _state.value = State.VERIFYING
                _progress.value = 1f

                if (modelFile.length() < MIN_FILE_SIZE_BYTES) {
                    val actualSize = modelFile.length()
                    modelFile.delete()
                    _error.value = "Downloaded file too small ($actualSize bytes, need $MIN_FILE_SIZE_BYTES)"
                    _state.value = State.FAILED
                    return@withContext
                }

                if (!verifyChecksum(modelFile)) {
                    modelFile.delete()
                    _error.value = "Model checksum verification failed."
                    _state.value = State.FAILED
                    return@withContext
                }

                _state.value = State.COMPLETE
            }
        } catch (e: IOException) {
            if (_state.value == State.DOWNLOADING) {
                _error.value = e.message ?: "Download failed"
                _state.value = State.FAILED
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Unexpected error"
            _state.value = State.FAILED
        }
    }

    fun cancelDownload() {
        activeCall?.cancel()
        activeCall = null
        _error.value = null
        _state.value = State.IDLE
        _progress.value = 0f
        _downloadedBytes.value = 0L
        _speedBytesPerSec.value = 0L
    }

    fun reset() {
        cancelDownload()
        _error.value = null
        _state.value = State.IDLE
    }

    private fun verifyChecksum(file: File): Boolean {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            // FIX: Use 'toInt() and 0xFF' to handle signed bytes correctly during hex conversion
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            val matches = actual.equals(MODEL_SHA256, ignoreCase = true)
            if (!matches) {
                Log.e(TAG, "Checksum mismatch! Expected: $MODEL_SHA256, Actual: $actual")
            }
            matches
        } catch (e: Exception) {
            Log.e(TAG, "Checksum calculation failed", e)
            false
        }
    }
}
