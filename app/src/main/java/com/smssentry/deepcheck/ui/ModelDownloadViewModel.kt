package com.smssentry.deepcheck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.deepcheck.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    val state: StateFlow<ModelDownloadManager.State> = downloadManager.state
    val progress: StateFlow<Float> = downloadManager.progress
    val downloadedBytes: StateFlow<Long> = downloadManager.downloadedBytes
    val totalBytes: StateFlow<Long> = downloadManager.totalBytes
    val speedBytesPerSec: StateFlow<Long> = downloadManager.speedBytesPerSec
    val error: StateFlow<String?> = downloadManager.error

    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    fun startDownload() {
        if (wifiOnly.value && !downloadManager.isOnWiFi()) {
            return
        }
        viewModelScope.launch {
            downloadManager.startDownload()
            if (downloadManager.state.value == ModelDownloadManager.State.COMPLETE) {
                kotlinx.coroutines.delay(2000)
                _navigateBack.value = true
            }
        }
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun retryDownload() {
        downloadManager.reset()
        startDownload()
    }

    fun toggleWifiOnly() {
        _wifiOnly.value = !_wifiOnly.value
    }

    fun onNavigatedBack() {
        _navigateBack.value = false
    }
}
