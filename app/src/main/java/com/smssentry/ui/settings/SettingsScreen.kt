package com.smssentry.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.deepcheck.data.ModelRepository
import com.smssentry.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onBlockedNumbersClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }

    // Launcher for default SMS app role request
    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        // Refresh status regardless of result
        viewModel.refreshDefaultSmsStatus()
    }

    // Refresh default SMS status when screen resumes
    LaunchedEffect(Unit) {
        viewModel.refreshDefaultSmsStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Appearance section ──────────────────────────────────────
            SettingsSectionHeader(title = "Appearance")

            SettingsItem(
                icon = when (state.themeMode) {
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                },
                title = "Theme",
                subtitle = state.themeMode.label,
                onClick = { showThemeDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── SMS section ────────────────────────────────────────────
            SettingsSectionHeader(title = "SMS")

            SettingsItem(
                icon = Icons.Filled.Sms,
                title = "Default SMS app",
                subtitle = if (state.isDefaultSmsApp) "SMSentry is your default SMS app" else "Tap to set as default",
                trailingIcon = if (state.isDefaultSmsApp) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                trailingIconTint = if (state.isDefaultSmsApp) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                onClick = {
                    if (!state.isDefaultSmsApp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(RoleManager::class.java)
                        if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                            roleRequestLauncher.launch(intent)
                        }
                    }
                },
            )

            SettingsItem(
                icon = Icons.Filled.Block,
                title = "Blocked numbers",
                subtitle = "Manage your blocked numbers list",
                onClick = onBlockedNumbersClick,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── AI Model section ───────────────────────────────────────
            SettingsSectionHeader(title = "AI Model")

            SettingsItem(
                icon = Icons.Filled.Memory,
                title = "On-device AI model",
                subtitle = when (state.modelState) {
                    ModelRepository.State.IDLE -> if (state.modelDownloaded) "Downloaded (not loaded)" else "Not downloaded"
                    ModelRepository.State.DOWNLOADING -> "Downloading…"
                    ModelRepository.State.VERIFYING -> "Verifying…"
                    ModelRepository.State.LOADING -> "Loading…"
                    ModelRepository.State.READY -> "Ready"
                    ModelRepository.State.FAILED -> "Failed to load"
                },
                trailingIcon = when (state.modelState) {
                    ModelRepository.State.READY -> Icons.Filled.CheckCircle
                    ModelRepository.State.FAILED -> Icons.Filled.Warning
                    else -> null
                },
                trailingIconTint = when (state.modelState) {
                    ModelRepository.State.READY -> MaterialTheme.colorScheme.primary
                    ModelRepository.State.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                onClick = { /* Could navigate to model download screen */ },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── About section ──────────────────────────────────────────
            SettingsSectionHeader(title = "About")

            SettingsItem(
                icon = Icons.Filled.Info,
                title = "SMSentry",
                subtitle = "Version ${state.appVersion}\nAI-powered SMS protection",
                onClick = { /* No-op or show about dialog */ },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Theme picker dialog ────────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Reusable components ────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingIconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = trailingIconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

