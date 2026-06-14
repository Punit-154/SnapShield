package com.smssentry.deepcheck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.deepcheck.data.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    val state: StateFlow<ModelRepository.State> = modelRepository.state
    val progress: StateFlow<Float> = modelRepository.progress
    val error: StateFlow<String?> = modelRepository.error

    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>()
    val navigateBack: SharedFlow<Unit> = _navigateBack

    fun startDownload() {
        if (wifiOnly.value && !modelRepository.isOnWiFi()) {
            return
        }
        viewModelScope.launch {
            modelRepository.downloadModel()
            if (modelRepository.state.value == ModelRepository.State.READY) {
                kotlinx.coroutines.delay(2000)
                _navigateBack.emit(Unit)
            }
        }
    }

    fun cancelDownload() {
        modelRepository.unload()
    }

    fun retryDownload() {
        modelRepository.unload()
        startDownload()
    }

    fun toggleWifiOnly() {
        _wifiOnly.value = !_wifiOnly.value
    }
}
