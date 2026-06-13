package com.smssentry.deepcheck

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ModelDownloadManager(private val context: Context) {

    enum class State { IDLE, DOWNLOADING, VERIFYING, COMPLETE, FAILED }

    companion object {
        const val MODEL_URL =
            "https://huggingface.co/google/gemma-2-2b-it/resolve/main/gemma-2-2b-it-int4.tflite"
        const val MODEL_FILE_NAME = "gemma-2-2b-it-int4.tflite"
        const val MIN_FILE_SIZE_BYTES = 500_000_000L
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

                val outputStream = if (isRangeResponse && modelFile.exists()) {
                    modelFile.outputStream().apply { channel.position(existingBytes) }
                } else {
                    if (modelFile.exists()) modelFile.delete()
                    modelFile.outputStream()
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
                    modelFile.delete()
                    _error.value = "Downloaded file too small (${modelFile.length()} bytes)"
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
}
