package com.smssentry.ui.settings

import android.app.Application
import android.app.role.RoleManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smssentry.deepcheck.data.ModelRepository
import com.smssentry.ui.theme.ThemeMode
import com.smssentry.ui.theme.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDefaultSmsApp: Boolean = false,
    val modelState: ModelRepository.State = ModelRepository.State.IDLE,
    val modelDownloaded: Boolean = false,
    val appVersion: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val modelRepository: ModelRepository,
) : ViewModel() {

    // ThemePreferenceRepository is not provided by Hilt; create from context.
    private val themePreferenceRepository = ThemePreferenceRepository(application)

    private val _isDefaultSmsApp = MutableStateFlow(checkDefaultSmsApp())
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    val state: StateFlow<SettingsUiState> = combine(
        themePreferenceRepository.themeMode,
        modelRepository.state,
        _isDefaultSmsApp,
    ) { themeMode, modelState, isDefault ->
        SettingsUiState(
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
}

