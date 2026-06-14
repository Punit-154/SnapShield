package com.smssentry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.smssentry.sms.NotificationHelper
import com.smssentry.ui.navigation.SMSSentryNavGraph
import com.smssentry.ui.navigation.Screen
import com.smssentry.ui.theme.SMSSentryTheme
import com.smssentry.ui.theme.ThemeMode
import com.smssentry.ui.theme.ThemePreferenceRepository
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            showRestrictedSettingsDialog.value = true
        }
    }

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* Role result handled automatically */ }

    private val showRestrictedSettingsDialog = mutableStateOf(value = false)

    // Pending deep-link destination from notification tap
    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestSmsPermissions()
        requestDefaultSmsRole()

        // Handle deep-link from notification
        handleNotificationIntent(intent)

        setContent {
            val themeRepository = remember { ThemePreferenceRepository(applicationContext) }
            val themeMode by themeRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            SMSSentryTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    SMSSentryNavGraph(
                        navController = navController,
                        themeRepository = themeRepository
                    )

                    // Navigate to deep-link destination when available
                    val deepLink = pendingDeepLink.value
                    LaunchedEffect(deepLink) {
                        if (deepLink != null) {
                            navController.navigate(deepLink) {
                                launchSingleTop = true
                            }
                            pendingDeepLink.value = null
                        }
                    }

                    if (showRestrictedSettingsDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showRestrictedSettingsDialog.value = false },
                            title = { Text(stringResource(R.string.perm_sms_title)) },
                            text = {
                                Text(stringResource(R.string.perm_sms_desc))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showRestrictedSettingsDialog.value = false
                                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                    startActivity(settingsIntent)
                                }) {
                                    Text(stringResource(R.string.open_settings))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRestrictedSettingsDialog.value = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Extracts thread/sender info from notification intents and sets up navigation.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val threadId = intent.getLongExtra(NotificationHelper.EXTRA_THREAD_ID, -1L)
        val sender = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_SENDER)

        if (threadId > 0 && sender != null) {
            Log.d(TAG, "Deep-link: threadId=$threadId, sender=$sender")
            pendingDeepLink.value = Screen.Chat.createRoute(threadId, sender)
            // Clear the extras so we don't re-navigate on config change
            intent.removeExtra(NotificationHelper.EXTRA_THREAD_ID)
            intent.removeExtra(NotificationHelper.EXTRA_NOTIFICATION_SENDER)
        }
    }

    private fun requestSmsPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }

        if (permissions.isNotEmpty()) {
            requestSmsPermission.launch(permissions.toTypedArray())
        }
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                val roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                requestRoleLauncher.launch(roleIntent)
            }
        }
    }
}
