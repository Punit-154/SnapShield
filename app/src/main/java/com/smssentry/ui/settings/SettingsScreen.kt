package com.smssentry.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.compose.ui.res.stringResource
import com.smssentry.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.School
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
    onModelDownloadClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

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
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
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
            SettingsSectionHeader(title = stringResource(R.string.section_appearance))

            SettingsItem(
                icon = when (state.themeMode) {
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                },
                title = stringResource(R.string.theme),
                subtitle = state.themeMode.label,
                onClick = { showThemeDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── SMS section ────────────────────────────────────────────
            SettingsSectionHeader(title = stringResource(R.string.section_sms))

            SettingsItem(
                icon = Icons.Filled.Sms,
                title = stringResource(R.string.default_sms_app),
                subtitle = if (state.isDefaultSmsApp) stringResource(R.string.default_sms_app_active) else stringResource(R.string.default_sms_app_inactive),
                trailingIcon = if (state.isDefaultSmsApp) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                trailingIconTint = if (state.isDefaultSmsApp) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                onClick = {
                    if (!state.isDefaultSmsApp) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                                roleRequestLauncher.launch(intent)
                            }
                        } else {
                            // Pre-Q fallback
                            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                            context.startActivity(intent)
                        }
                    }
                },
            )

            SettingsItem(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.blocked_numbers),
                subtitle = stringResource(R.string.blocked_numbers_subtitle),
                onClick = onBlockedNumbersClick,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Personal Learning section ───────────────────────────────────
            SettingsSectionHeader(title = stringResource(R.string.learning_section_title))

            SettingsItem(
                icon = Icons.Filled.School,
                title = if (state.isImporting) stringResource(R.string.learning_importing) else stringResource(R.string.learning_import_title),
                subtitle = if (state.isImporting) {
                    "${state.importProgress} / ${state.importTotal} messages"
                } else {
                    stringResource(R.string.learning_import_subtitle)
                },
                onClick = { if (!state.isImporting) viewModel.importExistingSms() },
            )

            if (state.isImporting) {
                LinearProgressIndicator(
                    progress = {
                        if (state.importTotal > 0) state.importProgress.toFloat() / state.importTotal else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            state.learningStats?.let { stats ->
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.learning_stats_title),
                    subtitle = "${stats.totalLabeled} messages learned \u2022 ${stats.trustedSenders} trusted senders",
                    onClick = {},
                )
            }

            SettingsItem(
                icon = Icons.Filled.DeleteForever,
                title = stringResource(R.string.learning_clear_title),
                subtitle = stringResource(R.string.learning_clear_subtitle),
                onClick = { showClearDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── AI Model section ───────────────────────────────────────
            SettingsSectionHeader(title = stringResource(R.string.section_ai_model))

            SettingsItem(
                icon = Icons.Filled.Memory,
                title = stringResource(R.string.on_device_ai_model),
                subtitle = when (state.modelState) {
                    ModelRepository.State.IDLE -> if (state.modelDownloaded) stringResource(R.string.model_downloaded_idle) else stringResource(R.string.model_not_downloaded_idle)
                    ModelRepository.State.DOWNLOADING -> stringResource(R.string.model_state_downloading)
                    ModelRepository.State.VERIFYING -> stringResource(R.string.model_state_verifying)
                    ModelRepository.State.LOADING -> stringResource(R.string.model_state_loading)
                    ModelRepository.State.READY -> stringResource(R.string.model_state_ready)
                    ModelRepository.State.FAILED -> stringResource(R.string.model_state_failed)
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
                onClick = onModelDownloadClick,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── About section ──────────────────────────────────────────
            SettingsSectionHeader(title = stringResource(R.string.section_about))

            SettingsItem(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.app_version, state.appVersion, stringResource(R.string.ai_powered_description)),
                onClick = { /* No-op or show about dialog */ },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Theme picker dialog ────────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme)) },
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
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ── Clear learning data dialog ─────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.learning_clear_confirm_title)) },
            text = {
                Text(stringResource(R.string.learning_clear_confirm_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLearningData()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
                contentDescription = title,
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
                    contentDescription = title,
                    tint = trailingIconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

