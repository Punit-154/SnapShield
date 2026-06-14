package com.smssentry.ui.settings

import android.app.Application
import android.app.role.RoleManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.deepcheck.data.ModelRepository
import com.smssentry.learning.LearningStats
import com.smssentry.learning.PersonalLearningRepository
import com.smssentry.ui.theme.ThemeMode
import com.smssentry.ui.theme.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDefaultSmsApp: Boolean = false,
    val modelState: ModelRepository.State = ModelRepository.State.IDLE,
    val modelDownloaded: Boolean = false,
    val appVersion: String = "",
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val learningStats: LearningStats? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val modelRepository: ModelRepository,
    private val personalLearningRepo: PersonalLearningRepository,
) : ViewModel() {

    // ThemePreferenceRepository is not provided by Hilt; create from context.
    private val themePreferenceRepository = ThemePreferenceRepository(application)

    private val _isDefaultSmsApp = MutableStateFlow(checkDefaultSmsApp())
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private val _state = MutableStateFlow(SettingsUiState(appVersion = getAppVersion()))

    val state: StateFlow<SettingsUiState> = combine(
        themePreferenceRepository.themeMode,
        modelRepository.state,
        _isDefaultSmsApp,
        _state,
    ) { themeMode, modelState, isDefault, local ->
        local.copy(
            themeMode = themeMode,
            isDefaultSmsApp = isDefault,
            modelState = modelState,
            modelDownloaded = modelRepository.isModelDownloaded(),
            appVersion = getAppVersion(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(appVersion = getAppVersion()),
    )

    init {
        refreshLearningStats()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferenceRepository.setThemeMode(mode)
        }
    }

    fun refreshDefaultSmsStatus() {
        _isDefaultSmsApp.value = checkDefaultSmsApp()
    }

    private fun checkDefaultSmsApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = application.getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_SMS) ?: false
        }
        return false
    }

    private fun getAppVersion(): String {
        return try {
            val info = application.packageManager.getPackageInfo(application.packageName, 0)
            info.versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun importExistingSms() {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importProgress = 0) }
            personalLearningRepo.importExistingSms { processed, total ->
                _state.update { it.copy(importProgress = processed, importTotal = total) }
            }
            refreshLearningStats()
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun clearLearningData() {
        viewModelScope.launch {
            personalLearningRepo.clearAll()
            refreshLearningStats()
        }
    }

    private fun refreshLearningStats() {
        viewModelScope.launch {
            val stats = personalLearningRepo.getStats()
            _state.update { it.copy(learningStats = stats) }
        }
    }
}

